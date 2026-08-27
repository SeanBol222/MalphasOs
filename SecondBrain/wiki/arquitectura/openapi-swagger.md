---
name: openapi-swagger
description: Configuración de OpenAPI con un GroupedOpenApi por dominio, separando Swagger UI en pestañas por módulo
tags: [arquitectura, backend, documentacion, "reusable:alta"]
source: Backend/sigma-bb/src/main/java/.../bootstrap/config/open_api/OpenApiConfig.java
updated: 2026-08-27
---

# OpenAPI / Swagger

`OpenApiConfig` define un bean `OpenAPI` con metadata (title/version/description/contact/license) más un `SecurityScheme` bearer-JWT global.

Además, define **un `GroupedOpenApi` por dominio** (`person`, `location`, `equipment`, `client`, `headquarter`, `service-area`, `client-equipment`), cada uno filtrando por `pathsToMatch`. Esto separa la Swagger UI en pestañas por módulo en vez de una sola lista plana de endpoints.

## Reutilizable en MalphasOS

`reusable:alta`, patrón limpio y directamente portable: solo hay que declarar un `GroupedOpenApi` nuevo por cada hexágono que se cree en MalphasOS. Los grupos concretos (`person`, `equipment`, etc.) son específicos de este dominio, pero el patrón (agrupar por bounded context) sí aplica igual.

## Notas relacionadas

[[arquitectura-hexagonal]] · [[seguridad-keycloak-backend]]
