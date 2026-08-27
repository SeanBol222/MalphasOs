---
name: dominio-equipo-mantenimiento
description: equipment_hexagon — Equipment, EquipmentType, Brand, Manufacturer, Model, TechnicalVerification, MetrologicalData. El corazón del negocio de mantenimiento preventivo y la referencia arquitectónica principal
tags: [dominio, backend, mantenimiento-preventivo, nucleo-malphasos, "reusable:alta"]
source: Backend/sigma-bb/src/main/java/.../equipment_hexagon/
updated: 2026-08-27
---

# Dominio Equipo y Mantenimiento Preventivo (`equipment_hexagon`)

**Este es el hexágono más importante de todo el wiki para MalphasOS** — es el motor de dominio de mantenimientos preventivos, y a la vez la mejor referencia arquitectónica del repo (Generación 2 completa, ver [[evolucion-arquitectonica-crud-a-cqrs]]).

## Modelo de dominio y relaciones

```
Manufacturer (name, countryId) ──┐
                                  ├──> Model (invima, manufacturerId, equipmentId)
Equipment (equipmentTypeId, brandId) ──┘
   ├──> EquipmentType (relación por id)
   └──> Brand (relación por id)

EquipmentType (nombre, definición técnica, recomendaciones de cuidado,
               voltaje, amperaje, tecnología predominante, verifiable, unitMaintenanceValue)
   ├── List<MetrologicalData>  (value: BigDecimal, type: String) — value object embebido
   └── Set<UUID> technicalVerification — relación M:N gestionada dentro del propio agregado

TechnicalVerification (description, verificationType) — agregado independiente
TechnicalVerificationEquipment — NO es entidad ni agregado; DTO de transporte para eventos/respuestas
```

Todas las relaciones entre agregados son **por UUID**, no referencias de objeto persistidas — DDD con agregados desacoplados. `EquipmentQueryService.attachRelations()` hace el "join" en memoria para hidratar `equipment.equipmentType`/`equipment.brand` solo en el lado de lectura.

## `TechnicalVerification` y `MetrologicalData` — el núcleo real del mantenimiento preventivo

- **`MetrologicalData`**: value object embebido en `EquipmentType` — parámetros metrológicos esperados/normativos para un tipo de equipo (rangos, unidades de medida a verificar).
- **`TechnicalVerification`**: catálogo independiente de tipos de verificación técnica (ej. "calibración eléctrica", "prueba de fuga"), reutilizable entre varios `EquipmentType` vía `Set<UUID>`.
- Un `EquipmentType` define qué verificaciones técnicas y qué datos metrológicos aplican; cada `Equipment` concreto hereda ese perfil de mantenimiento a través de su tipo. `unitMaintenanceValue` y `verifiable` sugieren costeo y flag de aplicabilidad de mantenimiento.

Esto conecta directamente con las tablas `orden_trabajo`, `reporte_servicio`, `protocolo_mantenimiento`, `verificacion_ingreso`, `verificacion_metrologica` en [[esquema-bd-v4]] — el ciclo completo de una orden de trabajo de mantenimiento.

## Patrón CQRS por commands — ver detalle en [[patron-cqrs-commands]]

Aplicado de forma no uniforme: `Equipment`/`Brand`/`Manufacturer`/`Model`/`EquipmentType` separan puertos read/write; `TechnicalVerificationService` usa un solo puerto pero igual usa Commands para escritura.

## Eventos de dominio por entidad

Cada agregado hereda `AggregateRoot` ([[aggregate-root-pattern]]) y registra eventos tipados por operación (`XCreatedEvent`/`XUpdatedEvent`/`XDeletedEvent`). Caso especial: `EquipmentType` también emite eventos de sub-colección (`MetrologicalDataCreated/Updated/DeletedEvent`, `TechnicalVerificationEquipmentCreated/Updated/DeletedEvent`) cuando se añaden/quitan datos metrológicos o verificaciones asociadas, con validación de duplicados vía `DomainException` pura (sin dependencia de Spring). Ver despacho completo en [[eventos-de-dominio]].

`EquipmentReportProviderAdapter` (en `infrastructure/input/listeners`, pese al nombre de carpeta) no es un listener reactivo real — funciona como agregador síncrono bajo demanda: dado un `modelId`, consulta en cascada `model → equipment → equipmentType → brand → manufacturer` y arma un DTO combinado para [[dominio-reportes]].

## Rutas REST expuestas

```
/v1/api/equipment            GET, GET/{id}, POST, PUT/{id}, PATCH/{id}, DELETE/{id}
/v1/api/manufacturers         GET, GET/{id}, POST, PUT/{id}, PATCH/{id}, DELETE/{id}
/v1/api/brands                GET, GET/{id}, POST, PUT/{id}, PATCH/{id}, DELETE/{id}
/v1/api/models                GET, GET/{id}, POST, PUT/{id}, PATCH/{id}, DELETE/{id}
/v1/api/equipment-types        GET, GET/{id}, POST, PUT/{id}, PATCH/{id}, DELETE/{id}
  /{id}/metrological-data          POST, DELETE, PUT (+ batch)
  /{id}/technical-verification     POST, DELETE, PUT (+ batch)
/v1/api/technical-verifications  GET, GET/{id}, POST, PUT/{id}, PATCH/{id}, DELETE/{id}
```

## Reutilizable en MalphasOS

`reusable:alta` — **debería portarse casi completo**. El modelo de dominio (`Equipment`, `EquipmentType`, `Brand`, `Manufacturer`, `Model`, `TechnicalVerification`, `MetrologicalData`) es genérico y no acopla nada de facturación/gestión ajena al mantenimiento en sí. Es, junto con `location_hexagon`, la plantilla arquitectónica a seguir para todos los módulos nuevos de MalphasOS — no la de `client_hexagon`.

## Notas relacionadas

[[patron-cqrs-commands]] · [[aggregate-root-pattern]] · [[eventos-de-dominio]] · [[dominio-reportes]] · [[esquema-bd-v4]] · [[evolucion-arquitectonica-crud-a-cqrs]] · [[alcance-malphasos]] · [[checklist-reutilizacion]]
