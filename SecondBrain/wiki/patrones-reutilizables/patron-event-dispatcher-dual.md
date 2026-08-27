---
name: patron-event-dispatcher-dual
description: Un puerto EventDispatcherPort con dos adaptadores intercambiables (in-process vs distribuido) seleccionables por @Qualifier
tags: [patron, backend, eventos, "reusable:alta"]
source: Backend/sigma-bb/src/main/java/.../shared/application/ports/output/EventDispatcherPort.java
updated: 2026-08-27
---

# Patrón: dispatcher de eventos dual (in-process / distribuido) tras un solo puerto

`EventDispatcherPort` tiene dos implementaciones — `SpringDispatcher` (`ApplicationEventPublisher` in-process) y `RabbitMQDispatcher` (publica a RabbitMQ) — seleccionables por `@Qualifier` sin que el dominio ni la capa de aplicación sepan cuál está activa. Es arquitectura hexagonal aplicada específicamente al mecanismo de despacho de eventos. Detalle completo, incluyendo un bug de routing key detectado en la implementación de Rabbit, en [[eventos-de-dominio]].

## Por qué vale la pena como patrón independiente

Permite empezar un módulo nuevo con despacho in-process (simple, sin infraestructura extra) y migrar a distribuido (RabbitMQ, Kafka, lo que sea) más adelante cambiando solo la implementación inyectada — sin tocar los agregados de dominio ni los services que llaman `dispatchEvents()`.

## Reutilizable en MalphasOS

`reusable:alta` el patrón; `reusable:media` la implementación concreta de `RabbitMQDispatcher` hasta corregir el mismatch de routing key documentado en [[eventos-de-dominio]] y [[deuda-tecnica-y-riesgos]].

## Notas relacionadas

[[eventos-de-dominio]] · [[aggregate-root-pattern]] · [[deuda-tecnica-y-riesgos]]
