---
name: event-persister-outbox
description: event_persister_hexagon — hexágono dedicado a persistir eventos como log de auditoría; actualmente desconectado/no operativo
tags: [arquitectura, backend, eventos, auditoria, "reusable:media"]
source: Backend/sigma-bb/src/main/java/.../event_persister_hexagon/
estado: deuda-tecnica
updated: 2026-08-27
---

# `event_persister_hexagon` — event log de auditoría (no Event Sourcing real)

Es un **event listener + persistencia como bitácora append-only**, no un Event Store en el sentido de Event Sourcing (no hay replay de agregados desde eventos, solo registro histórico).

## Flujo diseñado

1. Puerto de entrada `EventListenerPort.handle(DomainEvent)`.
2. `EventPersisterService` serializa `metadata` y `payload` a JSON y arma `EventStoreEntry` (dominio puro, sin anotaciones JPA).
3. Puerto de salida `EventStorePort.save(entry)` → `EventStorePersistenceAdapter` → mapea con MapStruct a `EventStoreEntity` (JPA, tabla `domain_events`, columnas `metadata_json`/`payload_json` como `jsonb`) → `EventStoreRepository`.

## ⚠️ No está operativo actualmente

Los dos adaptadores de entrada están deshabilitados:
- `EventListenerRabbitMQ` — el `@RabbitListener` está completamente comentado.
- `EventListenerSpring` — escucha `@EventListener` de Spring, pero la línea `port.handle(event)` está comentada; solo hace `System.out.println`.

El pipeline mecánico existe completo (contrato, service, mapper, entity, repository) pero **no persiste eventos realmente hoy**. Es "patrón documentado pero no conectado", no un sistema funcionando en producción. También se detectó que `EventPersisterService` crea un `ObjectMapper` nuevo en cada llamada en vez de reutilizar el bean configurado (ineficiencia menor, no crítica).

## Reutilizable en MalphasOS

`reusable:media` — la **arquitectura** (hexágono dedicado, separado del hexágono de negocio, para persistir eventos como audit log) es un patrón limpio y vale la pena portarlo. Pero antes de confiar en él en MalphasOS hay que: (1) activar los listeners reales, (2) corregir el mismatch de routing key documentado en [[eventos-de-dominio]], (3) reutilizar el `ObjectMapper` configurado en vez de instanciar uno nuevo.

## Notas relacionadas

[[eventos-de-dominio]] · [[aggregate-root-pattern]] · [[deuda-tecnica-y-riesgos]]
