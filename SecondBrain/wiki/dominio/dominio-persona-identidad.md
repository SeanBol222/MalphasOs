---
name: dominio-persona-identidad
description: person_hexagon — Person + integración con Keycloak Admin API para crear usuarios/roles
tags: [dominio, backend, identidad, keycloak, "reusable:alta"]
source: Backend/sigma-bb/src/main/java/.../person_hexagon/
estado: incompleto
updated: 2026-08-29
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

## Reutilizable en MalphasOS

`reusable:alta` para todo el patrón de integración Keycloak (`PersonIdentityPort`/Adapter, traducción de códigos HTTP a excepciones de dominio) — es directamente portable cambiando el nombre del realm. `reusable:media` para `RoleType` y los nombres de grupos concretos (`engineers`/`clients`/`admins`), que son específicos de este negocio pero sirven como plantilla de cuántos roles definir. Completar `createSuperAdminUser` es tarea pendiente a resolver en MalphasOS, no algo que se pueda copiar ya hecho.

## Notas relacionadas

[[seguridad-keycloak-backend]] · [[traduccion-de-fallos-de-adaptadores]] · [[relacion-manager-persona]] · [[dominio-cliente]] · [[keycloak-configuracion]] · [[deuda-tecnica-y-riesgos]]
