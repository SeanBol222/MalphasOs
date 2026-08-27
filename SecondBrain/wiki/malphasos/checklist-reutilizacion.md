---
name: checklist-reutilizacion
description: Orden priorizado sugerido de qué portar primero al construir MalphasOS, basado en el análisis de todo el wiki
tags: [malphasos, checklist, planificacion]
updated: 2026-08-27
---

# Checklist priorizado de reutilización

Orden sugerido para la construcción de MalphasOS. **La construcción ya arrancó** (2026-08-27); las decisiones tomadas se registran en [[decisiones-tecnicas-malphasos]].

## 1. Infraestructura base primero (sin esto no hay nada que construir encima)

- [x] **Hecho.** Clonar el patrón de `docker-compose.yaml` — Postgres + Keycloak compartiendo instancia + RabbitMQ. Corregido el import de realms roto del original, healthcheck en los tres servicios, pgAdmin bajo perfil `tools`. [[docker-compose]]
- [ ] Clonar `realm-export.json`, renombrar a `malphasos-realm`, adaptar los 3 clients. El compose ya lo importa correctamente con `--import-realm`; falta el archivo del realm. [[keycloak-configuracion]]
- [x] **Hecho.** Estrategia de esquema decidida: **Flyway**, no scripts `initdb`. `V1__baseline.sql` establece el punto de partida con PKs UUID como convención. Cada módulo aportará su migración. [[esquema-bd-v4]], [[evolucion-esquema-v1-v4]], [[decisiones-tecnicas-malphasos]]
- [x] **Hecho.** Backend conectado a PostgreSQL, arrancando en el puerto 8081, con tests sobre Testcontainers en verde.

## 2. Esqueleto de aplicación

- [x] **Hecho.** Dependencias del backend en el `pom.xml`: MapStruct + binding con los annotation processors en orden, springdoc-openapi, keycloak-admin-client. Ojo con las particularidades del stack moderno: [[stack-spring-boot-4-particularidades]]
- [ ] Portar `shared/domain/events` completo (`AggregateRoot`, `DomainEvent`, `EventMetadata`, `Payload`) sin cambios. **Siguiente paso natural.** [[aggregate-root-pattern]], [[eventos-de-dominio]]
- [ ] Portar `EventDispatcherPort` + `SpringDispatcher` + `RabbitMQDispatcher`, **corrigiendo el mismatch de routing key** antes de activar el de Rabbit. [[patron-event-dispatcher-dual]], [[deuda-tecnica-y-riesgos]]
- [x] **Hecho.** Portar `SecurityConfig` + `KeycloakRoleConverter` + `KeycloakAdminConfig`, con el client id ya configurable y sin casts inseguros. Pendiente activarla al crear el realm. [[seguridad-keycloak-backend]]
- [x] **Hecho.** Portar `OpenApiConfig` con grupos por módulo; fija la convención de rutas `/v1/api/<recurso>`. [[openapi-swagger]]
- [x] **Parcial.** Catálogo transversal migrado y corregido. Falta la interfaz/clase base común, que se definirá al migrar el primer módulo con excepciones propias. [[manejo-global-excepciones]], [[patron-catalogo-errores-por-contexto]]

## 3. Primer módulo de dominio — usar como plantilla la Generación 2, no la 1

- [ ] Implementar el primer hexágono (sugerido: ubicación, es el más simple) siguiendo exactamente el patrón de `location_hexagon`: agregado + factoría estática + eventos + commands si aplica. [[dominio-ubicacion]], [[evolucion-arquitectonica-crud-a-cqrs]]
- [ ] Adoptar MapStruct con el patrón de `@AfterMapping` para relaciones bidireccionales desde el primer mapper. [[patron-mapper-mapstruct]]

## 4. Módulo de mantenimiento preventivo (núcleo de negocio)

- [ ] Portar el modelo de dominio completo de `equipment_hexagon` (Equipment, EquipmentType, Brand, Manufacturer, Model, TechnicalVerification, MetrologicalData) como referencia directa. [[dominio-equipo-mantenimiento]]
- [ ] Decidir si se completa el patrón CQRS (read/write ports separados) en todos los sub-módulos o solo donde aporte valor — no es obligatorio uniformarlo, el propio original no lo hace. [[patron-cqrs-commands]]

## 5. Módulo de clientes — reconstruir, no copiar

- [ ] Resolver primero [[relacion-cliente-persona-ambiguedad]] como decisión de diseño explícita, antes de escribir el modelo.
- [ ] Reimplementar la jerarquía Client→Headquarter→ServiceArea siguiendo el patrón de agregados + eventos, no el CRUD anémico original. [[dominio-cliente]]

## 6. Identidad y frontend

- [ ] Portar `PersonIdentityPort`/Adapter completo, completando `createSuperAdminUser`. [[dominio-persona-identidad]]
- [ ] Portar `auth/keycloak.ts` + `AuthProvider` + `PrivateRoute` + `apiFetch` del frontend sin cambios estructurales. [[integracion-keycloak-frontend]]
- [ ] Decidir organización por feature (no por tipo técnico) desde el inicio del frontend de MalphasOS, dado que el original todavía no lo resolvió. [[arquitectura-frontend]]

## 7. Opcional / más adelante

- [ ] Completar y activar `event_persister_hexagon` como audit log si MalphasOS necesita trazabilidad de eventos. [[event-persister-outbox]]
- [ ] Portar el patrón `ReportDataProviderPort<T>` si se separa un módulo de reportes. [[patron-report-data-provider]], [[dominio-reportes]]

## Notas relacionadas

[[sintesis-malphasos]] · [[alcance-malphasos]] · [[deuda-tecnica-y-riesgos]]
