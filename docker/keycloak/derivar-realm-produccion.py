#!/usr/bin/env python3
"""
Deriva el realm de produccion del de desarrollo (2026-10-17).

    python3 docker/keycloak/derivar-realm-produccion.py

El de desarrollo, import/malphasos-realm-realm.json, es la fuente: roles, grupos, temas, clientes y la
configuracion del realm se mantienen ahi. Este guion escribe produccion/malphasos-realm-realm.json
quitandole lo que no puede llegar a un servidor:

  - el usuario dev.admin y su contrasena: en produccion el primer SuperUsuario se crea a mano
    -ver DESPLIEGUE.md-, como dice el modelo de permisos;
  - los secretos de los clientes: el del cliente de administracion lo pone el entorno con
    ${MALPHASOS_BACKEND_ADMIN_SECRET}, que Keycloak sustituye al importar, y el de malphasos-api
    lo genera Keycloak, porque nadie lo usa;
  - las URL de localhost del frontend, que pasan a ser las del dominio, ${MALPHASOS_DOMAIN}.

Si se cambia el realm de desarrollo, se vuelve a correr este guion: no se edita el de produccion a mano.
"""
import json
import pathlib
import re
import sys

AQUI = pathlib.Path(__file__).parent
ORIGEN = AQUI / "import" / "malphasos-realm-realm.json"
DESTINO = AQUI / "produccion" / "malphasos-realm-realm.json"
DOMINIO = "https://${MALPHASOS_DOMAIN}"

realm = json.loads(ORIGEN.read_text(encoding="utf-8"))

# Solo quedan las cuentas de servicio: sin ellas los clientes confidenciales pierden sus roles.
realm["users"] = [u for u in realm.get("users", []) if u.get("serviceAccountClientId")]

for cliente in realm["clients"]:
    if cliente["clientId"] == "malphasos-backend-admin":
        cliente["secret"] = "${MALPHASOS_BACKEND_ADMIN_SECRET}"
    elif cliente["clientId"] == "malphasos-api":
        cliente.pop("secret", None)
    elif cliente["clientId"] == "malphasos-frontend":
        # Swagger no se publica en produccion: su URL de retorno sobra.
        cliente["redirectUris"] = [f"{DOMINIO}/*"]
        cliente["rootUrl"] = DOMINIO
        cliente["baseUrl"] = DOMINIO
        cliente["webOrigins"] = ["+"]
        # Adonde vuelve el navegador al cerrar sesion.
        cliente.setdefault("attributes", {})["post.logout.redirect.uris"] = f"{DOMINIO}/*"

# Un identificador que no es un UUID no cabe en la columna de Keycloak, varchar(36), y tumba el import.
# Paso el 2026-09-13 con un rol escrito a mano, y no se vio hasta la primera instalacion desde cero, el
# 2026-10-17: en desarrollo el realm ya existia y el import se saltaba.
UUID = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")


def identificadores_invalidos(nodo, ruta=""):
    if isinstance(nodo, dict):
        for clave, valor in nodo.items():
            if clave == "id" and isinstance(valor, str) and not UUID.match(valor):
                yield f"{ruta}/{clave}: {valor}"
            yield from identificadores_invalidos(valor, f"{ruta}/{clave}")
    elif isinstance(nodo, list):
        for indice, valor in enumerate(nodo):
            yield from identificadores_invalidos(valor, f"{ruta}[{indice}]")


invalidos = list(identificadores_invalidos(realm))
if invalidos:
    sys.exit("Identificadores que no son UUID en el realm de desarrollo:\n  " + "\n  ".join(invalidos))

texto = json.dumps(realm, ensure_ascii=False, indent=2) + "\n"
if "localhost" in texto:
    sys.exit("Queda una URL de localhost en el realm de produccion: revise el guion")
if any("credentials" in u for u in realm["users"]):
    sys.exit("Queda una contrasena en el realm de produccion: revise el guion")

DESTINO.write_text(texto, encoding="utf-8")
print(f"Escrito {DESTINO.relative_to(AQUI.parent.parent)}: {len(realm['users'])} cuentas de servicio, sin usuarios")
