---
name: dominio-persona-identidad
description: person_hexagon — Person + integración con Keycloak Admin API para crear usuarios/roles
tags: [dominio, backend, identidad, keycloak, "reusable:alta", "describe:ambos"]
source: Backend/sigma-bb/src/main/java/.../person_hexagon/
estado: incompleto
updated: 2026-09-09
---

# Dominio Persona e Identidad (`person_hexagon`)

## Modelo de dominio

`Person` (cédula, nombres, apellidos, `tipoPersona`, `segundoTipoPersona`) + `EmailPerson`/`PhonePerson` propios. Sin import cruzado con `Client`/`Manager` en los modelos de dominio, aunque **un encargado sí es una persona** en el esquema y en el flujo de creación (ver [[relacion-manager-persona]]).

## Casos de uso REST

`PersonRestAdapter`, `EmailPersonRestAdapter`, `PhonePersonRestAdapter` (CRUD estándar). `PersonCommunicationAdapter` es un adapter de entrada distinto, no REST — probablemente para invocación interna/mensajería.

## Integración con Keycloak (el patrón más valioso de este hexágono)

`PersonIdentityPort` (puerto de salida) implementado por `PersonIdentityAdapter`, que usa el admin client de Keycloak inyectado ([[seguridad-keycloak-backend]]):

1. `createUser(PersonIdentityResponse, RoleType)` construye `UserRepresentation` + `CredentialRepresentation` y llama `keycloakClient.realm("sigma-bb-realm").users().create(user)`.
2. Asigna grupos según `RoleType`: `ENGINEER→"engineers"`, `CEO_CLIENT→"clients"`, `ADMIN→"admins"`.
3. Traduce códigos HTTP de la respuesta de Keycloak a excepciones de dominio propias: `KeycloakUserAlreadyExistsException` (409), `KeycloakInvalidDataException` (400), `KeycloakUnauthorizedException` (401/403), `KeycloakConnectionException` (default).
4. `PersonIdentityResponse` es un DTO interno exclusivo de esta integración (username, email, nombres, password), separado tanto del modelo de dominio `Person` como de los DTOs REST — tres representaciones distintas de "una persona" a propósito, cada una con su responsabilidad.

⚠️ `createSuperAdminUser` está **sin implementar** (`return null`) — deuda técnica pendiente, no asumir que existe un flujo de creación de super-admin funcional.

## Excepciones de dominio

`PersonNotFoundException` + las 4 de Keycloak arriba, manejadas por `PersonGlobalControllerAdvice`/`PersonErrorCatalog`. Ver inconsistencias (reutilización cruzada de `UNKNOWN_ERROR` con `client_hexagon`) en [[manejo-global-excepciones]].

## Estado en MalphasOS (migrado el 2026-08-28)

Módulo migrado completo en seis commits, conservando el patrón de Generación 1. Se corrigieron **22 defectos** en el camino, varios capaces de romper el sistema: ver el inventario en [[migracion-person-hallazgos]].

Cambios de diseño respecto al original: `tipoPersona` pasa de `String` al enum `PersonType`; las reglas de combinación de tipos viven en el dominio y no en una función SQL sin trigger; el adaptador de comunicación interna queda aplazado hasta que exista el módulo de clientes; y `createSuperAdminUser` no se incluye en el puerto por estar sin implementar.

## El puerto de identidad, ampliado (2026-09-09)

Al migrar, `PersonIdentityPort` solo sabía **crear** un usuario y **borrarlo** para deshacer un alta fallida. Eso dejaba dos huecos que el original también tenía y que nadie había señalado como defectos hasta el contraste ERS↔código del 2026-09-02:

- **`delete` no tocaba Keycloak**: la persona quedaba inactiva en la base y su cuenta seguía emitiendo tokens válidos.
- **`update` no propagaba nada**: los dos registros divergían desde la primera edición.

El puerto suma ahora `disableUser(userId)` y `updateUserProfile(userId, perfil)`. Las decisiones que sostienen esas dos operaciones —por qué se deshabilita en vez de borrar, en qué orden se llama a cada sistema, qué se propaga y qué no se puede propagar todavía, y qué ventana queda abierta pese a todo— están en [[sincronizacion-con-proveedor-de-identidad]], porque aplican a cualquier adaptador hacia un sistema que también guarda estado, no solo a este.

El puerto suma además `syncGroup(userId, roleType)` desde el **2026-10-04**: **cambiar `tipoPersona` ya mueve al usuario de grupo**, y este párrafo decía lo contrario. Era deuda heredada, y lo que la volvió urgente fue el filtrado por dueño del mismo día —ver [[filtrado-por-dueno]]—, porque el alcance de lectura se decide por el tipo y la fila podía contradecir al grupo sin que nada lo notara.

Sigue **sin** hacer, y no por olvido: **no existe `enableUser`**, porque no hay camino de reactivación que lo llamaría.


## Reutilizable en MalphasOS

`reusable:alta` para todo el patrón de integración Keycloak (`PersonIdentityPort`/Adapter, traducción de códigos HTTP a excepciones de dominio) — es directamente portable cambiando el nombre del realm. `reusable:media` para `RoleType` y los nombres de grupos concretos (`engineers`/`clients`/`admins`), que son específicos de este negocio pero sirven como plantilla de cuántos roles definir. Completar `createSuperAdminUser` es tarea pendiente a resolver en MalphasOS, no algo que se pueda copiar ya hecho.

## Los cinco tipos, y cuáles son usuarios (2026-09-13)

`PersonType` tiene cinco valores y `RoleType` tres, **y la diferencia no es un olvido**: el javadoc de los dos lo dice. `PersonType` describe **qué es alguien para el negocio**; `RoleType`, **con qué permisos entra**. No toda persona registrada necesita usuario.

| `PersonType` | ¿Usuario? | Grupo | ¿Se crea por el API? |
|---|---|---|---|
| `ENGINEER` | Sí | `engineers` | Sí, con `super.person.write` |
| `ADMIN` | Sí | `admins` | Sí, con `super.person.write` |
| `CEO_CLIENT` | Sí | `clients` | Sí, con `person.write` |
| `MANAGER` | **No** | — | Sí, por `POST /persons`, que no crea cuenta |
| `SUPER_ADMIN` | Sí | — | **No. Solo a mano en Keycloak** |

**`MANAGER` no es un cabo suelto.** Su razón está escrita: *«un encargado puede existir como contacto de una sede sin acceder nunca a la aplicación»*. Y no se pide por el API: lo fija `ManagerService` al registrar a alguien como encargado de una sede o de un área —*«el tipo de persona lo fija este servicio, no quien llama»*—.

Es además **el único segundo tipo válido**, impuesto en tres capas: una constante del agregado, un `CHECK` del esquema y una prueba de regresión. Eso es lo que permite que **un ingeniero sea además encargado**, que es lo que `WorkOrderService` contempla al aceptar que cualquiera de los dos tipos sea `ENGINEER`.

**`SUPER_ADMIN` sí era un cabo suelto**, y ahora es una decisión. `RoleType` dice que el alta *«quedó sin implementar en el original»*; desde el 2026-09-13 esa ausencia es deliberada: el super usuario se crea entrando a Keycloak, y por eso `super.admin.full` no cuelga de ningún grupo. Ver [[modelo-de-permisos]] para la escalera completa.

## Notas relacionadas

[[sincronizacion-con-proveedor-de-identidad]] · [[seguridad-keycloak-backend]] · [[traduccion-de-fallos-de-adaptadores]] · [[relacion-manager-persona]] · [[dominio-cliente]] · [[keycloak-configuracion]] · [[deuda-tecnica-y-riesgos]]
