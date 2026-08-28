---
name: keycloak-configuracion
description: Realm sigma-bb-realm con 3 clients (público SPA + 2 confidenciales), theme de login personalizado
tags: [infraestructura, keycloak, "reusable:alta"]
source: keycloak/
updated: 2026-08-27
---

# Configuración de Keycloak

- **Realm**: `sigma-bb-realm`, definido en `keycloak/imports/realm-export.json`. Existe un segundo archivo en `keycloak/configuration/realm-export.json` que **no es idéntico** (corregido el 2026-08-27: difieren en tamaño, 91628 vs 92441 bytes, y el de `imports/` es el más reciente). Antes de clonar el realm hay que verificar cuál refleja la configuración real.
- ⚠️ **El import automático nunca funcionó.** El `docker-compose.yaml` original usa `KEYCLOAK_IMPORT`, una variable de la era WildFly que **Keycloak 26 (Quarkus) ignora por completo**, y encima apunta a `real-export.json` (nombre inexistente) con un `- Dkeycloak.profile...` concatenado como basura dentro del valor. La forma correcta es `--import-realm` en el `command` con los archivos en `/opt/keycloak/data/import/`. Ver [[docker-compose]].
- **Clients de negocio** (además de los internos de Keycloak):
  - `sigma-frontend` — público (`publicClient=true`), `directAccessGrantsEnabled=true`, `redirectUris=['http://localhost:5173/*']`. El SPA React (ver [[integracion-keycloak-frontend]]).
  - `sigma-api` — confidencial, `serviceAccountsEnabled=true`, `directAccessGrantsEnabled=false`. Client credentials para el backend (ver [[seguridad-keycloak-backend]]).
  - `sigma-backend-admin` — mismo patrón que `sigma-api`, probablemente para tareas administrativas/machine-to-machine separadas.
- **Roles de realm**: solo defaults (`offline_access`, `uma_authorization`, `default-roles-employee-realm` — este último sugiere que el realm fue nombrado internamente como "employee"; los roles de negocio reales están definidos a nivel de client, no capturados como roles de realm).
- **Theme custom**: `sigma-theme` (extiende `keycloak.v2`), personaliza solo la pantalla de login (CSS propio + logo/favicon/imagen de fondo). Cargado por volumen a `/opt/keycloak/themes`.
- **Provider custom**: un JAR de compatibilidad de temas entre versiones de Keycloak, montado en `/opt/keycloak/providers`.

## ⚠️ Bug crítico: `admin.full` definido pero sin asignar

El client `sigma-api` define **15 roles** (`client.read`, `equipment.write`, `work-order.assign`, `admin.full`, `super.admin.full`…) y el realm los reparte entre los grupos `admins`, `clients` y `engineers`.

Pero el grupo `admins` recibe 12 de esos roles y **`admin.full` no está entre ellos**. Y todos los controladores del backend usan exactamente `@PreAuthorize("hasAuthority('admin.full')")`.

Consecuencia: **con este realm, cualquier usuario recibe 403 en todos los endpoints**. La API es inutilizable tal como está configurada. `client.delete` y `super.admin.full` también quedan sin asignar a nadie.

## ⚠️ `frontendUrl` fijado a una URL interna de Docker

El realm trae `attributes.frontendUrl = "http://keycloak:8080"`. Ese valor **anula `KC_HOSTNAME`** y hace que todas las URL públicas del realm apunten al nombre del servicio de Docker, que ningún navegador puede resolver. Con él, autenticarse desde fuera de Docker es imposible.

## El modelo de permisos granular no se usa

Los 15 roles describen un control de acceso fino por recurso y operación, pero el código solo comprueba `admin.full`. El diseño existe y nunca se aprovechó: cualquiera con ese permiso puede hacer todo. Es una decisión pendiente si se quiere aplicar de verdad.

## Estado en MalphasOS (migrado el 2026-08-28)

Realm adaptado transformando el export original, no reescribiéndolo, para conservar los 21 flujos de autenticación y 14 client scopes. Correcciones aplicadas: `admin.full` y `client.delete` asignados al grupo `admins`; `frontendUrl` vaciado; fuerza bruta activada con bloqueo a los 5 intentos en vez de 30; política de contraseñas; y el client público del frontend deja de ofrecer el flujo de contraseña directa, innecesario con PKCE. Ver [[issuer-uri-vs-jwk-set-uri]] para el problema de emisor que apareció al contenerizarlo.

## Reutilizable en MalphasOS

`reusable:alta` — el patrón de 3 clients (público SPA + confidencial API + confidencial admin) es directamente trasladable. El `realm-export.json` se puede clonar y renombrar (`malphasos-realm`), igual que el theme de login (cambiar solo assets de marca).

## Notas relacionadas

[[seguridad-keycloak-backend]] · [[integracion-keycloak-frontend]] · [[docker-compose]] · [[dominio-persona-identidad]]
