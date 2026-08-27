---
name: stack-tecnologico
description: Stack tecnológico completo del backend, frontend, BD e infraestructura
tags: [overview, stack, "reusable:alta"]
source: Backend/sigma-bb/pom.xml, Frontend/package.json, docker-compose.yaml
updated: 2026-08-27
---

# Stack tecnológico

## Backend (`Backend/sigma-bb/pom.xml`)

- **Spring Boot 4.0.6**, **Java 21**.
- Spring Data JPA + PostgreSQL driver (runtime).
- Spring Validation, `spring-boot-starter-webmvc`.
- **Lombok** 1.18.30 + **MapStruct** 1.5.5.Final (con `mapstruct-processor` y `lombok-mapstruct-binding` 0.2.0 encadenados en el compiler plugin — el orden de los annotation processors importa para que Lombok y MapStruct coexistan).
- OAuth2 resource server + oauth2-client (Spring Security).
- `spring-boot-starter-amqp` (RabbitMQ).
- **Jackson mixto**: `tools.jackson.core:jackson-databind:3.1.3` (Jackson 3, groupId nuevo) junto a módulos clásicos de Jackson 2 (`com.fasterxml.jackson.datatype:*`). Posible transición en curso — **verificar compatibilidad real antes de copiar esta combinación tal cual** en MalphasOS.
- `springdoc-openapi-starter-webmvc-ui` 3.0.2.
- `keycloak-admin-client` 26.0.9.
- Testing: `spring-boot-starter-test` (JUnit/Mockito incluidos por el starter, no se detectaron librerías de test adicionales).

## Frontend (`Frontend/package.json`)

- **React 19.2 + TypeScript**, bootstrapeado con **Vite 8**.
- `react-router-dom` v7.
- `keycloak-js` v26 (SDK oficial, adapter directo).
- **Sin librería de estado global** (solo Context API para auth), **sin UI kit** detectado, cliente HTTP con `fetch` nativo envuelto en un helper propio. Proyecto en etapa de bootstrap — no asumir que esto es una decisión definitiva de arquitectura, es simplemente lo que existe hoy.
- ESLint 10 + `eslint-plugin-react-hooks` + `eslint-plugin-react-refresh`.

## Base de datos e infraestructura

- **PostgreSQL 17** (`postgres:17-alpine`), esquema en `DataBase/v4/initdb/`. Ver [[esquema-bd-v4]].
- **Keycloak 26.6.1** (`quay.io/keycloak/keycloak`), modo `start-dev`, comparte la instancia de Postgres con una DB separada.
- **RabbitMQ 4.1.5** (`rabbitmq:4.1.5-management-alpine`).
- **pgAdmin** solo para desarrollo.
- Orquestado con `docker-compose.yaml`. Ver [[docker-compose]].

## Reutilizable en MalphasOS

`reusable:alta` para casi todo el stack backend (Spring Boot 4 + Java 21 + MapStruct + Lombok + OAuth2 resource server + AMQP es una base sólida y moderna), con la única advertencia de validar la mezcla Jackson 2/3 antes de copiarla. El starter kit de frontend (Vite + React + TS + keycloak-js) también es `reusable:alta` como punto de partida, entendiendo que hoy es un esqueleto mínimo, no un sistema de diseño maduro.

## Notas relacionadas

[[sistema-bolivarbioingenieria]] · [[arquitectura-frontend]] · [[docker-compose]]
