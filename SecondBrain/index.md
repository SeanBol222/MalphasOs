# Índice — SecondBrain MalphasOS

Catálogo de contenido del wiki. Ver [[CLAUDE.md]] para las convenciones. Leyenda de reusabilidad: 🟢 alta · 🟡 media · 🔴 baja/no.

## Overview

- [[sistema-bolivarbioingenieria]] 🟢 — Qué es bolivarbioingenieria-app, sus módulos y cómo encajan.
- [[stack-tecnologico]] 🟢 — Stack completo backend/frontend/BD/infra con versiones exactas.
- [[sintesis-malphasos]] ⭐ — **La nota más importante**: tesis evolutiva de qué reutilizar y por qué.

## Arquitectura

- [[arquitectura-hexagonal]] 🟢 — Ports & adapters, flujo real de una request capa por capa.
- [[evolucion-arquitectonica-crud-a-cqrs]] ⭐ — El repo convive con 2 generaciones de patrón; hallazgo clave para MalphasOS.
- [[patron-cqrs-commands]] 🟢 — Commands inmutables + separación selectiva de puertos read/write.
- [[aggregate-root-pattern]] 🟢 — Clase base que acumula eventos de dominio.
- [[eventos-de-dominio]] 🟡 — Contrato DomainEvent + doble dispatcher. Contiene bug de routing key.
- [[event-persister-outbox]] 🟡 — Hexágono de auditoría de eventos, actualmente desconectado.
- [[manejo-global-excepciones]] 🟡 — Catálogo + advice + DTO, con inconsistencias reales detectadas.
- [[seguridad-keycloak-backend]] 🟢 — Resource server OAuth2/JWT + admin client, dos piezas separadas.
- [[openapi-swagger]] 🟢 — Un GroupedOpenApi por dominio.

## Dominio (hexágonos de negocio)

- [[dominio-cliente]] 🟡 — client_hexagon: Client/Headquarter/ServiceArea/Manager. Patrón viejo (Generación 1).
- [[dominio-persona-identidad]] 🟢 — person_hexagon: Person + integración Keycloak Admin API.
- [[dominio-ubicacion]] 🟢 — location_hexagon: Country/City. Referencia de Generación 2.
- [[dominio-equipo-mantenimiento]] ⭐ — equipment_hexagon: **el núcleo de mantenimiento preventivo y la referencia arquitectónica principal**.
- [[dominio-reportes]] 🟢 — reports_hexagon: agregador cross-dominio desacoplado.
- [[relacion-cliente-persona-ambiguedad]] 🔴 — Manager no referencia Person; decisión de diseño pendiente, no replicar.

## Base de datos

- [[esquema-bd-v4]] 🟢 — 27 tablas, convenciones de prefijo, soft-delete universal.
- [[evolucion-esquema-v1-v4]] 🟡 — Endurecimiento incremental de un mismo modelo, no rediseño.

## Frontend

- [[arquitectura-frontend]] 🟡 — React 19 + TS + Vite, proyecto en bootstrap.
- [[integracion-keycloak-frontend]] 🟢 — keycloak-js + AuthProvider + PrivateRoute + apiFetch, starter completo.

## Infraestructura

- [[docker-compose]] 🟢 — 5 servicios: Postgres, pgAdmin, RabbitMQ, Keycloak, backend.
- [[keycloak-configuracion]] 🟢 — Realm sigma-bb-realm, 3 clients, theme custom.

## Patrones reutilizables (transversales)

- [[patron-mapper-mapstruct]] 🟢 — Mapper por frontera de capa, `@AfterMapping` para relaciones bidireccionales.
- [[patron-catalogo-errores-por-contexto]] 🟡 — Catálogo de errores por bounded context, boilerplate duplicado.
- [[patron-soft-delete]] 🟢 — `b_estado_activo` universal en vez de DELETE físico.
- [[patron-report-data-provider]] 🟢 — Puerto genérico plugin/strategy para agregación cross-módulo.
- [[patron-event-dispatcher-dual]] 🟢 — Un puerto, dos mecanismos de despacho intercambiables.
- [[deuda-tecnica-y-riesgos]] ⭐ — Registro centralizado de todos los bugs/inconsistencias detectados. Consultar antes de portar cualquier pieza.

## MalphasOS

- [[alcance-malphasos]] — Mapeo módulo por módulo de qué entra y qué no.
- [[checklist-reutilizacion]] — Orden priorizado de trabajo sugerido para cuando arranque el código de MalphasOS.

---

**30 notas** · última actualización 2026-08-27 · ver [[log.md]] para el historial de ingests.
