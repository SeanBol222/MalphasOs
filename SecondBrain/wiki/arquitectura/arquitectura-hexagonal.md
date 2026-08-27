---
name: arquitectura-hexagonal
description: Patrón ports & adapters aplicado en el backend, capa por capa con ejemplo de flujo real
tags: [arquitectura, backend, "reusable:alta"]
source: Backend/sigma-bb/src/main/java/com/bolivar/bioingenieria/app/sigma_bb/
updated: 2026-08-27
---

# Arquitectura hexagonal (ports & adapters)

Todo el backend está organizado en **hexágonos** (bounded contexts), cada uno con la misma estructura interna de 3 capas:

```
<hexagono>/
  application/
    ports/input/    -> interfaces que expone la capa de aplicación (ServicePort)
    ports/output/    -> interfaces que la aplicación necesita del exterior (PersistencePort)
    service/ o services/  -> implementación de los ports de entrada
    mapper/ (a veces)
  domain/
    model/           -> entidades de dominio puras (sin anotaciones de framework)
    exception/        -> excepciones de negocio
  infrastructure/
    adapters/input/rest/   -> controllers REST + mappers REST DTO <-> dominio
    adapters/output/persistence/  -> adapters JPA + entities + mappers + repositories
```

## Flujo real de una request (ejemplo: crear un Client)

```
ClientRestAdapter.createClient()
  -> ClientRestMapper.toClient(request)        [REST DTO -> Domain]
  -> ClientServicePort.save(Client)             [puerto de entrada]
    -> ClientService.save()                     [lógica de aplicación]
      -> ClientPersistencePort.save(Client)      [puerto de salida]
        -> ClientPersistenceAdapter.save()
          -> ClientPersistenceMapper.toClientEntity()  [Domain -> JPA Entity]
          -> ClientRepository.save(entity)              [Spring Data JPA]
          -> ClientPersistenceMapper.toClient(entity)    [Entity -> Domain, de vuelta]
  -> ClientRestMapper.toClientResponse(Client)   [Domain -> REST DTO]
```

Cada frontera (REST↔dominio, dominio↔persistencia) tiene su propio mapper — ver [[patron-mapper-mapstruct]]. El dominio nunca conoce anotaciones de Spring/JPA/REST.

## Dos generaciones del patrón dentro del mismo repo

No todos los hexágonos implementan esto igual — hay una división clara entre un patrón CRUD más simple/antiguo y uno más rico ya adoptado parcialmente. Ver [[evolucion-arquitectonica-crud-a-cqrs]] para el detalle, y [[patron-cqrs-commands]] para el patrón nuevo específicamente.

## Reutilizable en MalphasOS

`reusable:alta` como esqueleto de carpetas y separación de responsabilidades — es un patrón limpio y bien aplicado en términos generales. La recomendación concreta para MalphasOS es partir directamente de la variante evolucionada (agregados ricos + commands + eventos, ver [[dominio-equipo-mantenimiento]] como referencia), no de la variante CRUD anémica.

## Notas relacionadas

[[evolucion-arquitectonica-crud-a-cqrs]] · [[patron-cqrs-commands]] · [[patron-mapper-mapstruct]] · [[manejo-global-excepciones]] · [[dominio-cliente]] · [[dominio-equipo-mantenimiento]]
