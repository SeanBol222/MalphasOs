---
name: sintesis-malphasos
description: La tesis de que patrones adoptar del original, cerrada el 2026-09-09 con lo que realmente ocurrio al aplicarla
tags: [overview, sintesis, malphasos, decision-clave, "describe:ambos"]
estado: estable
updated: 2026-09-09
---

# Síntesis: qué llevarse de bolivarbioingenieria-app a MalphasOS

> **Nota cerrada el 2026-09-09.** Esta era la tesis del wiki mientras la pregunta abierta era *qué portar*. **La migración terminó**, así que deja de ser una recomendación y pasa a ser el registro de una predicción y de cómo salió. Se conserva entera —no se reescribe— porque explica de dónde vienen las decisiones que hoy sostienen el código.
>
> Si buscas **qué falta por construir**, es [[hoja-de-ruta-producto]]. Si buscas **por qué el código es como es**, es [[decisiones-tecnicas-malphasos]].

## Cómo salió la tesis (2026-09-09)

Se comprobó contra el código, no contra el recuerdo:

| Lo que esta nota predijo | Lo que ocurrió |
|---|---|
| Construir **ambos** módulos en Generación 2 "desde el día uno" | Se cumplió en `location`, `client` y `equipment`. **`person` se quedó en Generación 1 por decisión explícita**, por ser el primero y el más acoplado a Keycloak. La tesis no se aplicó al pie de la letra |
| `client_hexagon` como inspiración de dominio, no de implementación | Se cumplió: `client` se **reconstruyó** en cuatro agregados pequeños, no se portó |
| Infraestructura transversal "portable casi sin fricción" | Se cumplió a medias. `AggregateRoot` y el contrato de eventos sí, quitándoles el `eventTopic`; **[[patron-mapper-mapstruct]] resultó inservible** para construir agregados de Generación 2, y los mappers de persistencia se escriben a mano |
| El despachador dual de eventos con el arreglo de la routing key | **No se portó.** Los cuatro módulos despachan en proceso; `RabbitMQDispatcher` sigue pendiente |
| [[patron-report-data-provider]] entre lo sólido y portable | **No se portó**: no hay módulo de reportes. Su grupo de OpenAPI apunta a rutas que no existen |
| [[integracion-keycloak-frontend]] entre lo sólido y portable | **No se portó**: no hay frontend. Sigue siendo el punto de partida cuando lo haya |
| «Diseñar el manejo de errores con una base compartida real entre módulos» | **Se decidió lo contrario**, y a propósito: cada contexto acotado es dueño de su catálogo. Ver [[decisiones-tecnicas-malphasos]] |

Lo que sí resultó ser el hallazgo más útil de toda la nota es el de fondo: **el repositorio original convivía con dos generaciones de patrón y había que elegir**. Eso está en [[evolucion-arquitectonica-crud-a-cqrs]], y sigue siendo la lectura que explica el resto.

---

*Lo que sigue es el texto original de la nota, tal como se escribió entre el 2026-08-27 y el 2026-08-29.*

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
