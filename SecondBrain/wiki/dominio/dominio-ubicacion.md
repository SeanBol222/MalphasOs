---
name: dominio-ubicacion
description: location_hexagon — Country/City, referencia de la Generación 2 (agregados ricos + eventos) aplicada a un dominio simple
tags: [dominio, backend, ubicacion, "reusable:alta"]
source: Backend/sigma-bb/src/main/java/.../location_hexagon/
updated: 2026-08-27
---

# Dominio Ubicación (`location_hexagon`)

## Modelo de dominio

`Country` (1) → `City` (N). Implementado como **agregado rico** ejemplar: `City extends AggregateRoot` ([[aggregate-root-pattern]]), con factoría estática `City.create(name, countryId)` y métodos de negocio (`updateCity`, `updateCityPatch`, `deleteCity`) que registran `DomainEvent`s internamente (`CityCreatedEvent`, `CityUpdatedEvent`, `CityDeletedEvent`).

## Por qué esta nota importa más de lo que su tamaño sugiere

`location_hexagon` es el dominio más simple del sistema (solo dos entidades), pero es junto con `equipment_hexagon` la referencia de implementación de la **Generación 2** (ver [[evolucion-arquitectonica-crud-a-cqrs]]): agregados + eventos + `EventDispatcherPort` con `@Qualifier`. Precisamente por ser simple, es el mejor ejemplo pedagógico de cómo se ve el patrón completo sin el ruido de un dominio complejo — útil como plantilla de referencia rápida al construir el primer agregado de MalphasOS.

## Casos de uso REST

`RestControllerCity`, `RestControllerCountry` — incluyen `PATCH` (parcial) además del CRUD completo.

## Excepciones de dominio

`CityNotFoundException`, `CountryNotFoundException` — **viven en `infrastructure/output/errors`, no en `domain/exception`** como en `client_hexagon`/`person_hexagon` (inconsistencia de ubicación entre hexágonos, señalada también en [[manejo-global-excepciones]]). Manejadas por `LocationGlobalHandlerError`.

## Reutilizable en MalphasOS

`reusable:alta` — este hexágono completo sirve como plantilla mínima de "cómo se ve un hexágono bien hecho en este repo". Al iniciar MalphasOS, es más útil estudiar `location_hexagon` primero que `client_hexagon`, precisamente por representar el patrón que sí se quiere replicar. Corregir al portar: ubicar las excepciones de dominio en `domain/exception` de forma consistente.

## Notas relacionadas

[[aggregate-root-pattern]] · [[evolucion-arquitectonica-crud-a-cqrs]] · [[dominio-equipo-mantenimiento]] · [[manejo-global-excepciones]] · [[esquema-bd-v4]]
