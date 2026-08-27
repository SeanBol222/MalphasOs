---
name: dominio-reportes
description: reports_hexagon — agregador cross-dominio desacoplado vía ReportDataProviderPort genérico
tags: [dominio, backend, reportes, "reusable:alta"]
source: Backend/sigma-bb/src/main/java/.../reports_hexagon/, shared/application/ports/input/ReportDataProviderPort.java
updated: 2026-08-27
---

# Dominio Reportes (`reports_hexagon`)

## El patrón: agregación cross-hexágono sin acoplamiento

`ReportService` **no consulta la BD directamente**: depende de `ReportDataProviderPort<T>` — un puerto genérico definido en `shared` (no en `reports_hexagon`), con `domainName()` + `provideReportData(id)`. `reports_hexagon` inyecta implementaciones concretas vía `@Qualifier`: `equipmentReportProvider` (de [[dominio-equipo-mantenimiento]]) y `countryReportProvider` (de [[dominio-ubicacion]]).

`reports_hexagon` **no conoce las clases internas** de los hexágonos que agrega — solo el contrato `ReportDataProviderPort<ReportData>`. Es el patrón plugin/strategy aplicado a agregación de reportes: cualquier hexágono nuevo puede exponer un provider propio sin que `reports_hexagon` cambie una línea.

## API expuesta

`POST /v1/api/reports` con body `ReportRequest(reportId, modelId)` → `ReportResponseDTO` (árbol anidado Equipment→EquipmentType/Brand/Model→Manufacturer→Country), mapeado a mano campo a campo (no usa MapStruct aquí, a diferencia del resto del backend).

## Reutilizable en MalphasOS

`reusable:alta` — este es exactamente el patrón que MalphasOS necesitará si separa un módulo de reportes de los módulos de negocio (cliente, equipo/mantenimiento): definir el puerto genérico en la capa compartida, no en el hexágono de reportes, y que cada dominio de negocio provea su propio adapter. Evita el acoplamiento típico de "el módulo de reportes conoce todas las entidades del sistema".

## Notas relacionadas

[[dominio-equipo-mantenimiento]] · [[dominio-ubicacion]] · [[patron-report-data-provider]] · [[arquitectura-hexagonal]]
