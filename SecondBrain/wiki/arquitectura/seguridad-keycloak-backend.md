---
name: seguridad-keycloak-backend
description: Resource server OAuth2/JWT + admin client de Keycloak, dos piezas separadas con responsabilidades distintas
tags: [arquitectura, backend, seguridad, keycloak, "reusable:alta"]
source: Backend/sigma-bb/src/main/java/.../bootstrap/config/keycloak/, bootstrap/config/security/
updated: 2026-09-09
---

# Seguridad — Keycloak en el backend

Dos piezas separadas, no confundir:

## 1. Resource server (autenticar/autorizar requests entrantes)

`SecurityConfig` + `KeycloakRoleConverter` (`bootstrap/config/security/`) — resource server OAuth2/JWT estándar de Spring Security.

- `SecurityFilterChain` permite `/swagger-ui/**` y `/v3/api-docs/**` sin auth; exige autenticación en todo lo demás; CSRF deshabilitado (API stateless).
- `KeycloakRoleConverter` extrae roles desde `resource_access.<CLIENT_ID>.roles` del JWT (client-id hardcodeado `"sigma-api"` en este repo) y los mapea a `GrantedAuthority` de Spring. Los controllers usan `@PreAuthorize` a nivel de método (autorización por operación, no por clase — ver [[dominio-cliente]] para ejemplos), y **en el original todos exigen la misma autoridad, `admin.full`**. Eso es lo que MalphasOS heredó y corrigió el 2026-09-08: ver [[modelo-de-permisos]].
- Toda la config es `@ConditionalOnProperty(app.security.enabled, matchIfMissing=true)` — permite desactivar seguridad completa vía `application.yaml`, útil para tests/dev.
- `issuer-uri` apunta al realm dedicado: `http://keycloak.test:8080/realms/sigma-bb-realm`.

## 2. Admin client (gestión administrativa de usuarios/roles)

`KeycloakAdminConfig` + `KeycloakProperties` (`bootstrap/config/keycloak/`) — cliente admin de Keycloak (`org.keycloak.admin.client.Keycloak`) vía grant `client_credentials`, configurado con `@ConfigurationProperties(prefix="keycloak.admin")`. Se usa para gestión administrativa contra la Admin API de Keycloak (crear usuarios, asignar grupos) — no para autenticar requests entrantes. Ver [[dominio-persona-identidad]] para cómo se usa concretamente al crear un `Person`.

## Estado en MalphasOS (migrado el 2026-08-27)

Portado con tres correcciones:

- **El client id ya no está hardcodeado**: se inyecta desde `app.security.client-id`.
- **Sin casts sin verificar**. El original hacía `(Map<String, Object>) resourceAccess.get(CLIENT_ID)` y `(List<String>) client.get("roles")` sin comprobar tipos. El token es entrada externa: uno con estructura inesperada lanzaba `ClassCastException` dentro del filtro de seguridad. Reescrito con pattern matching de Java 21, cualquier desviación devuelve lista vacía de autoridades.
- **`/actuator/health` añadido a las rutas públicas**, para que el healthcheck del contenedor funcione sin token. Ver [[dockerfile-y-contenedores]].

### Una clase nueva que el original no tiene: `SecurityDisabledConfig`

El original condiciona toda la configuración a `app.security.enabled` con `matchIfMissing = true`. El problema, descubierto al migrarlo: cuando esa propiedad está en `false`, `SecurityConfig` no se carga, pero **Spring Security aplica su cadena por defecto** — basic auth con una contraseña generada en cada arranque. Es decir, "seguridad desactivada" no deja la API abierta ni protegida de forma útil: la vuelve inaccesible, incluida Swagger UI, que devuelve 401.

MalphasOS añade una cadena `permitAll` explícita condicionada a `enabled=false`, que además registra una advertencia visible en el arranque. Así los dos estados son inequívocos y el de desarrollo es utilizable.

~~⚠️ **Estado actual**: la seguridad está desactivada en `application.yaml` porque el realm `malphasos-realm` todavía no existe.~~ **Corregido el 2026-09-08 al verificarlo contra el código**: eso fue cierto hasta que el realm se importó (2026-08-28). Hoy `application.yaml` trae `enabled: ${APP_SECURITY_ENABLED:true}` y la API exige token; solo el `application.yaml` de pruebas la apaga, para que cada prueba se centre en su capa. La protección se verifica en `SecurityIntegrationTest`, con el decodificador de JWT sustituido por un doble.

## El modelo de permisos (desde el 2026-09-08)

Hasta esa fecha **las 83 operaciones exigían `admin.full`** y el modelo de roles del realm era decorativo. Hoy cada operación declara únicamente la autoridad de su recurso —`hasAuthority('client.read')`— y una clase nueva, `ApiAuthority`, reúne el vocabulario de 19 autoridades y la regla de expansión del administrador. La regla se aplica en dos capas, el converter y un bean `RoleHierarchy`, ambas derivadas de la misma constante.

`KeycloakRoleConverter` gana con eso una responsabilidad más: además de extraer los roles del claim, los expande. Es el único punto por el que un token se convierte en autoridades, así que es donde la regla se aplica una vez en lugar de repetirse en cada anotación.

El detalle completo —qué autoridad protege qué, qué recibe cada grupo, qué queda abierto— está en [[modelo-de-permisos]].

## Lo que un JWT válido no sabe: la cuenta pudo morir después de firmarse

Este resource server valida el token **solo con la firma**, sin preguntarle a Keycloak si la cuenta sigue viva. Es lo normal en OAuth2 y es lo que hace que escale; el precio es una ventana que conviene tener escrita antes de que alguien la descubra en producción.

Medido contra un **Keycloak 26.6.1 real** el 2026-09-09, al implementar la baja de personas:

- Deshabilitar el usuario impide autenticarse de nuevo y renovar el token: el intento responde `invalid_grant`.
- **Un token de acceso emitido antes de la baja sigue abriendo el API hasta que caduca.** En el realm de desarrollo, `accessTokenLifespan` son **300 segundos** (`docker/keycloak/import/malphasos-realm-realm.json`).

Es decir: retirar el acceso a alguien surte efecto **para las autenticaciones nuevas, no para las ya emitidas**. Cerrar la ventana del todo exigiría introspección del token en cada petición, un coste por petición que hoy no se paga. Ver [[sincronizacion-con-proveedor-de-identidad]].


## Reutilizable en MalphasOS

`reusable:alta` — el patrón completo (resource server + admin client + role converter desde `resource_access`) es portable prácticamente sin cambios, solo actualizando `CLIENT_ID`, nombre de realm y prefijos de propiedades. Es una de las piezas más maduras y consistentes de todo el backend original. Ya está migrado.

## Notas relacionadas

[[modelo-de-permisos]] · [[sincronizacion-con-proveedor-de-identidad]] · [[dominio-persona-identidad]] · [[integracion-keycloak-frontend]] · [[keycloak-configuracion]] · [[stack-tecnologico]]
