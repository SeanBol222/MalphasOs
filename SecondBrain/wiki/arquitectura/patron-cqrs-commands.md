---
name: patron-cqrs-commands
description: Cómo se aplica CQRS por commands en equipment_hexagon — no es CQRS completo, es separación de puertos + commands inmutables
tags: [arquitectura, backend, cqrs, "reusable:alta"]
source: Backend/sigma-bb/src/main/java/.../equipment_hexagon/application/services/*/commands/
estado: incompleto
updated: 2026-08-27
---

# Patrón CQRS por Commands

Introducido en el commit `2d39984 Fix: applying CQRS patterns`, visible sobre todo en [[dominio-equipo-mantenimiento]] (`equipment_hexagon`). **No es CQRS completo** (no hay Command Bus/Mediator, ni Event Sourcing como fuente de verdad) — es una separación de responsabilidades en dos niveles independientes:

## A) Separación de puertos read/write (selectiva, no universal)

Para `Equipment` concretamente:
- `EquipmentServicePort` (solo lectura: `findAll`/`findById`) → implementado por `EquipmentQueryService`.
- `EquipmentCommandServicePort` (`save`/`update`/`patchUpdate`/`delete`) → implementado por `EquipmentService`.
- El controller inyecta ambos puertos por separado, aunque los expone en el mismo `@RestController`.

Esta separación **no está en todo el hexágono**: `TechnicalVerificationService` implementa un único puerto con los 6 métodos (find + CUD) mezclados, aunque igual usa Commands para escritura. Es decir, la separación read/write es opcional; los Commands sí son la constante.

## B) Commands como `record` inmutables (esto es el patrón central, sí es universal)

Un `record` por operación de escritura — `CreateEquipmentCommand`, `UpdateEquipmentCommand`, `EquipmentPatchCommand`, `DeleteEquipmentCommand` — en `application/services/<dominio>_services/commands/`. Son DTOs de la capa de aplicación, **distintos** de los DTOs REST (`EquipmentRequest`). El controller mapea Request→Command a mano; el Command no tiene lógica, solo transporta datos primitivos hacia el service, que reconstituye el agregado de dominio (`Equipment.create(...)`, `equipment.updateEquipment(...)`).

## Contraste con el patrón viejo

`client_hexagon` (`ClientEquipmentService`) recibe directamente el **modelo de dominio mutable** en el service y lo muta in-place (`clientEquipment.setIdentificadorEquipoCliente(...)`) — sin Commands, sin eventos. Es el "antes" que este patrón reemplaza. Ver [[evolucion-arquitectonica-crud-a-cqrs]].

## Reutilizable en MalphasOS

`reusable:alta` — los Commands como records inmutables por operación de escritura son el patrón a imitar desde el día uno en toda la capa de aplicación nueva de MalphasOS, en vez de mutar modelos de dominio desde el service. La separación read/write en puertos distintos es opcional y se puede aplicar selectivamente donde aporte valor (agregados con lecturas complejas o de alto tráfico), sin forzarla en todos los módulos.

## Notas relacionadas

[[dominio-equipo-mantenimiento]] · [[evolucion-arquitectonica-crud-a-cqrs]] · [[eventos-de-dominio]] · [[aggregate-root-pattern]]
