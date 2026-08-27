---
name: evolucion-arquitectonica-crud-a-cqrs
description: El repo convive con dos generaciones de patrón — CRUD anémico (client/person) vs agregados ricos + commands + eventos (equipment/location) — este es el hallazgo más importante del wiki
tags: [arquitectura, backend, decision-clave, "reusable:alta"]
source: git log — commit 2d39984 "Fix: applying CQRS patterns"
updated: 2026-08-27
---

# La evolución arquitectónica dentro del propio repo (hallazgo clave)

Este es probablemente el dato más accionable de todo este wiki para arrancar MalphasOS bien desde el principio.

## Generación 1 — CRUD anémico (más antigua)

`client_hexagon` y `person_hexagon`. Características:

- El service recibe el **modelo de dominio mutable** directamente y lo muta in-place (ej. `clientEquipment.setIdentificadorEquipoCliente(...)` en `ClientEquipmentService`).
- No hay Commands, no hay eventos de dominio.
- `Manager` es un modelo anémico y desacoplado (solo `identificadorEncargado`, `tipoEncargado`, `estadoActivo`), sin `registerEvent`/`AggregateRoot`.
- Manejo de excepciones acotado al bounded context, sin eventos que notifiquen cambios a otros módulos.

## Generación 2 — agregados ricos + CQRS por commands + eventos (más nueva)

`equipment_hexagon` y `location_hexagon`. Características:

- Agregados que extienden `AggregateRoot` ([[aggregate-root-pattern]]), con factorías estáticas (`City.create(...)`) y métodos de negocio que registran eventos (`registerEvent`).
- Commands inmutables (`record`) por operación de escritura ([[patron-cqrs-commands]]).
- Eventos de dominio tipados por entidad, despachados vía `EventDispatcherPort` ([[eventos-de-dominio]]).
- En algunos casos, separación de puertos read/write.

## Por qué pasó esto

El historial de git (`2d39984 Fix: applying CQRS patterns`) confirma que la Generación 2 es una corrección/evolución deliberada sobre la Generación 1 — no son dos estilos igualmente válidos coexistiendo por diseño, es un dominio (`equipment`) que fue refactorizado hacia un patrón mejor y otros (`client`, `person`) que **todavía no** recibieron esa migración.

## Consecuencia directa para MalphasOS

MalphasOS debería **partir directamente de la Generación 2 en todos sus módulos desde el día uno**, incluyendo el módulo de gestión de clientes (que en el repo original todavía está en Generación 1). No tiene sentido reproducir el patrón anémico solo porque es el que existe hoy en `client_hexagon` — el propio proyecto origen ya está alejándose de él. Ver el detalle de qué modelo de `client_hexagon` sí vale la pena conservar conceptualmente (la jerarquía Client→Headquarter→ServiceArea) en [[dominio-cliente]], pero implementado con agregados + commands + eventos, no copiado tal cual.

## Notas relacionadas

[[aggregate-root-pattern]] · [[patron-cqrs-commands]] · [[eventos-de-dominio]] · [[dominio-cliente]] · [[dominio-equipo-mantenimiento]] · [[sintesis-malphasos]]
