---
name: patron-mapper-mapstruct
description: MapStruct en cada frontera de capa (REST<->Domain<->Entity), con @AfterMapping para relaciones bidireccionales JPA
tags: [patron, backend, mappers, "reusable:alta"]
source: Backend/sigma-bb/src/main/java/.../client_hexagon/infrastructure/adapters/output/persistence/mapper/
updated: 2026-08-29
---

# Patrón: mapper por frontera con MapStruct

Todo el backend usa **MapStruct** (`@Mapper(componentModel = "spring")`) con al menos 3 capas de mapeo por entidad: `RestMapper` (Request→Domain, Domain→Response), `PersistenceMapper` (Domain↔Entity). Nunca se mapea a mano dentro de un service (excepción notable: `reports_hexagon` mapea `ReportResponseDTO` campo a campo manualmente, ver [[dominio-reportes]]).

## Composición de mappers y relaciones bidireccionales

`ClientPersistenceMapper` usa `uses = {...}` para componer mappers hijos (Email, Phone, Headquarter) en vez de un mapper monolítico. Para relaciones bidireccionales JPA (padre↔hijos que se referencian mutuamente), usa `@AfterMapping` con `@MappingTarget` para reconstruir los enlaces inversos (`linkChildren`) después del mapeo automático — evita mapeo manual de FKs inversas y el problema clásico de referencias circulares en el mapeo automático.

## Reutilizable en MalphasOS

`reusable:alta`, sin cambios. Adoptar como convención desde el día uno: un mapper por frontera, composición vía `uses`, `@AfterMapping` para relaciones bidireccionales. Consistente con MapStruct + Lombok en el `pom.xml` (ver [[stack-tecnologico]] sobre el orden correcto de annotation processors).

## Dónde deja de servir: los agregados de Generación 2

Descubierto el 2026-08-29 al migrar [[dominio-ubicacion]]. MapStruct construye el objeto destino **por setters o por builder**. Un agregado de Generación 2 no ofrece ninguno de los dos a propósito: se entra por su factoría `create`, que registra un evento, o por `rehydrate`, que reconstruye desde lo persistido sin emitir nada. Generar el mapeo automáticamente exigiría abrir justo la puerta que el agregado cierra.

De modo que la regla completa es:

| Dirección | Herramienta | Por qué |
|---|---|---|
| DTO → agregado, fila → agregado | **a mano** | El destino protege su construcción |
| agregado → DTO de respuesta | **MapStruct** | El destino es un `record` sin reglas |
| DTO ↔ modelo anémico (Generación 1) | **MapStruct** | Es el caso para el que se pensó |

No es una limitación de MapStruct sino la consecuencia buscada de que un agregado no se deje construir de cualquier manera. Aplica a `client` y `equipment`, que se reconstruyen con el mismo patrón.

## Notas relacionadas

[[arquitectura-hexagonal]] · [[dominio-cliente]] · [[stack-tecnologico]] · [[migracion-location-hallazgos]]
