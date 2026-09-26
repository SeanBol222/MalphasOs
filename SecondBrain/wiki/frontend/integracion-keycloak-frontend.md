---
name: integracion-keycloak-frontend
description: keycloak-js + AuthProvider (Context) + PrivateRoute + apiFetch — starter kit de auth completo y reutilizable sin cambios estructurales
tags: [frontend, keycloak, seguridad, "reusable:media", "describe:original"]
source: Frontend/src/auth/, Frontend/src/services/api.ts
updated: 2026-09-26
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

## Lo que costó el primer arranque de verdad en Angular (2026-09-26)

La reescritura contra `keycloak-angular` compiló, pasó la batería y **dejó la página en blanco**: ni aplicación, ni redirección a Keycloak, ni error visible. Dos defectos, y los dos comparten una forma — **cosas que solo se manifiestan con un navegador delante**.

**1. `withAutoRefreshToken` exige dos servicios que nadie provee.** `AutoRefreshTokenService` y `UserActivityService` **no son `providedIn: 'root'`**, así que hay que declararlos en `providers`. Sin ellos el inyector falla **antes de pintar nada**: de ahí el blanco sin redirección y sin mensaje útil. Está escrito en el README de la librería, y no haberlo leído costó el fallo entero. La lección no es sobre Angular: **una librería de sesión se lee antes de configurarla, no cuando falla**.

**2. El backend no tenía CORS.** El siguiente intento ya autenticaba, y entonces apareció `Access-Control-Allow-Origin missing` con un **401 que engañaba: era del preflight, no del token**. El detalle completo, con la verificación por mutación, en [[seguridad-keycloak-backend]].

**Los dos son el mismo hallazgo visto dos veces**: el backend llevaba cinco módulos y 645 pruebas en verde sin que una sola petición hubiera salido de un navegador. Ninguna de las dos ausencias era detectable con `MockMvc` ni con `curl`, porque `curl` no manda `Origin` y `MockMvc` no tiene inyector de Angular. **Verificar ejecutando incluye ejecutar por donde el usuario entra.**

Lo que sí se conserva del patrón original, y funciona: el token se adjunta **solo hacia el API propio** —un interceptor con lista explícita de destinos, nunca a todo—, que es la misma decisión que toma `CorsConfig` del otro lado de la línea.

## Notas relacionadas

[[arquitectura-frontend]] · [[arquitectura-frontend-malphasos]] · [[keycloak-configuracion]] · [[seguridad-keycloak-backend]] · [[modelo-de-permisos]]
