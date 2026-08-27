---
name: integracion-keycloak-frontend
description: keycloak-js + AuthProvider (Context) + PrivateRoute + apiFetch — starter kit de auth completo y reutilizable sin cambios estructurales
tags: [frontend, keycloak, seguridad, "reusable:alta"]
source: Frontend/src/auth/, Frontend/src/services/api.ts
updated: 2026-08-27
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

`reusable:alta` — portable sin cambios estructurales, solo actualizando `realm`/`clientId` a los de MalphasOS (ver [[keycloak-configuracion]] para el patrón de 3 clients a replicar).

## Notas relacionadas

[[arquitectura-frontend]] · [[keycloak-configuracion]] · [[seguridad-keycloak-backend]]
