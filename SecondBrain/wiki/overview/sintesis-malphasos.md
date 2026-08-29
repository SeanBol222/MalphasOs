---
name: sintesis-malphasos
description: La nota más importante del wiki — tesis evolutiva sobre qué patrones adoptar y por qué, se actualiza en cada ingest relevante
tags: [overview, sintesis, malphasos, decision-clave]
updated: 2026-08-29
---

# Síntesis: qué llevarse de bolivarbioingenieria-app a MalphasOS

Esta nota se actualiza cada vez que un ingest nuevo cambia el juicio general sobre qué reutilizar. Es la tesis evolutiva del wiki, no un dato estático.

## La tesis central (2026-08-27)

`bolivarbioingenieria-app` no es un sistema uniforme — es un sistema **a mitad de una migración arquitectónica que nunca se terminó** (ver [[evolucion-arquitectonica-crud-a-cqrs]]). Convive:

- Una **Generación 1** (CRUD anémico, sin eventos ni commands) en `client_hexagon`/`person_hexagon` — justamente el dominio de "gestión de clientes" que MalphasOS quiere absorber.
- Una **Generación 2** (agregados ricos + `AggregateRoot` + commands inmutables + eventos de dominio despachados vía puerto dual) en `equipment_hexagon`/`location_hexagon` — el dominio de "mantenimientos preventivos", el otro pilar de MalphasOS.

Esto significa que **MalphasOS no puede simplemente "copiar" el dominio de clientes tal cual** — sería copiar el patrón viejo justo cuando el patrón nuevo (más maduro, ya probado en `equipment_hexagon`) está disponible como referencia en el mismo repo. La recomendación de fondo: **construir ambos módulos de MalphasOS (clientes y mantenimiento) siguiendo la Generación 2 desde el día uno**, usando `equipment_hexagon` y `location_hexagon` como plantilla arquitectónica, y el modelo conceptual de `client_hexagon` (jerarquía Client→Headquarter→ServiceArea) solo como inspiración de dominio, no de implementación.

## Lo que es sólido y portable casi sin fricción

- Infraestructura transversal: [[aggregate-root-pattern]], [[eventos-de-dominio]] (con el fix de routing key), [[patron-cqrs-commands]], [[patron-mapper-mapstruct]], [[patron-report-data-provider]].
- Seguridad: [[seguridad-keycloak-backend]] + [[integracion-keycloak-frontend]] + [[keycloak-configuracion]] — de las piezas más maduras de todo el repo.
- Infraestructura de despliegue: [[docker-compose]], convenciones de [[esquema-bd-v4]] (prefijos, soft-delete vía [[patron-soft-delete]], PKs UUID).
- El modelo de dominio de mantenimiento completo: [[dominio-equipo-mantenimiento]] — es, literalmente, el núcleo de negocio que MalphasOS necesita.

## Lo que hay que decidir explícitamente, no heredar por defecto

- [[relacion-manager-persona]] — la relación ya está decidida en el original (identidad compartida); lo que falta es expresarla en el modelo de dominio, y elegir entre `@MapsId` o absorber el rol dentro de `Person`.
- [[manejo-global-excepciones]] — diseñar el manejo de errores con una base compartida real entre módulos, no repetir el boilerplate divergente detectado aquí.
- Completar lo que en el original quedó a medias antes de confiar en ello: ver toda la tabla en [[deuda-tecnica-y-riesgos]].

## Alcance propuesto

Ver [[alcance-malphasos]] para el mapeo módulo-por-módulo de qué entra y qué no, y [[checklist-reutilizacion]] para el orden priorizado de trabajo.

## Notas relacionadas

[[evolucion-arquitectonica-crud-a-cqrs]] · [[dominio-equipo-mantenimiento]] · [[dominio-cliente]] · [[deuda-tecnica-y-riesgos]] · [[alcance-malphasos]] · [[checklist-reutilizacion]]
