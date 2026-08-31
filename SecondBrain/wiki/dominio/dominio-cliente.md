---
name: dominio-cliente
description: client_hexagon — Client, Headquarter, ServiceArea, Manager. Patrón CRUD anémico (Generación 1)
tags: [dominio, backend, gestion-clientes, "reusable:media"]
source: Backend/sigma-bb/src/main/java/.../client_hexagon/
estado: incompleto
updated: 2026-08-29
---

# Dominio Cliente (`client_hexagon`)

## Modelo de dominio y relaciones

```
Client (1) -> Headquarter (N, sede) -> Manager (N, encargado)
                                     -> ServiceArea (N, área de servicio dentro de la sede) -> Manager (N)
Client -> EmailClient (N), PhoneClient (N)   [soft-delete vía estadoActivo]
```

`Manager` es **anémico**: solo `identificadorEncargado (UUID)`, `tipoEncargado (String)`, `estadoActivo`. No referencia `Person` en el modelo de dominio, pero **sí es una persona**: su PK es a la vez FK a `persona`, y el servicio la crea antes de crear el encargado. La relación está implementada en todas las capas menos en el dominio — ver [[relacion-manager-persona]]. Ojo con el javadoc de `tipoEncargado`, que documenta `"sede"|"area_servicio"`, dos valores que la restricción `CHK_tipo_encagado` rechaza: solo acepta `HEADQUARTER` y `SERVICE_AREA`.

## Una tabla sin código: `representante_legal`

El esquema define `representante_legal` (persona ↔ cliente, por identidad compartida) y **el hexágono no la usa en ningún sitio**: no hay entidad, ni modelo, ni puerto, ni controlador. Es la relación que le falta a `CEO_CLIENT` para asociarse a su cliente. Ver [[relacion-manager-persona]].

## Casos de uso REST expuestos

`ClientRestAdapter` (`/client/v1/api`, CRUD completo) + adapters hermanos `EmailClientRestAdapter`, `PhoneClientRestAdapter`, `HeadquarterRestAdapter`, `ManagerHeadquarterRestAdapter`, `ServiceAreaRestAdapter`, `ManagerServiceAreaRestAdapter`, `ClientEquipmentRestAdapter` — mismo patrón CRUD por sub-recurso. Todos protegidos con `@PreAuthorize("hasAuthority('admin.full')")` a nivel de método.

## Patrón arquitectónico: Generación 1 (CRUD anémico)

Ver [[evolucion-arquitectonica-crud-a-cqrs]] para el contraste completo. Aquí el service recibe el modelo de dominio y lo muta in-place, sin Commands ni eventos de dominio — es el patrón más antiguo del repo, ya superado en [[dominio-equipo-mantenimiento]].

## Excepciones de dominio

`ClientNotFoundException`, `HeadquarterFoundException`, `ServiceAreaFoundException`, `ClientEquipmentFoundException`, manejadas por `ClientGlobalControllerAdvice` con `ClientErrorCatalog`/`ClientErrorResponse`. Ver detalle e inconsistencias en [[manejo-global-excepciones]].

## Mappers

MapStruct en cada frontera. `ClientPersistenceMapper` compone mappers hijos (`uses = {...}` para Email, Phone, Headquarter) y usa `@AfterMapping` + `@MappingTarget` para reconstruir relaciones bidireccionales JPA (`linkChildren`) — patrón reutilizable para evitar mapeo manual de FKs inversas. Ver [[patron-mapper-mapstruct]].

## Reutilizable en MalphasOS

`reusable:media`. La **jerarquía conceptual** Client → Headquarter → ServiceArea → Manager es directamente relevante para MalphasOS (gestión de clientes es uno de sus dos pilares) y vale la pena conservarla como modelo de dominio. Pero **no copiar la implementación tal cual**: hay que reconstruirla siguiendo el patrón de Generación 2 (agregados ricos + commands + eventos, como en [[dominio-equipo-mantenimiento]]), y hacer explícita en el modelo la relación Manager↔Person que hoy solo existe en el esquema y en un método privado (ver [[relacion-manager-persona]]). El patrón de mapper con `@AfterMapping` para relaciones bidireccionales sí es `reusable:alta` tal cual.

## Notas relacionadas

[[relacion-manager-persona]] · [[dominio-persona-identidad]] · [[evolucion-arquitectonica-crud-a-cqrs]] · [[manejo-global-excepciones]] · [[esquema-bd-v4]] · [[alcance-malphasos]]
