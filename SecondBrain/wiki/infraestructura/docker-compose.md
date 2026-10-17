---
name: docker-compose
description: 5 servicios orquestados — Postgres con initdb ordenado por prefijo, Keycloak compartiendo la instancia de Postgres, RabbitMQ, pgAdmin, backend
tags: [infraestructura, docker, "reusable:alta", "describe:ambos"]
source: docker-compose.yaml
updated: 2026-10-17
---

# `docker-compose.yaml` — orquestación

5 servicios en red bridge `sigma-bb-network`:

- **`postgres17`** (`postgres:17-alpine`) — monta `./DataBase/v4/initdb` en `/docker-entrypoint-initdb.d`; de ahí el prefijo `A_/B_/C_` en los scripts (controla orden alfabético de ejecución, ver [[evolucion-esquema-v1-v4]]). Healthcheck con `pg_isready`.
- **`pgadmin`** (`dpage/pgadmin4`) — solo dev, credenciales hardcodeadas directamente en el compose (no vía `.env` — nota de higiene a corregir en MalphasOS).
- **`rabbitmq`** (`rabbitmq:4.1.5-management-alpine`) — broker de eventos, puertos AMQP + management UI parametrizados por env vars.
- **`keycloak`** (`quay.io/keycloak/keycloak:26.6.1`, modo `start-dev`) — **usa la misma instancia de Postgres** que el backend (DB separada `keycloak`, creada por el script `B_KeyCloak_Create.sql`), monta `themes/`, `providers/`, `imports/`. Depende de `postgres17` healthy. ⚠️ **El import del realm está roto** (ver abajo).
- **`bolivarbioingenieria-app`** — build local desde `Backend/sigma-bb/Dockerfile`, depende de postgres (healthy) y rabbitmq (started); credenciales inyectadas por variables de entorno desde `.env` raíz.

Variables de entorno referenciadas (solo nombres, sin valores): `NETWORK_NAME`, `POSTGRES_ROOT_USER`, `POSTGRES_ROOT_PASSWORD`, `POSTGRES_DB`, `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_HOST_PORT`, `RABBITMQ_DEFAULT_USER`, `RABBITMQ_DEFAULT_PASS`, `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_HOST_PORT`, `RABBITMQ_MGMT_PORT`, `RABBITMQ_MGMT_HOST_PORT`, `SIGMA_PORT`, `SIGMA_HOST_PORT`, `KC_ADMIN_USER`, `KC_ADMIN_PASSWORD`, `SIGMA_BACKEND_ADMIN_SECRET`.

## ⚠️ Bug: el import del realm de Keycloak nunca se ejecutó

```yaml
KEYCLOAK_IMPORT: /opt/keycloak/imports/real-export.json - Dkeycloak.profile.feature.upload_scripts=enable
```

Tres problemas en una sola línea:

1. `KEYCLOAK_IMPORT` es una variable de la **era WildFly** de Keycloak. Desde la migración a Quarkus (Keycloak 17+), **se ignora por completo**. En Keycloak 26 la forma correcta es el flag `--import-realm` en el `command`, con los archivos montados en `/opt/keycloak/data/import/`.
2. El archivo se llama `realm-export.json`, no `real-export.json`.
3. El sufijo `- Dkeycloak.profile.feature.upload_scripts=enable` quedó concatenado dentro del valor de la variable, en vez de ser un argumento de JVM.

Consecuencia: el realm `sigma-bb-realm` del proyecto original **muy probablemente se importó a mano** desde la consola de administración, no automáticamente. Al reproducir el entorno desde cero, no asumir que el realm aparece solo.

## MalphasOS: desarrollo y producción (2026-10-17)

Lo de arriba describe el compose **del original**. El de MalphasOS se separó en dos con la versión 1.0.0:

- **`docker-compose.yaml`, el de desarrollo**: Postgres, Keycloak en `start-dev` con el realm de
  desarrollo —con `dev.admin`—, el backend, el frontend, Gotenberg y pgAdmin con el perfil `tools`.
  Publica los puertos de todo para poder trabajar.
- **`docker-compose.prod.yaml`, el de producción**, aparte y sin heredar nada: **solo Caddy publica
  puertos**, el 80 y el 443, con HTTPS automático, y todo vive en un dominio —el frontend en `/`, el API en
  `/v1` y Keycloak en `/auth`, con su consola de administración bloqueada—. Keycloak corre en `start`, con
  un realm derivado del de desarrollo **sin usuarios ni secretos escritos**; el backend, con la seguridad
  encendida y sin Swagger; y un servicio `respaldo` saca un `pg_dump` diario de las dos bases. Cómo se
  instala, en `DESPLIEGUE.md` de la raíz.

**RabbitMQ se retiró el 2026-10-17** de los dos, del backend y del `.env.example`: ninguna clase lo usaba
—el adaptador del original nunca se migró, ver [[eventos-de-dominio]]— y solo aportaba un contenedor, un
puerto publicado y una comprobación de salud que en pruebas había que apagar. Lo que esta nota cuenta de
él sigue siendo cierto **del original**.

**Y la primera instalación desde cero destapó un defecto del realm versionado**: un rol escrito a mano el
2026-09-13 tenía un identificador de 38 caracteres, y la columna de Keycloak admite 36. En desarrollo
nunca falló, porque el realm ya existía y `--import-realm` lo saltaba; **una instalación nueva no habría
arrancado**. Se corrigió, y el guion que deriva el realm de producción comprueba desde entonces que todo
identificador sea un UUID.

## Reutilizable en MalphasOS

`reusable:alta` — el patrón completo es una plantilla directa: Postgres con `initdb.d` ordenado por prefijo alfabético, Keycloak compartiendo la misma instancia de Postgres con DB separada, RabbitMQ para eventos, healthchecks con `depends_on: condition: service_healthy`. Al portar, mover las credenciales de `pgadmin` a variables de entorno también (higiene, no arquitectura).

**Ya implementado en MalphasOS**: el `docker-compose.yaml` de MalphasOS declara `name: malphasos` para agrupar todo como un proyecto de Compose independiente del nombre del directorio, incluye el servicio `app` construido desde [[dockerfile-y-contenedores]] con healthcheck vía Actuator, corrige el import del realm, añade healthcheck a los tres servicios, parametriza pgAdmin bajo un perfil `tools`, usa `KC_HOSTNAME=localhost` para no tocar `/etc/hosts`, y **no monta el esquema de la aplicación como `initdb`** — eso lo gestiona Flyway. El único script de init crea la base de datos de Keycloak. Ver [[decisiones-tecnicas-malphasos]].

## Notas relacionadas

[[esquema-bd-v4]] · [[keycloak-configuracion]] · [[stack-tecnologico]]
