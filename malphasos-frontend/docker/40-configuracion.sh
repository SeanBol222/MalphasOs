#!/bin/sh
# Escribe /config.json desde las variables de entorno del contenedor, antes de que nginx arranque
# (2026-10-17). La aplicacion lo lee al cargar: asi una misma imagen sirve en cualquier dominio, sin
# recompilar. Sin variables quedan los valores de desarrollo, los del docker-compose.yaml de la raiz.
set -eu

: "${MALPHASOS_API_URL:=http://localhost:8081}"
: "${MALPHASOS_KEYCLOAK_URL:=http://localhost:8080}"
: "${MALPHASOS_KEYCLOAK_REALM:=malphasos-realm}"
: "${MALPHASOS_KEYCLOAK_CLIENT_ID:=malphasos-frontend}"
export MALPHASOS_API_URL MALPHASOS_KEYCLOAK_URL MALPHASOS_KEYCLOAK_REALM MALPHASOS_KEYCLOAK_CLIENT_ID

# Una comilla o una barra invertida romperian el JSON: una URL no las lleva, y si llegan es un error de
# configuracion que se dice aqui y no como una pagina en blanco.
for valor in "$MALPHASOS_API_URL" "$MALPHASOS_KEYCLOAK_URL" "$MALPHASOS_KEYCLOAK_REALM" "$MALPHASOS_KEYCLOAK_CLIENT_ID"; do
  case "$valor" in
    *\"* | *\\*) echo "40-configuracion: valor no valido para config.json: $valor" >&2; exit 1 ;;
  esac
done

envsubst < /etc/malphasos/config.template.json > /usr/share/nginx/html/config.json
echo "40-configuracion: API en $MALPHASOS_API_URL, Keycloak en $MALPHASOS_KEYCLOAK_URL"
