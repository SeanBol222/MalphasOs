---
name: openapi-swagger
description: Configuración de OpenAPI con un GroupedOpenApi por dominio, separando Swagger UI en pestañas por módulo
tags: [arquitectura, backend, documentacion, "reusable:alta"]
source: Backend/sigma-bb/src/main/java/.../bootstrap/config/open_api/OpenApiConfig.java
updated: 2026-09-02
---

# OpenAPI / Swagger

`OpenApiConfig` define un bean `OpenAPI` con metadata (title/version/description/contact/license) más un `SecurityScheme` bearer-JWT global.

Además, define **un `GroupedOpenApi` por dominio** (`person`, `location`, `equipment`, `client`, `headquarter`, `service-area`, `client-equipment`), cada uno filtrando por `pathsToMatch`. Esto separa la Swagger UI en pestañas por módulo en vez de una sola lista plana de endpoints.

## Reutilizable en MalphasOS

`reusable:alta`, patrón limpio y directamente portable: solo hay que declarar un `GroupedOpenApi` nuevo por cada hexágono que se cree en MalphasOS. Los grupos concretos (`person`, `equipment`, etc.) son específicos de este dominio, pero el patrón (agrupar por bounded context) sí aplica igual.

## El fallo silencioso de los grupos (hallado el 2026-09-02)

**Un patrón de `pathsToMatch` que no casa con ninguna ruta real no falla ni avisa: deja el recurso fuera de la documentación.** No hay error de arranque, no hay log, y Swagger UI muestra un grupo que simplemente no incluye lo que debería.

En MalphasOS ocurrió cuatro veces, porque `OpenApiConfig` se escribió al portar la configuración transversal, antes que los controladores que iba a documentar:

| Patrón declarado | Ruta real | Consecuencia |
|---|---|---|
| `/v1/api/equipment/**` | `/v1/api/equipments` | fuera de Swagger |
| `/v1/api/equipment-models/**` | `/v1/api/models` | fuera de Swagger |
| `/v1/api/client-equipment/**` | `/v1/api/client-equipments` | fuera de Swagger |
| *(ninguno)* | `/v1/api/managers` | fuera de Swagger |

**Las cuatro quedaron corregidas el 2026-09-02**, cada módulo con una prueba que consulta su propio documento generado y exige que aparezcan todos sus recursos. La de `managers` apareció justamente al buscar más instancias de las tres primeras, que es la única forma en que este fallo se encuentra: nunca se manifiesta solo.

**La lección de método**: escribir el grupo *antes* que el controlador invierte el orden de la verificación. Si el grupo se declara primero, la única forma de saber que casa es una prueba que consulte el documento generado; leer las dos listas en paralelo es exactamente lo que falló cuatro veces.

## Notas relacionadas

[[arquitectura-hexagonal]] · [[seguridad-keycloak-backend]]
