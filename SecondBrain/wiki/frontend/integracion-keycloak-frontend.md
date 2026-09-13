---
name: integracion-keycloak-frontend
description: keycloak-js + AuthProvider (Context) + PrivateRoute + apiFetch — starter kit de auth completo y reutilizable sin cambios estructurales
tags: [frontend, keycloak, seguridad, "reusable:media", "describe:original"]
source: Frontend/src/auth/, Frontend/src/services/api.ts
updated: 2026-09-13
---

# Integración frontend–Keycloak

Patrón limpio con `keycloak-js` v26 (SDK oficial, adapter directo — no `oidc-client` ni NextAuth):

- `auth/keycloak.ts` — instancia única de `Keycloak({ url, realm: 'sigma-bb-realm', clientId: 'sigma-frontend' })`.
- `auth/AuthProvider.tsx` — Context Provider que inicializa Keycloak con `onLoad: 'check-sso'` + `pkceMethod: 'S256'` (Authorization Code + PKCE, correcto para SPA pública). Mantiene `authenticated`, `initialized`, `token` en estado React. Auto-refresca el token cada 10s vía `updateToken(30)` (renueva si expira en <30s); si falla, fuerza logout. Expone `login()`/`logout()` delegando al SDK con `redirectUri` explícito.
- `auth/PrivateRoute.tsx` — guard de rutas: loader mientras `!initialized`, redirige a `/login` con `state={{from}}` si no autenticado (permite volver post-login).
- `services/api.ts` — helper `apiFetch()` que inyecta `Authorization: Bearer <token>` automáticamente en cada request; no envuelve manejo de errores/refresh (delegado al interval de `AuthProvider`).

## Por qué es la pieza más madura del frontend actual

A diferencia del resto del frontend (todavía en bootstrap, ver [[arquitectura-frontend]]), este patrón de 4 archivos pequeños con responsabilidad única (instancia SDK / provider de contexto / guard de ruta / helper de fetch) está completo y bien separado — es un starter kit de auth listo para usar.

## Reutilizable en MalphasOS

> **Corregido el 2026-09-13, y es la corrección más cara que ha hecho este wiki.** Aquí decía `reusable:alta` — «portable sin cambios estructurales, solo actualizando `realm`/`clientId`». **Era cierto hasta que MalphasOS eligió Angular.** Estas cuatro piezas son React y **no se portan**: hay que reescribirlas contra `keycloak-angular`.

`reusable:media` — **el patrón se conserva, el código no.** Es la primera vez que el proyecto desecha algo marcado como reutilizable, y se hizo con los ojos abiertos: se prefirió la correspondencia estructural con el backend hexagonal a ahorrar la reescritura de cuatro archivos pequeños. Ver `Documentation/wiki/documentos/declaracion-diseno-frontend.md`.

**Lo que sí se porta, y es lo que valía**: la separación en cuatro responsabilidades únicas —instancia del SDK, proveedor de sesión, guard de ruta, inyección del *Bearer*—, el flujo de código de autorización con PKCE, el refresco anticipado del token y el cierre de sesión si el refresco falla. Eso es diseño, no código, y sobrevive al cambio de framework.

**Y una precisión que esta nota no hacía**: el guard de ruta **no autoriza, oculta**. El permiso lo comprueba el servidor en cada llamada. Ver [[arquitectura-frontend-malphasos]] y [[modelo-de-permisos]].

Para el realm y sus clientes, [[keycloak-configuracion]] sigue valiendo sin cambios.

## Notas relacionadas

[[arquitectura-frontend]] · [[arquitectura-frontend-malphasos]] · [[keycloak-configuracion]] · [[seguridad-keycloak-backend]] · [[modelo-de-permisos]]
