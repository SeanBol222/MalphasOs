---
name: keycloak-configuracion
description: Realm sigma-bb-realm con 3 clients (público SPA + 2 confidenciales), theme de login personalizado
tags: [infraestructura, keycloak, "reusable:alta"]
source: keycloak/
updated: 2026-08-27
---

# Configuración de Keycloak

- **Realm**: `sigma-bb-realm`, importado desde `keycloak/imports/realm-export.json` (hay una copia idéntica en `keycloak/configuration/realm-export.json`, probablemente vestigial — verificar cuál es la fuente real antes de clonar).
- **Clients de negocio** (además de los internos de Keycloak):
  - `sigma-frontend` — público (`publicClient=true`), `directAccessGrantsEnabled=true`, `redirectUris=['http://localhost:5173/*']`. El SPA React (ver [[integracion-keycloak-frontend]]).
  - `sigma-api` — confidencial, `serviceAccountsEnabled=true`, `directAccessGrantsEnabled=false`. Client credentials para el backend (ver [[seguridad-keycloak-backend]]).
  - `sigma-backend-admin` — mismo patrón que `sigma-api`, probablemente para tareas administrativas/machine-to-machine separadas.
- **Roles de realm**: solo defaults (`offline_access`, `uma_authorization`, `default-roles-employee-realm` — este último sugiere que el realm fue nombrado internamente como "employee"; los roles de negocio reales están definidos a nivel de client, no capturados como roles de realm).
- **Theme custom**: `sigma-theme` (extiende `keycloak.v2`), personaliza solo la pantalla de login (CSS propio + logo/favicon/imagen de fondo). Cargado por volumen a `/opt/keycloak/themes`.
- **Provider custom**: un JAR de compatibilidad de temas entre versiones de Keycloak, montado en `/opt/keycloak/providers`.

## Reutilizable en MalphasOS

`reusable:alta` — el patrón de 3 clients (público SPA + confidencial API + confidencial admin) es directamente trasladable. El `realm-export.json` se puede clonar y renombrar (`malphasos-realm`), igual que el theme de login (cambiar solo assets de marca).

## Notas relacionadas

[[seguridad-keycloak-backend]] · [[integracion-keycloak-frontend]] · [[docker-compose]] · [[dominio-persona-identidad]]
