#!/bin/sh
# Vuelve MalphasOS a un respaldo (2026-10-17). Ver DESPLIEGUE.md.
#
#   ./docker/respaldo/restaurar.sh 2026-10-17_020000
#
# Se corre en el servidor, desde la raiz del repositorio. Para la aplicacion y Keycloak -nadie debe
# escribir mientras se restaura-, devuelve las dos bases al respaldo y los vuelve a arrancar. Lo que se
# hizo despues de ese respaldo se pierde: antes de restaurar, conviene sacar uno con
#   docker compose -f docker-compose.prod.yaml --env-file .env.prod exec respaldo respaldo.sh ahora
set -eu

[ "$#" -eq 1 ] || { echo "Uso: $0 <carpeta del respaldo, p. ej. 2026-10-17_020000>" >&2; exit 2; }

ENV_FILE="${ENV_FILE:-.env.prod}"
COMPOSE="docker compose -f docker-compose.prod.yaml --env-file $ENV_FILE"

$COMPOSE stop app keycloak
$COMPOSE exec -T respaldo respaldo.sh restaurar "$1"
$COMPOSE start keycloak app
echo "Restaurado $1. La aplicacion vuelve a arrancar; tarda un minuto en estar lista."
