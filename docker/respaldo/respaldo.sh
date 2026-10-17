#!/bin/sh
# El respaldo de MalphasOS (2026-10-17): la base de la aplicacion y la de Keycloak, con pg_dump.
#
#   respaldo.sh vigilar     -> lo que corre el contenedor: un respaldo al dia, a la hora RESPALDO_HORA
#   respaldo.sh ahora       -> uno en este momento, por ejemplo antes de actualizar
#   respaldo.sh restaurar <carpeta>  -> vuelve las dos bases a ese respaldo. Lo llama
#                                       restaurar.sh desde el servidor, que antes para la aplicacion
#
# Las carpetas se llaman por fecha y hora hasta el segundo: 2026-10-17_020000.
#
# Cada respaldo es una carpeta con su fecha y hora y dos archivos en el formato propio de pg_dump
# (-Fc): comprimido, y restaurable con pg_restore. Las carpetas de mas de RESPALDO_DIAS dias se borran.
#
# Los respaldos quedan en el servidor, en RESPALDO_DIR. Copiarlos FUERA de el es responsabilidad de
# quien despliega: un respaldo en el mismo disco no sobrevive a perder el disco.
set -eu

DESTINO=/respaldos
: "${RESPALDO_HORA:=2}"
: "${RESPALDO_DIAS:=14}"
export PGHOST=postgres PGUSER="$POSTGRES_USER" PGPASSWORD="$POSTGRES_PASSWORD"

# Uno a la vez: el del dia y uno pedido a mano pueden coincidir, y el 2026-10-17 coincidieron en el
# mismo minuto -con el nombre hasta los minutos, el segundo acabo DENTRO del primero-. mkdir es atomico.
CERROJO="$DESTINO/.en-curso"

respaldar() {
  until mkdir "$CERROJO" 2>/dev/null; do sleep 5; done
  trap 'rmdir "$CERROJO" 2>/dev/null || true' EXIT
  # Lo que quedo a medias de un respaldo que se corto no es un respaldo.
  rm -rf "$DESTINO"/*.parcial
  carpeta="$DESTINO/$(date +%Y-%m-%d_%H%M%S)"
  mkdir "$carpeta.parcial"
  pg_dump -Fc -d "$POSTGRES_DB" -f "$carpeta.parcial/malphasos.dump"
  pg_dump -Fc -d "$KC_DB_NAME" -f "$carpeta.parcial/keycloak.dump"
  # Se renombra al terminar: una carpeta sin «.parcial» es un respaldo completo, nunca uno a medias.
  mv "$carpeta.parcial" "$carpeta"
  echo "respaldo: $carpeta ($(du -sh "$carpeta" | cut -f1))"
  find "$DESTINO" -mindepth 1 -maxdepth 1 -type d -name '20*' -mtime +"$RESPALDO_DIAS" -print -exec rm -rf {} + \
    | sed 's/^/respaldo: borrado por antiguo /'
  rmdir "$CERROJO"
  trap - EXIT
}

# Si hoy ya hay un respaldo completo: una carpeta con fecha de hoy que no es «.parcial».
hay_de_hoy() {
  find "$DESTINO" -mindepth 1 -maxdepth 1 -type d -name "$(date +%Y-%m-%d)_*" ! -name '*.parcial' | grep -q .
}

restaurar() {
  carpeta="$DESTINO/$1"
  [ -f "$carpeta/malphasos.dump" ] && [ -f "$carpeta/keycloak.dump" ] \
    || { echo "restaurar: $carpeta no es un respaldo completo" >&2; exit 1; }
  for base in "$POSTGRES_DB:malphasos" "$KC_DB_NAME:keycloak"; do
    nombre="${base%%:*}"; archivo="${base##*:}"
    # Se borra y se crea de nuevo: restaurar sobre una base con datos mezclaria los dos estados.
    psql -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS \"$nombre\" WITH (FORCE)" \
      -c "CREATE DATABASE \"$nombre\""
    pg_restore --no-owner -d "$nombre" "$carpeta/$archivo.dump"
    echo "restaurar: $nombre desde $carpeta/$archivo.dump"
  done
}

case "${1:-}" in
  ahora) respaldar ;;
  restaurar) restaurar "${2:?Falta la carpeta del respaldo}" ;;
  vigilar)
    echo "respaldo: uno al dia a las ${RESPALDO_HORA}h, se guardan ${RESPALDO_DIAS} dias en $DESTINO"
    # Cada diez minutos se mira si ya toca y si hoy ya hay uno: asi un reinicio no salta ni repite el
    # respaldo del dia, que es lo que haria un temporizador que cuenta desde el arranque.
    while true; do
      if [ "$(date +%H | sed 's/^0//')" -ge "$RESPALDO_HORA" ] && ! hay_de_hoy; then
        # En un subproceso: si falla, el vigilante sigue y lo reintenta, y el cerrojo se suelta.
        ( respaldar ) || { rmdir "$CERROJO" 2>/dev/null || true; echo "respaldo: FALLO; se reintenta en diez minutos" >&2; }
      fi
      sleep 600
    done
    ;;
  *) echo "Uso: $0 vigilar | ahora | restaurar <carpeta>" >&2; exit 2 ;;
esac
