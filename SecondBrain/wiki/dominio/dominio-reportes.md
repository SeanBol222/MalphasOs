---
name: dominio-reportes
description: reports_hexagon — agregador cross-dominio desacoplado vía ReportDataProviderPort genérico
tags: [dominio, backend, reportes, "reusable:alta", "describe:original"]
source: Backend/sigma-bb/src/main/java/.../reports_hexagon/, shared/application/ports/input/ReportDataProviderPort.java
updated: 2026-09-27
---

# Dominio Reportes (`reports_hexagon`)

## El patrón: agregación cross-hexágono sin acoplamiento

`ReportService` **no consulta la BD directamente**: depende de `ReportDataProviderPort<T>` — un puerto genérico definido en `shared` (no en `reports_hexagon`), con `domainName()` + `provideReportData(id)`. `reports_hexagon` inyecta implementaciones concretas vía `@Qualifier`: `equipmentReportProvider` (de [[dominio-equipo-mantenimiento]]) y `countryReportProvider` (de [[dominio-ubicacion]]).

`reports_hexagon` **no conoce las clases internas** de los hexágonos que agrega — solo el contrato `ReportDataProviderPort<ReportData>`. Es el patrón plugin/strategy aplicado a agregación de reportes: cualquier hexágono nuevo puede exponer un provider propio sin que `reports_hexagon` cambie una línea.

## API expuesta

`POST /v1/api/reports` con body `ReportRequest(reportId, modelId)` → `ReportResponseDTO` (árbol anidado Equipment→EquipmentType/Brand/Model→Manufacturer→Country), mapeado a mano campo a campo (no usa MapStruct aquí, a diferencia del resto del backend).

## Reutilizable en MalphasOS

`reusable:alta` — este es exactamente el patrón que MalphasOS necesitará si separa un módulo de reportes de los módulos de negocio (cliente, equipo/mantenimiento): definir el puerto genérico en la capa compartida, no en el hexágono de reportes, y que cada dominio de negocio provea su propio adapter. Evita el acoplamiento típico de "el módulo de reportes conoce todas las entidades del sistema".

## ⚠️ MalphasOS construyó su módulo de reportes el 2026-09-27, y no es esto

**Esta nota sigue describiendo el original y sigue siendo cierta**, pero conviene leerla sabiendo que el módulo de MalphasOS ya existe y **resolvió otro problema**. Ver [[dominio-reporte-servicio]].

Lo que hay aquí es un **agregador de consulta**: junta datos de varios hexágonos para devolver un árbol. Lo que MalphasOS construyó es una **entidad de negocio con ciclo de vida** —el reporte de lo que se hizo sobre un equipo, que se abre, se llena, se verifica y se cierra—. Comparten el nombre y poco más.

Por eso **`ReportDataProviderPort` no se usó**: el reporte de servicio no agrega nada, cuelga de una orden de trabajo y consulta a los módulos vecinos por sus puertos de entrada normales, como hacen `work-order` y los demás. El patrón sigue **disponible y sin gastar** para el día que haga falta un reporte que de verdad agregue —el PDF de RF-17 es el candidato—, y entonces esta nota volverá a ser la referencia.

## Notas relacionadas

[[dominio-reporte-servicio]] · [[dominio-equipo-mantenimiento]] · [[dominio-ubicacion]] · [[patron-report-data-provider]] · [[arquitectura-hexagonal]]
