---
name: dominio-ubicacion
description: location_hexagon — Country/City, referencia de la Generación 2 (agregados ricos + eventos) aplicada a un dominio simple
tags: [dominio, backend, ubicacion, "reusable:alta"]
source: Backend/sigma-bb/src/main/java/.../location_hexagon/
updated: 2026-08-29
---

# Dominio Ubicación (`location_hexagon`)

## Modelo de dominio

`Country` (1) → `City` (N). Implementado como **agregado rico** ejemplar: `City extends AggregateRoot` ([[aggregate-root-pattern]]), con factoría estática `City.create(name, countryId)` y métodos de negocio (`updateCity`, `updateCityPatch`, `deleteCity`) que registran `DomainEvent`s internamente (`CityCreatedEvent`, `CityUpdatedEvent`, `CityDeletedEvent`).

## Por qué esta nota importa más de lo que su tamaño sugiere

`location_hexagon` es el dominio más simple del sistema (solo dos entidades), pero es junto con `equipment_hexagon` la referencia de **forma** de la Generación 2 (ver [[evolucion-arquitectonica-crud-a-cqrs]]): agregados + eventos + `EventDispatcherPort` con `@Qualifier`. Precisamente por ser simple, es la mejor plantilla para ver la estructura del patrón sin el ruido de un dominio complejo.

> **Matizado el 2026-08-29, al migrarlo.** Esta nota lo llamaba *"el mejor ejemplo pedagógico de cómo se ve el patrón completo"*. La estructura sí lo es; el código no. Los agregados llevaban `@Data`, que les daba un setter público por campo y permitía cambiarlos sin emitir nada; tenían la igualdad rota por `callSuper = true`; uno de sus métodos escribía en la metadata un tipo de evento distinto del que decía la clase que construía; y su método de borrado no modificaba estado alguno. **Estudiar la forma, no copiar la implementación.** El detalle completo en [[migracion-location-hallazgos]].

## Casos de uso REST

`RestControllerCity`, `RestControllerCountry` — incluyen `PATCH` (parcial) además del CRUD completo.

## Excepciones de dominio

`CityNotFoundException`, `CountryNotFoundException` — **viven en `infrastructure/output/errors`, no en `domain/exception`** como en `client_hexagon`/`person_hexagon` (inconsistencia de ubicación entre hexágonos, señalada también en [[manejo-global-excepciones]]). Manejadas por `LocationGlobalHandlerError`.

## Reutilizable en MalphasOS

`reusable:alta` **para la estructura**, no para el código. Sigue siendo más útil estudiar `location_hexagon` que `client_hexagon`, porque representa el patrón que sí se quiere replicar. Corregir al portar: los cuatro defectos de [[migracion-location-hallazgos]], y ubicar las excepciones de dominio en `domain/exception` de forma consistente.

**Migrado por completo el 2026-08-29** (esquema, dominio, aplicación, persistencia y REST). `Country` y `City` son los primeros agregados de Generación 2 de MalphasOS, con identidad por UUID, igualdad por identidad, sin setters, y con `rename` / `relocateTo` / `deactivate` en lugar de `updateX` / `updateXPatch` / `deleteX`.

## Notas relacionadas

[[aggregate-root-pattern]] · [[migracion-location-hallazgos]] · [[evolucion-arquitectonica-crud-a-cqrs]] · [[dominio-equipo-mantenimiento]] · [[manejo-global-excepciones]] · [[esquema-bd-v4]]
