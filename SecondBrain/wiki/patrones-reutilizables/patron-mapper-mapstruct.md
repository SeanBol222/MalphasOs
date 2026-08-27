---
name: patron-mapper-mapstruct
description: MapStruct en cada frontera de capa (REST<->Domain<->Entity), con @AfterMapping para relaciones bidireccionales JPA
tags: [patron, backend, mappers, "reusable:alta"]
source: Backend/sigma-bb/src/main/java/.../client_hexagon/infrastructure/adapters/output/persistence/mapper/
updated: 2026-08-27
---

# Patrón: mapper por frontera con MapStruct

Todo el backend usa **MapStruct** (`@Mapper(componentModel = "spring")`) con al menos 3 capas de mapeo por entidad: `RestMapper` (Request→Domain, Domain→Response), `PersistenceMapper` (Domain↔Entity). Nunca se mapea a mano dentro de un service (excepción notable: `reports_hexagon` mapea `ReportResponseDTO` campo a campo manualmente, ver [[dominio-reportes]]).

## Composición de mappers y relaciones bidireccionales

`ClientPersistenceMapper` usa `uses = {...}` para componer mappers hijos (Email, Phone, Headquarter) en vez de un mapper monolítico. Para relaciones bidireccionales JPA (padre↔hijos que se referencian mutuamente), usa `@AfterMapping` con `@MappingTarget` para reconstruir los enlaces inversos (`linkChildren`) después del mapeo automático — evita mapeo manual de FKs inversas y el problema clásico de referencias circulares en el mapeo automático.

## Reutilizable en MalphasOS

`reusable:alta`, sin cambios. Adoptar como convención desde el día uno: un mapper por frontera, composición vía `uses`, `@AfterMapping` para relaciones bidireccionales. Consistente con MapStruct + Lombok en el `pom.xml` (ver [[stack-tecnologico]] sobre el orden correcto de annotation processors).

## Notas relacionadas

[[arquitectura-hexagonal]] · [[dominio-cliente]] · [[stack-tecnologico]]
