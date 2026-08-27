---
name: patron-report-data-provider
description: ReportDataProviderPort<T> genérico en shared — patrón plugin/strategy para que cada dominio provea datos de reportes sin acoplar el módulo de reportes
tags: [patron, backend, reportes, "reusable:alta"]
source: Backend/sigma-bb/src/main/java/.../shared/application/ports/input/ReportDataProviderPort.java
updated: 2026-08-27
---

# Patrón: `ReportDataProviderPort<T>` (plugin/strategy para reportes)

Puerto genérico definido en `shared` (no en `reports_hexagon`): `domainName()` + `provideReportData(id)`. Cada hexágono de negocio que quiera exponer datos a reportes implementa su propio adapter (`equipmentReportProvider`, `countryReportProvider`) y `reports_hexagon` los inyecta por `@Qualifier` sin conocer las clases internas de esos hexágonos. Ver el caso de uso completo en [[dominio-reportes]].

## Por qué es un patrón valioso más allá de "reportes"

Es la solución general al problema de "un módulo transversal necesita datos de N módulos de negocio sin acoplarse a sus internals": el contrato vive en la capa compartida, la implementación vive en cada dominio. Aplica a cualquier funcionalidad cross-cutting similar (búsqueda global, exportación, auditoría de negocio), no solo a reportes.

## Reutilizable en MalphasOS

`reusable:alta` — si MalphasOS separa un módulo de reportes (o cualquier funcionalidad que agregue datos de varios módulos de negocio), replicar este contrato desde el diseño inicial en vez de dejar que el módulo agregador termine importando clases internas de cada dominio.

## Notas relacionadas

[[dominio-reportes]] · [[dominio-equipo-mantenimiento]] · [[arquitectura-hexagonal]]
