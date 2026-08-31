---
name: eventos-de-dominio
description: Contrato DomainEvent/EventMetadata/Payload y el doble dispatcher intercambiable (Spring in-process vs RabbitMQ)
tags: [arquitectura, backend, eventos, "reusable:alta"]
source: Backend/sigma-bb/src/main/java/.../shared/domain/events/, shared/application/ports/output/EventDispatcherPort.java
estado: deuda-tecnica
updated: 2026-08-29
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

## Estado en MalphasOS (portado el 2026-08-29)

El contrato se porta con tres correcciones y una omisión deliberada.

**`eventTopic` sale del dominio.** El original guardaba ahí `"events-domain"`, el nombre del exchange de RabbitMQ: un detalle de transporte escrito dentro del modelo de dominio, y encima duplicando el valor que el propio despachador ya tenía como constante. Dónde se publica un evento lo decide el adaptador de salida, no la entidad que lo emite. `EventMetadata` queda con seis campos en vez de siete.

**Fuera `Serializable`.** Verificado contra el `RabbitMQConfig` del original: usa `JacksonJsonMessageConverter`, de modo que la serialización de Java no interviene en ningún punto del recorrido. Exigirla obligaba a cada payload nuevo a arrastrar una interfaz que nunca entra en juego.

**Metadata validada y construida en un sitio.** `EventMetadata.of(...)` genera identificador y marca de tiempo, y el constructor rechaza campos vacíos. Un evento sin `eventId` no se puede deduplicar y uno sin `occurredAt` no se puede ordenar: son fallos que no rompen nada al emitir y dejan el evento inservible al otro lado.

**No se portó el `RabbitMQDispatcher`.** Tres razones: no hay ningún consumidor que escuche, su clave de publicación no casa con el binding declarado (el bug de esta misma nota), y los dos listeners que lo consumirían están desactivados. Sería un componente sin uso y con un defecto conocido. Por eso `SpringEventDispatcher` tampoco lleva `@Qualifier`: mientras haya una sola implementación, exigirlo obliga a nombrarla en cada punto de inyección sin que haya nada entre lo que elegir.

Se agrega `dispatchAll(...)` al puerto, porque un agregado casi siempre entrega varios eventos y el original obligaba a que cada servicio escribiera el bucle.

## Reutilizable en MalphasOS

`reusable:alta` el contrato y el patrón de doble dispatcher — es limpio y portable, con los ajustes de arriba. `reusable:media` la implementación de `RabbitMQDispatcher` específicamente, por el bug de routing key: corregirlo antes de reutilizar.

## Notas relacionadas

[[aggregate-root-pattern]] · [[migracion-location-hallazgos]] · [[event-persister-outbox]] · [[patron-cqrs-commands]] · [[deuda-tecnica-y-riesgos]] · [[dominio-equipo-mantenimiento]]
