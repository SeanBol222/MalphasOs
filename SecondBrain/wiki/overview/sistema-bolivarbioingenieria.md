---
name: sistema-bolivarbioingenieria
description: Visión general de bolivarbioingenieria-app — qué es, sus módulos y cómo encajan
tags: [overview, "reusable:alta"]
updated: 2026-08-27
---

# bolivarbioingenieria-app — visión general

Aplicación de **gestión de clientes** y **gestión de mantenimientos preventivos de equipos biomédicos/industriales** para Bolívar Bioingeniería. Backend Spring Boot con **arquitectura hexagonal** (ports & adapters) dividida en bounded contexts ("hexágonos"), frontend React, PostgreSQL, Keycloak para identidad/autorización, RabbitMQ para eventos de dominio.

## Componentes

- **Backend** (`Backend/sigma-bb/`) — Spring Boot 4, Java 21. Ver [[stack-tecnologico]].
- **Frontend** (`Frontend/`) — React 19 + TypeScript + Vite, en etapa muy temprana. Ver [[arquitectura-frontend]].
- **Base de datos** (`DataBase/v4/initdb/`) — PostgreSQL, 27 tablas. Ver [[esquema-bd-v4]].
- **Identidad** — Keycloak, realm `sigma-bb-realm`. Ver [[seguridad-keycloak-backend]] y [[integracion-keycloak-frontend]].
- **Mensajería** — RabbitMQ para despacho distribuido de eventos de dominio. Ver [[eventos-de-dominio]].
- **Orquestación** — `docker-compose.yaml` en la raíz. Ver [[docker-compose]].

## Bounded contexts (hexágonos) del backend

| Hexágono | Responsabilidad | Nota |
|---|---|---|
| `client_hexagon` | Clientes, sedes, áreas de servicio, encargados | [[dominio-cliente]] |
| `person_hexagon` | Personas físicas + identidad Keycloak | [[dominio-persona-identidad]] |
| `location_hexagon` | Países y ciudades | [[dominio-ubicacion]] |
| `equipment_hexagon` | Equipos, tipos, marcas, fabricantes, verificaciones técnicas, datos metrológicos — **núcleo del dominio de mantenimiento preventivo** | [[dominio-equipo-mantenimiento]] |
| `reports_hexagon` | Reportes cross-dominio (agregación de datos de otros hexágonos) | [[dominio-reportes]] |
| `event_persister_hexagon` | Persistencia de eventos de dominio como bitácora/auditoría | [[event-persister-outbox]] |
| `shared` / `bootstrap` | Infraestructura transversal: eventos, seguridad, excepciones, OpenAPI, RabbitMQ | [[arquitectura-hexagonal]] |

## El dato más importante para MalphasOS

El repo no es arquitectónicamente uniforme: convive un patrón antiguo (CRUD anémico, sin eventos ni commands — `client_hexagon`, `person_hexagon`) con un patrón nuevo ya adoptado en parte del código (agregados ricos + commands + eventos de dominio — `equipment_hexagon`, `location_hexagon`). Ver [[evolucion-arquitectonica-crud-a-cqrs]] y la síntesis en [[sintesis-malphasos]].

## Notas relacionadas

[[stack-tecnologico]] · [[arquitectura-hexagonal]] · [[sintesis-malphasos]] · [[alcance-malphasos]]
