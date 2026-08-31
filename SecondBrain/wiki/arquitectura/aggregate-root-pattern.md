---
name: aggregate-root-pattern
description: Clase base AggregateRoot que acumula eventos de dominio internamente (registerEvent/pullEvents)
tags: [arquitectura, backend, ddd, "reusable:alta"]
source: Backend/sigma-bb/src/main/java/.../shared/domain/events/AggregateRoot.java
updated: 2026-08-29
---

# `AggregateRoot` — agregados que registran sus propios eventos

Clase base en `shared/domain/events/` de la que heredan los agregados ricos del dominio (`Equipment`, `Brand`, `Manufacturer`, `Model`, `EquipmentType`, `TechnicalVerification` en [[dominio-equipo-mantenimiento]]; `City` en [[dominio-ubicacion]]).

Provee `registerEvent(event)` (acumula en una lista interna) y `pullEvents()` (devuelve y **vacía** la lista) — patrón clásico DDD de "aggregate that records events". Cada método de negocio del agregado (`create`, `updateX`, `updateXPatch`, `deleteX`) registra internamente un evento tipado antes de retornar. El service de aplicación, tras persistir, llama `dispatchEvents(aggregate)` que hace `aggregate.pullEvents().forEach(eventDispatcherPort::dispatch)`.

Es independiente de Spring (sin anotaciones de framework) — pertenece puramente al dominio, lo que lo hace portable sin arrastrar dependencias.

## Ejemplo de factoría estática

`City.create(name, countryId)` — el agregado se construye vía método de fábrica, no constructor público directo, y registra `CityCreatedEvent` como parte de la creación.

## Estado en MalphasOS (portado el 2026-08-29)

> **Corregido el 2026-08-29.** Esta nota decía `reusable:alta` **"tal cual, sin cambios"**. La clase base en sí es correcta, pero portarla sin tocar nada dejaba dos problemas fuera de la vista.

La clase se conserva casi intacta y suma tres cosas:

- **`metadataFor(eventType)`**, con `aggregateType()` y `aggregateId()` como métodos abstractos. En el original cada método que emitía un evento repetía cinco líneas de construcción de `EventMetadata` con el tipo del agregado escrito a mano; `City` lo hacía cuatro veces. Un error de dedo ahí solo se habría notado depurando un consumidor.
- **`pullEvents()` devuelve una lista inmutable**, para que quien la recibe no altere lo que el agregado registró.
- **`hasPendingEvents()`**, para no llamar al despachador en balde, y la clase pasa a ser `abstract`.

**El aviso que esta nota no daba**: heredar de `AggregateRoot` es una trampa para `@EqualsAndHashCode(callSuper = true)`, porque la superclase no redefine `equals` y la llamada acaba en la comparación por referencia de `Object`. Los agregados del original caían justo ahí. Ver [[migracion-location-hallazgos]].

## Reutilizable en MalphasOS

`reusable:alta` para el patrón. La implementación pide los ajustes de arriba.

## Notas relacionadas

[[eventos-de-dominio]] · [[migracion-location-hallazgos]] · [[patron-cqrs-commands]] · [[dominio-equipo-mantenimiento]] · [[dominio-ubicacion]]
