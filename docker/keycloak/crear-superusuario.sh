#!/bin/sh
# Crea el primer SuperUsuario de una instalacion de produccion (2026-10-17). Ver DESPLIEGUE.md.
#
#   ./docker/keycloak/crear-superusuario.sh <usuario> <correo> <nombre> <apellido>
#
# Se corre en el servidor, desde la raiz del repositorio, con el compose de produccion levantado. El
# SuperUsuario se crea a mano por diseño -ningun grupo trae super.admin.full, ver el modelo de
# permisos-, y la consola de Keycloak no se publica, asi que se crea por la linea de comandos de
# Keycloak dentro de su contenedor: las credenciales del administrador no salen de el.
#
# Queda en el grupo «admins» con el rol super.admin.full, y con una contrasena temporal que se imprime
# una vez y que Keycloak obliga a cambiar en el primer inicio de sesion.
set -eu

if [ "$#" -ne 4 ]; then
  echo "Uso: $0 <usuario> <correo> <nombre> <apellido>" >&2
  exit 2
fi

ENV_FILE="${ENV_FILE:-.env.prod}"
COMPOSE="docker compose -f docker-compose.prod.yaml --env-file $ENV_FILE"
TEMPORAL="$(openssl rand -base64 18 | tr -d '/+=' | cut -c1-16)"

$COMPOSE exec -T \
  -e NUEVO_USUARIO="$1" -e NUEVO_CORREO="$2" -e NUEVO_NOMBRE="$3" -e NUEVO_APELLIDO="$4" \
  -e NUEVA_CLAVE="$TEMPORAL" \
  keycloak sh -eu -c '
    kc=/opt/keycloak/bin/kcadm.sh
    $kc config credentials --server http://localhost:8080/auth --realm master \
      --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null 2>&1
    $kc create users -r malphasos-realm -s username="$NUEVO_USUARIO" -s email="$NUEVO_CORREO" \
      -s firstName="$NUEVO_NOMBRE" -s lastName="$NUEVO_APELLIDO" -s enabled=true -s emailVerified=true \
      -s "groups=[\"/admins\"]" >/dev/null
    $kc set-password -r malphasos-realm --username "$NUEVO_USUARIO" --new-password "$NUEVA_CLAVE" --temporary
    $kc add-roles -r malphasos-realm --uusername "$NUEVO_USUARIO" --cclientid malphasos-api \
      --rolename super.admin.full
  '

echo "SuperUsuario $1 creado. Contrasena temporal, que se cambia al primer inicio de sesion:"
echo "  $TEMPORAL"
