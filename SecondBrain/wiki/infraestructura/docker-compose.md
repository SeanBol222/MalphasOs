---
name: docker-compose
description: 5 servicios orquestados — Postgres con initdb ordenado por prefijo, Keycloak compartiendo la instancia de Postgres, RabbitMQ, pgAdmin, backend
tags: [infraestructura, docker, "reusable:alta"]
source: docker-compose.yaml
updated: 2026-08-27
---

# `docker-compose.yaml` — orquestación

5 servicios en red bridge `sigma-bb-network`:

- **`postgres17`** (`postgres:17-alpine`) — monta `./DataBase/v4/initdb` en `/docker-entrypoint-initdb.d`; de ahí el prefijo `A_/B_/C_` en los scripts (controla orden alfabético de ejecución, ver [[evolucion-esquema-v1-v4]]). Healthcheck con `pg_isready`.
- **`pgadmin`** (`dpage/pgadmin4`) — solo dev, credenciales hardcodeadas directamente en el compose (no vía `.env` — nota de higiene a corregir en MalphasOS).
- **`rabbitmq`** (`rabbitmq:4.1.5-management-alpine`) — broker de eventos, puertos AMQP + management UI parametrizados por env vars.
- **`keycloak`** (`quay.io/keycloak/keycloak:26.6.1`, modo `start-dev`) — **usa la misma instancia de Postgres** que el backend (DB separada `keycloak`, creada por el script `B_KeyCloak_Create.sql`), importa el realm automáticamente vía `KEYCLOAK_IMPORT`, monta `themes/`, `providers/`, `imports/`. Depende de `postgres17` healthy.
- **`bolivarbioingenieria-app`** — build local desde `Backend/sigma-bb/Dockerfile`, depende de postgres (healthy) y rabbitmq (started); credenciales inyectadas por variables de entorno desde `.env` raíz.

Variables de entorno referenciadas (solo nombres, sin valores): `NETWORK_NAME`, `POSTGRES_ROOT_USER`, `POSTGRES_ROOT_PASSWORD`, `POSTGRES_DB`, `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_HOST_PORT`, `RABBITMQ_DEFAULT_USER`, `RABBITMQ_DEFAULT_PASS`, `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_HOST_PORT`, `RABBITMQ_MGMT_PORT`, `RABBITMQ_MGMT_HOST_PORT`, `SIGMA_PORT`, `SIGMA_HOST_PORT`, `KC_ADMIN_USER`, `KC_ADMIN_PASSWORD`, `SIGMA_BACKEND_ADMIN_SECRET`.

## Reutilizable en MalphasOS

`reusable:alta` — el patrón completo es una plantilla directa: Postgres con `initdb.d` ordenado por prefijo alfabético, Keycloak compartiendo la misma instancia de Postgres con DB separada, RabbitMQ para eventos, healthchecks con `depends_on: condition: service_healthy`. Al portar, mover las credenciales de `pgadmin` a variables de entorno también (higiene, no arquitectura).

## Notas relacionadas

[[esquema-bd-v4]] · [[keycloak-configuracion]] · [[stack-tecnologico]]
