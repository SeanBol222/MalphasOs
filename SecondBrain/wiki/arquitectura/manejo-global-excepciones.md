---
name: manejo-global-excepciones
description: Patrón de manejo de excepciones — catálogo enum + RestControllerAdvice + DTO de error, repetido por bounded context
tags: [arquitectura, backend, excepciones, "reusable:media"]
source: Backend/sigma-bb/src/main/java/.../bootstrap/exception/
estado: inconsistente
updated: 2026-08-29
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

## ⚠️ Segundo problema: el mensaje de la excepción de BD llega al cliente

El handler de `DataAccessException` hace `details(List.of(ex.getMessage()))`. Ese mensaje suele contener nombres de tablas, columnas y fragmentos de SQL, que quedan expuestos en la respuesta HTTP. Es divulgación de información interna hacia cualquiera que provoque un error de base de datos.

## Estado en MalphasOS (migrado el 2026-08-27)

El paquete ya está portado, con estas correcciones aplicadas:

- Ambos handlers devuelven `GlobalErrorResponse`; se eliminó la fuga del tipo del hexágono de clientes.
- El mensaje de `DataAccessException` va al log; la respuesta solo lleva el código de error.
- `GlobalErrorResponse` pasó a ser un `record`: una respuesta de error no debe mutar tras construirse.
- Los códigos del catálogo se alinearon con su propio javadoc.
- Las violaciones de integridad dejan de responder 500. Su origen es lo que envió el cliente —una clave duplicada, un valor fuera del catálogo—, así que se traducen en 409 con código propio, y el mensaje técnico, que nombra tablas y restricciones, se queda en el log. Llegar a ese manejador delata además una validación ausente en el dominio: ver [[reglas-de-negocio-en-el-esquema]].

Sigue **pendiente** definir la interfaz o clase base común para los catálogos por módulo: hoy solo existe el catálogo transversal, y la duplicación aparecerá cuando se migre el primer módulo de dominio con sus propias excepciones.

## Reutilizable en MalphasOS

`reusable:media`. El **esqueleto** (catálogo enum + advice + DTO con code/message/details/timestamp) es sólido y ya está portado, pero al extenderlo a cada módulo conviene: (1) definir una interfaz/clase base común para `ErrorCatalog` y `ErrorResponse` que los catálogos por hexágono implementen, evitando la duplicación exacta; (2) mantener el advice transversal cubriendo solo validación y acceso a datos, y que cada módulo maneje únicamente lo suyo sin filtrar tipos entre módulos; (3) mantener la ubicación de las excepciones de dominio consistente — en MalphasOS ya se decidió `domain/exception` en todos los módulos, ver [[decisiones-tecnicas-malphasos]].

## Decidido en MalphasOS el 2026-08-29: se repite por módulo

Este wiki arrastraba como pendiente si el manejo de excepciones debía compartir una base entre módulos o repetirse por contexto acotado. Al escribir el segundo catálogo —`LocationErrorCatalog`, tras `PersonErrorCatalog`— hubo que decidir, y se optó por **repetir**.

El motivo: cada contexto acotado es dueño de su contrato de error y puede cambiar sus códigos sin arrastrar a los demás. Lo que se duplica es la forma —un enum de catálogo, un `record` de respuesta, un advice limitado con `assignableTypes`—, no el comportamiento. Extraer una clase base en `bootstrap` habría acoplado todos los contextos a una forma compartida, y obligado a refactorizar `person` para estrenarla.

El coste es real y conviene tenerlo presente: cuando llegue `client` habrá una tercera copia de la misma estructura. Si llegara a haber cinco o seis y ninguna divergiera, la decisión merecería revisarse.

## Notas relacionadas

[[patron-catalogo-errores-por-contexto]] · [[traduccion-de-fallos-de-adaptadores]] · [[dominio-cliente]] · [[dominio-persona-identidad]] · [[dominio-ubicacion]] · [[deuda-tecnica-y-riesgos]]
