---
name: aggregate-root-pattern
description: Clase base AggregateRoot que acumula eventos de dominio internamente (registerEvent/pullEvents)
tags: [arquitectura, backend, ddd, "reusable:alta"]
source: Backend/sigma-bb/src/main/java/.../shared/domain/events/AggregateRoot.java
updated: 2026-08-27
---

# `AggregateRoot` — agregados que registran sus propios eventos

Clase base en `shared/domain/events/` de la que heredan los agregados ricos del dominio (`Equipment`, `Brand`, `Manufacturer`, `Model`, `EquipmentType`, `TechnicalVerification` en [[dominio-equipo-mantenimiento]]; `City` en [[dominio-ubicacion]]).

Provee `registerEvent(event)` (acumula en una lista interna) y `pullEvents()` (devuelve y **vacía** la lista) — patrón clásico DDD de "aggregate that records events". Cada método de negocio del agregado (`create`, `updateX`, `updateXPatch`, `deleteX`) registra internamente un evento tipado antes de retornar. El service de aplicación, tras persistir, llama `dispatchEvents(aggregate)` que hace `aggregate.pullEvents().forEach(eventDispatcherPort::dispatch)`.

Es independiente de Spring (sin anotaciones de framework) — pertenece puramente al dominio, lo que lo hace portable sin arrastrar dependencias.

## Ejemplo de factoría estática

`City.create(name, countryId)` — el agregado se construye vía método de fábrica, no constructor público directo, y registra `CityCreatedEvent` como parte de la creación.

## Reutilizable en MalphasOS

`reusable:alta`, tal cual, sin cambios. Es infraestructura de dominio genérica y bien diseñada — debería ser una de las primeras piezas a portar al iniciar MalphasOS, junto con [[eventos-de-dominio]].

## Notas relacionadas

[[eventos-de-dominio]] · [[patron-cqrs-commands]] · [[dominio-equipo-mantenimiento]] · [[dominio-ubicacion]]
