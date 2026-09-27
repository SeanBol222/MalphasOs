---
name: dominio-ubicacion
description: location_hexagon — Country/City, referencia de la Generación 2 (agregados ricos + eventos) aplicada a un dominio simple
tags: [dominio, backend, ubicacion, "reusable:alta", "describe:ambos"]
source: Backend/sigma-bb/src/main/java/.../location_hexagon/
updated: 2026-09-26
---

# Dominio Ubicación (`location_hexagon`)

## Modelo de dominio

`Country` (1) → `City` (N). Implementado como **agregado rico** ejemplar: `City extends AggregateRoot` ([[aggregate-root-pattern]]), con factoría estática `City.create(name, countryId)` y métodos de negocio (`updateCity`, `updateCityPatch`, `deleteCity`) que registran `DomainEvent`s internamente (`CityCreatedEvent`, `CityUpdatedEvent`, `CityDeletedEvent`).

## Por qué esta nota importa más de lo que su tamaño sugiere

`location_hexagon` es el dominio más simple del sistema (solo dos entidades), pero es junto con `equipment_hexagon` la referencia de **forma** de la Generación 2 (ver [[evolucion-arquitectonica-crud-a-cqrs]]): agregados + eventos + `EventDispatcherPort` con `@Qualifier`. Precisamente por ser simple, es la mejor plantilla para ver la estructura del patrón sin el ruido de un dominio complejo.

> **Matizado el 2026-08-29, al migrarlo.** Esta nota lo llamaba *"el mejor ejemplo pedagógico de cómo se ve el patrón completo"*. La estructura sí lo es; el código no. Los agregados llevaban `@Data`, que les daba un setter público por campo y permitía cambiarlos sin emitir nada; tenían la igualdad rota por `callSuper = true`; uno de sus métodos escribía en la metadata un tipo de evento distinto del que decía la clase que construía; y su método de borrado no modificaba estado alguno. **Estudiar la forma, no copiar la implementación.** El detalle completo en [[migracion-location-hallazgos]].

## Casos de uso REST

`RestControllerCity`, `RestControllerCountry` — incluyen `PATCH` (parcial) además del CRUD completo.

## Excepciones de dominio

`CityNotFoundException`, `CountryNotFoundException` — **viven en `infrastructure/output/errors`, no en `domain/exception`** como en `client_hexagon`/`person_hexagon` (inconsistencia de ubicación entre hexágonos, señalada también en [[manejo-global-excepciones]]). Manejadas por `LocationGlobalHandlerError`.

## Los datos de referencia, y lo que sembrarlos destapó (2026-09-26)

**`V7__seed_location_reference_data.sql` siembra 249 países y 1.350 ciudades**: los países de la ISO 3166-1 con su nombre en español, los **1.103 municipios de Colombia** y la capital de cada uno de los demás países.

**Por qué es una migración y no un script suelto.** Sin países no se puede registrar un fabricante y sin ciudades no se puede abrir una sede; **no hay pantalla que los cree** —el catálogo de ubicaciones se consulta, no se administra— así que una instalación nueva nacía inservible. Como migración, Flyway la aplica al arrancar, una vez y en cualquier base de datos. Un script en `docker/postgres/init` solo corre **al crear el volumen**: no habría tocado la base de datos que ya existía.

### Lo que los datos reales destaparon: el esquema le falta un nivel

El comentario de `V3__location.sql` dice que «el nombre de una ciudad solo es único dentro de su país: hay un Córdoba en España y otro en Argentina». **Es cierto entre países y falso dentro de Colombia.**

| | |
|---|---|
| Municipios de Colombia | **1.104** en el dataset, 1.103 tras quitar un duplicado de la fuente |
| Nombres compartidos entre departamentos | **64** |
| El peor caso | **La Unión, Villanueva y Buenavista, cuatro veces cada uno** |

`UQ_ciudad_nombre_por_pais` hace imposible guardarlos tal cual. La salida aplicada es **meter el departamento en el nombre** —`La Unión (Nariño)`— porque es lo único que cabe sin cambiar el esquema. Lo correcto sería un **nivel de división administrativa**: `departamento`/`estado` entre país y ciudad, con la unicidad puesta ahí. Exige migración y toca el frontend, así que queda anotado en [[deuda-tecnica-y-riesgos]].

**Lo que vale de este hallazgo**: la restricción se escribió razonando sobre dos países y se contrastó con datos reales dos meses después. Ninguna prueba lo habría encontrado, porque todas creaban una ciudad cada una.

### La prueba lee el archivo, no la base de datos

`LocationSeedDataTest` verifica el contenido de la migración contra el `.sql`, y no contando filas. La razón es concreta: **`LocationSchemaTest` hace `DELETE FROM ciudad` y `DELETE FROM pais` después de cada método**, así que al terminar esa clase el contenedor de pruebas se queda sin datos de referencia. Una prueba que contara filas pasaría o fallaría **según el orden de ejecución de las clases**, que es la peor clase de prueba. Verificado por mutación: quitar la traducción al español y repetir un municipio ponen rojas las suyas.

## Reutilizable en MalphasOS

`reusable:alta` **para la estructura**, no para el código. Sigue siendo más útil estudiar `location_hexagon` que `client_hexagon`, porque representa el patrón que sí se quiere replicar. Corregir al portar: los cuatro defectos de [[migracion-location-hallazgos]], y ubicar las excepciones de dominio en `domain/exception` de forma consistente.

**Migrado por completo el 2026-08-29** (esquema, dominio, aplicación, persistencia y REST). `Country` y `City` son los primeros agregados de Generación 2 de MalphasOS, con identidad por UUID, igualdad por identidad, sin setters, y con `rename` / `relocateTo` / `deactivate` en lugar de `updateX` / `updateXPatch` / `deleteX`.

## Notas relacionadas

[[aggregate-root-pattern]] · [[migracion-location-hallazgos]] · [[evolucion-arquitectonica-crud-a-cqrs]] · [[dominio-equipo-mantenimiento]] · [[manejo-global-excepciones]] · [[esquema-bd-v4]]
