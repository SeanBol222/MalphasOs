---
name: manejo-global-excepciones
description: Patrón de manejo de excepciones — catálogo enum + RestControllerAdvice + DTO de error, repetido por bounded context
tags: [arquitectura, backend, excepciones, "reusable:media"]
source: Backend/sigma-bb/src/main/java/.../bootstrap/exception/
estado: inconsistente
updated: 2026-08-27
---

# Manejo global de excepciones

## Patrón base (`bootstrap/exception/`)

`@RestControllerAdvice` centralizado (`GlobalControllerAdvice`) + catálogo de errores como `enum` (`GlobalErrorCatalog`) + DTO inmutable con builder (`GlobalErrorResponse`: `code`, `message`, `details: List<String>`, `timestamp`).

Solo 2 handlers implementados a nivel global: `DataAccessException` → 500 (`DATABASE_ERROR`) y `MethodArgumentNotValidException` → 400 (`INVALID_DATA`, `details` = mensajes de campo desde `BindingResult`).

## ⚠️ Inconsistencia real detectada

El handler de validación "global" retorna `ClientErrorResponse` (un tipo específico de `client_hexagon.domain.model.error_model`), no `GlobalErrorResponse` — el advice global ya tiene una fuga de un tipo de dominio específico. No es un detalle menor: significa que el contrato de error no es realmente uniforme entre endpoints.

## El patrón real es "por bounded context", no global

Cada hexágono repite el mismo esqueleto con su propio scope:

- **client_hexagon**: `ClientGlobalControllerAdvice` (`@RestControllerAdvice(assignableTypes = {...4 controllers...})`, no es global de verdad) + `ClientErrorCatalog` + `ClientErrorResponse`. Excepciones: `ClientNotFoundException`, `HeadquarterFoundException`, `ServiceAreaFoundException`, `ClientEquipmentFoundException`. Incluye catch-all de `Exception.class` → 500 con stacktrace resumido en `details`.
- **person_hexagon**: `PersonGlobalControllerAdvice` + `PersonErrorCatalog` (incluye `UNKNOWN_ERROR`, que además se reutiliza cruzado desde `ClientGlobalControllerAdvice` — acoplamiento entre catálogos que se supone independientes). Excepciones: `PersonNotFoundException` + 4 excepciones de Keycloak.
- **location_hexagon**: `LocationGlobalHandlerError`. Excepciones: `CityNotFoundException`, `CountryNotFoundException` — pero viven en `infrastructure/output/errors`, **no** en `domain/exception` como en los otros dos hexágonos (inconsistencia de ubicación entre hexágonos, no solo de nombres).

Ventaja real de este patrón: buen aislamiento entre bounded contexts. Costo real: boilerplate case-por-caso casi idéntico repetido 3+ veces (`Catalog` + `Response` + `Advice` con la misma forma), sin una interfaz o clase base compartida que fuerce consistencia.

## Reutilizable en MalphasOS

`reusable:media`. El **esqueleto** (catálogo enum + advice + DTO con code/message/details/timestamp) es sólido y vale la pena portarlo, pero al hacerlo en MalphasOS conviene: (1) definir una interfaz/clase base común para `ErrorCatalog` y `ErrorResponse` que los catálogos por hexágono implementen, evitando la duplicación exacta; (2) decidir explícitamente si el advice "global" real cubre solo excepciones transversales (validación, acceso a datos) y cada hexágono maneja únicamente las suyas, sin filtrar tipos de un hexágono a otro; (3) mantener la ubicación de las excepciones de dominio consistente (`domain/exception` en todos los hexágonos, no mezclado con `infrastructure`).

## Notas relacionadas

[[patron-catalogo-errores-por-contexto]] · [[dominio-cliente]] · [[dominio-persona-identidad]] · [[dominio-ubicacion]] · [[deuda-tecnica-y-riesgos]]
