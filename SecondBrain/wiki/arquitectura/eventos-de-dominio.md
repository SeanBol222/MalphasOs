---
name: eventos-de-dominio
description: Contrato DomainEvent/EventMetadata/Payload y el doble dispatcher intercambiable (Spring in-process vs RabbitMQ)
tags: [arquitectura, backend, eventos, "reusable:alta"]
source: Backend/sigma-bb/src/main/java/.../shared/domain/events/, shared/application/ports/output/EventDispatcherPort.java
estado: deuda-tecnica
updated: 2026-08-27
---

# Eventos de dominio

## Contrato (`shared/domain/events/`)

- `DomainEvent<T extends Payload>` — interfaz con `metadata()` y `payload()`.
- `EventMetadata` — record: `eventTopic, eventId, aggregateType, eventType, version, occurredAt, aggregateId`.
- `Payload` — marker interface, un payload propio por entidad (`EquipmentPayload`, `BrandPayload`, etc.).
- `AggregateRoot` — ver [[aggregate-root-pattern]].

## Despacho: puerto único, dos adaptadores intercambiables

`EventDispatcherPort.dispatch(event)` (puerto de salida en `shared/application/ports/output/`) tiene dos implementaciones, seleccionadas por `@Qualifier`:

- `SpringDispatcher` (`@Qualifier("springDispatcher")`) — usa `ApplicationEventPublisher` in-process. Es el que usan los services de escritura por defecto en `equipment_hexagon`.
- `RabbitMQDispatcher` — publica al exchange topic `"events-domain"` con routing key `"events-domains." + eventType` (nota la **s** en `events-domains`).

Es arquitectura hexagonal aplicada a los propios eventos: el dominio depende solo del puerto, no de si el mecanismo real es in-process o distribuido.

## ⚠️ Bug detectado: mismatch de routing key

`RabbitMQConfig` declara el binding de la cola con el patrón `"events-domain.#"` (sin la "s"), pero `RabbitMQDispatcher` publica con el prefijo `"events-domains."` (con "s"). Esto significa que, si `RabbitMQDispatcher` llega a ser el dispatcher activo, **los mensajes publicados no calzarían con el binding declarado** y se perderían o irían a ninguna cola. No está confirmado si esto ya causó un problema real porque no se verificó si `RabbitMQDispatcher` está siendo usado activamente en producción (`SpringDispatcher` parece ser el default en los services revisados). **Corregir esta discrepancia antes de portar el patrón a MalphasOS**, no asumir que el dispatcher de Rabbit funciona tal cual está.

## Quién publica eventos

Los hexágonos con agregados ricos (`equipment_hexagon`, `location_hexagon`) — convención de carpetas `domain/<entidad>/events/` con eventos `XCreatedEvent`/`XUpdatedEvent`/`XDeletedEvent` por entidad. `client_hexagon`/`person_hexagon` **no** publican eventos de dominio (patrón viejo, ver [[evolucion-arquitectonica-crud-a-cqrs]]).

## Consumidores

- `event_persister_hexagon` — persiste eventos como bitácora/auditoría. Ver [[event-persister-outbox]] (⚠️ actualmente desconectado).
- `EquipmentReportProviderAdapter` — no es un listener reactivo real pese a estar en `infrastructure/input/listeners`; funciona como agregador síncrono bajo demanda para [[dominio-reportes]].

## Reutilizable en MalphasOS

`reusable:alta` el contrato y el patrón de doble dispatcher — es limpio y portable. `reusable:media` la implementación de `RabbitMQDispatcher` específicamente, por el bug de routing key: corregirlo antes de reutilizar.

## Notas relacionadas

[[aggregate-root-pattern]] · [[event-persister-outbox]] · [[patron-cqrs-commands]] · [[deuda-tecnica-y-riesgos]] · [[dominio-equipo-mantenimiento]]
