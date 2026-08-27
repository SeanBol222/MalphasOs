---
name: deuda-tecnica-y-riesgos
description: Registro centralizado de bugs, inconsistencias y piezas incompletas detectadas en bolivarbioingenieria-app — no asumir que estas partes funcionan al portarlas
tags: [deuda-tecnica, riesgos, "reusable:no"]
updated: 2026-08-27
---

# Deuda técnica y riesgos conocidos

Nota índice que centraliza todo lo detectado como bug, inconsistencia o pieza incompleta durante la construcción de este wiki (2026-08-27). El propósito es que ninguna sesión futura de Claude asuma que estas partes ya funcionan correctamente solo porque están en el código — **verificar y corregir antes de portar a MalphasOS**, no copiar tal cual.

| Hallazgo | Dónde | Severidad para portar | Nota detallada |
|---|---|---|---|
| **El import del realm de Keycloak nunca se ejecutó**: `KEYCLOAK_IMPORT` es de la era WildFly y Keycloak 26 la ignora; además apunta a un nombre de archivo inexistente (`real-export.json`) con basura concatenada en el valor | `docker-compose.yaml` | Alta — al reproducir el entorno desde cero el realm no aparece solo. **Ya corregido en MalphasOS** con `--import-realm` | [[docker-compose]], [[keycloak-configuracion]] |
| Los dos `realm-export.json` (`imports/` y `configuration/`) **no son idénticos** — difieren en tamaño; el wiki afirmaba lo contrario hasta el 2026-08-27 | `keycloak/` | Media — verificar cuál es la fuente real antes de clonar el realm | [[keycloak-configuracion]] |
| Mismatch de routing key: dispatcher publica `events-domains.*` (con "s"), binding declara `events-domain.#` (sin "s") | `RabbitMQDispatcher` / `RabbitMQConfig` | Alta si se usa Rabbit como dispatcher activo — mensajes no llegarían a la cola | [[eventos-de-dominio]] |
| `event_persister_hexagon` tiene el pipeline completo pero los dos listeners de entrada están comentados/desactivados — no persiste eventos hoy | `EventListenerRabbitMQ`, `EventListenerSpring` | Alta — no asumir auditoría de eventos funcionando | [[event-persister-outbox]] |
| `EventPersisterService` crea un `ObjectMapper` nuevo por llamada en vez de reutilizar el bean configurado | `event_persister_hexagon` | Baja (ineficiencia, no bug funcional) | [[event-persister-outbox]] |
| Handler de validación en el advice "global" retorna `ClientErrorResponse` (tipo de un hexágono específico), no el `GlobalErrorResponse` genérico | `bootstrap/exception/GlobalControllerAdvice` | Media — el contrato de error no es realmente uniforme | [[manejo-global-excepciones]] |
| `PersonErrorCatalog.UNKNOWN_ERROR` reutilizado también desde `ClientGlobalControllerAdvice` — acoplamiento entre catálogos que deberían ser independientes | `person_hexagon` / `client_hexagon` | Media | [[manejo-global-excepciones]] |
| Excepciones `CityNotFoundException`/`CountryNotFoundException` viven en `infrastructure/output/errors`, no en `domain/exception` como en los otros hexágonos | `location_hexagon` | Baja (inconsistencia de ubicación, no funcional) | [[manejo-global-excepciones]], [[dominio-ubicacion]] |
| `createSuperAdminUser` sin implementar (`return null`) | `PersonIdentityAdapter` | Media — no hay flujo de creación de super-admin funcional | [[dominio-persona-identidad]] |
| No hay relación explícita `Manager`↔`Person` en el modelo de dominio | `client_hexagon` / `person_hexagon` | Alta como decisión de diseño pendiente — resolver explícitamente en MalphasOS | [[relacion-cliente-persona-ambiguedad]] |
| Mezcla de Jackson 3 (`tools.jackson.core`) con módulos Jackson 2 clásicos en el mismo `pom.xml` | `pom.xml` | Media — verificar compatibilidad antes de replicar | [[stack-tecnologico]] |
| Patrón CQRS/commands no uniforme dentro del mismo hexágono (`TechnicalVerificationService` no separa puertos read/write como el resto de `equipment_hexagon`) | `equipment_hexagon` | Baja — no es un bug, es aplicación parcial de un patrón en evolución | [[patron-cqrs-commands]] |
| Credenciales de `pgadmin` hardcodeadas en `docker-compose.yaml` en vez de vía `.env` | `docker-compose.yaml` | Baja (higiene, solo dev) | [[docker-compose]] |
| `client_hexagon`/`person_hexagon` completos usan el patrón CRUD anémico ya superado por `equipment_hexagon`/`location_hexagon` | todo el hexágono | Alta como decisión arquitectónica — no replicar el patrón viejo | [[evolucion-arquitectonica-crud-a-cqrs]] |

## Cómo usar esta nota

Antes de decidir portar cualquier pieza de `bolivarbioingenieria-app` a MalphasOS, revisar si aparece en esta tabla. Si aparece, la nota de detalle explica qué corregir antes de confiar en ella — no es una lista de razones para descartar el patrón completo, casi todos son arreglos puntuales sobre patrones por lo demás sólidos.

## Notas relacionadas

[[sintesis-malphasos]] · [[checklist-reutilizacion]]
