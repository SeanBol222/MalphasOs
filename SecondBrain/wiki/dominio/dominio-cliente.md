---
name: dominio-cliente
description: client_hexagon — Client, Headquarter, ServiceArea, Manager. Patrón CRUD anémico (Generación 1)
tags: [dominio, backend, gestion-clientes, "reusable:media"]
source: Backend/sigma-bb/src/main/java/.../client_hexagon/
estado: incompleto
updated: 2026-08-27
---

# Dominio Cliente (`client_hexagon`)

## Modelo de dominio y relaciones

```
Client (1) -> Headquarter (N, sede) -> Manager (N, encargado)
                                     -> ServiceArea (N, área de servicio dentro de la sede) -> Manager (N)
Client -> EmailClient (N), PhoneClient (N)   [soft-delete vía estadoActivo]
```

`Manager` es **anémico y desacoplado**: solo `identificadorEncargado (UUID)`, `tipoEncargado (String: "sede"|"area_servicio")`, `estadoActivo`. **No referencia `Person` en el modelo de dominio** — ver la ambigüedad completa en [[relacion-cliente-persona-ambiguedad]].

## Casos de uso REST expuestos

`ClientRestAdapter` (`/client/v1/api`, CRUD completo) + adapters hermanos `EmailClientRestAdapter`, `PhoneClientRestAdapter`, `HeadquarterRestAdapter`, `ManagerHeadquarterRestAdapter`, `ServiceAreaRestAdapter`, `ManagerServiceAreaRestAdapter`, `ClientEquipmentRestAdapter` — mismo patrón CRUD por sub-recurso. Todos protegidos con `@PreAuthorize("hasAuthority('admin.full')")` a nivel de método.

## Patrón arquitectónico: Generación 1 (CRUD anémico)

Ver [[evolucion-arquitectonica-crud-a-cqrs]] para el contraste completo. Aquí el service recibe el modelo de dominio y lo muta in-place, sin Commands ni eventos de dominio — es el patrón más antiguo del repo, ya superado en [[dominio-equipo-mantenimiento]].

## Excepciones de dominio

`ClientNotFoundException`, `HeadquarterFoundException`, `ServiceAreaFoundException`, `ClientEquipmentFoundException`, manejadas por `ClientGlobalControllerAdvice` con `ClientErrorCatalog`/`ClientErrorResponse`. Ver detalle e inconsistencias en [[manejo-global-excepciones]].

## Mappers

MapStruct en cada frontera. `ClientPersistenceMapper` compone mappers hijos (`uses = {...}` para Email, Phone, Headquarter) y usa `@AfterMapping` + `@MappingTarget` para reconstruir relaciones bidireccionales JPA (`linkChildren`) — patrón reutilizable para evitar mapeo manual de FKs inversas. Ver [[patron-mapper-mapstruct]].

## Reutilizable en MalphasOS

`reusable:media`. La **jerarquía conceptual** Client → Headquarter → ServiceArea → Manager es directamente relevante para MalphasOS (gestión de clientes es uno de sus dos pilares) y vale la pena conservarla como modelo de dominio. Pero **no copiar la implementación tal cual**: hay que reconstruirla siguiendo el patrón de Generación 2 (agregados ricos + commands + eventos, como en [[dominio-equipo-mantenimiento]]), y resolver explícitamente la relación Manager↔Person antes de escribir código (ver [[relacion-cliente-persona-ambiguedad]]). El patrón de mapper con `@AfterMapping` para relaciones bidireccionales sí es `reusable:alta` tal cual.

## Notas relacionadas

[[relacion-cliente-persona-ambiguedad]] · [[dominio-persona-identidad]] · [[evolucion-arquitectonica-crud-a-cqrs]] · [[manejo-global-excepciones]] · [[esquema-bd-v4]] · [[alcance-malphasos]]
