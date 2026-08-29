---
name: alcance-malphasos
description: Mapeo módulo por módulo de qué entra a MalphasOS desde bolivarbioingenieria-app y qué se queda fuera
tags: [malphasos, alcance, planificacion]
updated: 2026-08-29
---

# Alcance de MalphasOS frente a bolivarbioingenieria-app

MalphasOS es la extracción de **gestión de clientes** + **gestión de mantenimientos preventivos** como aplicación independiente. Esta nota es una lectura, no una decisión tomada — el alcance real lo define el usuario cuando arranque el proyecto de código.

## Entra (núcleo directo de MalphasOS)

| Del original | Se convierte en | Nota de referencia |
|---|---|---|
| `client_hexagon` (modelo conceptual, no implementación) | Módulo de gestión de clientes | [[dominio-cliente]] |
| `person_hexagon` | Módulo de personas/identidad | [[dominio-persona-identidad]] |
| `location_hexagon` | Módulo de ubicación (país/ciudad) — también sirve de plantilla arquitectónica | [[dominio-ubicacion]] |
| `equipment_hexagon` | Módulo de equipos y mantenimiento preventivo — núcleo del negocio, plantilla arquitectónica principal | [[dominio-equipo-mantenimiento]] |
| `reports_hexagon` (si MalphasOS necesita reportes cross-módulo) | Módulo de reportes | [[dominio-reportes]] |
| `shared`/`bootstrap` (eventos, excepciones, seguridad, OpenAPI) | Infraestructura transversal de MalphasOS | [[arquitectura-hexagonal]] |
| Tablas de ubicación, persona, cliente, equipo, mantenimiento en [[esquema-bd-v4]] | Esquema de base de datos de MalphasOS | [[esquema-bd-v4]] |
| `keycloak/` (realm, 3 clients, theme) | Identidad de MalphasOS (realm propio) | [[keycloak-configuracion]] |
| `docker-compose.yaml`, patrón de `initdb.d` | Infraestructura de MalphasOS | [[docker-compose]] |
| `Frontend/src/auth/` completo | Starter de autenticación del frontend de MalphasOS | [[integracion-keycloak-frontend]] |

## Fuera de alcance (queda en bolivarbioingenieria-app o es dominio ajeno)

- Cualquier cosa específica de facturación/roles que sea propia del negocio actual y no aplique a MalphasOS (a confirmar con el usuario cuando se defina el alcance real — este wiki no asume qué se queda).
- `event_persister_hexagon` **tal cual está** — la arquitectura es reutilizable pero está desconectada, ver [[event-persister-outbox]]; portar la idea, no el código en su estado actual sin completarlo.

## Decisiones pendientes explícitas antes de escribir código

Ver [[relacion-manager-persona]] y la tabla completa en [[deuda-tecnica-y-riesgos]].

## Notas relacionadas

[[sintesis-malphasos]] · [[checklist-reutilizacion]] · [[dominio-equipo-mantenimiento]] · [[dominio-cliente]]
