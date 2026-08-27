# Log

Registro cronológico append-only del SecondBrain. Formato: `## [YYYY-MM-DD] tipo | tema`.

## [2026-08-27] ingest | Construcción inicial del SecondBrain a partir de bolivarbioingenieria-app

Primera carga completa del wiki. Se exploró el repo raw (`bolivarbioingenieria-app`, rama `backend/reports/client`) en 4 frentes paralelos:

1. Núcleo arquitectónico backend: `bootstrap/` (excepciones globales, seguridad/Keycloak, OpenAPI), `shared/` (eventos de dominio, RabbitMQ), `event_persister_hexagon/`, `pom.xml`, `application.yaml`.
2. Hexágonos `client_hexagon`, `person_hexagon`, `location_hexagon` — modelo de dominio, capas, mappers, integración Keycloak para identidad.
3. Hexágonos `equipment_hexagon` (núcleo de mantenimiento preventivo) y `reports_hexagon` — patrón CQRS por commands, eventos por entidad, agregador cross-dominio de reportes.
4. Frontend (React+TS+Vite), esquema de base de datos PostgreSQL v4 (27 tablas), evolución v1→v4, `docker-compose.yaml`, configuración de Keycloak (realm, clients, theme).

Resultado: ~30 notas creadas en `wiki/`, organizadas en `overview/`, `arquitectura/`, `dominio/`, `base-de-datos/`, `frontend/`, `infraestructura/`, `patrones-reutilizables/`, `malphasos/`. Hallazgo transversal más importante: el repo muestra una **evolución arquitectónica a medio camino** — `client_hexagon`/`person_hexagon` usan un patrón CRUD anémico más antiguo, mientras `equipment_hexagon`/`location_hexagon` ya aplican agregados ricos + commands + eventos de dominio (commit `2d39984 Fix: applying CQRS patterns`). Para MalphasOS, el patrón recomendado como punto de partida es el de `equipment_hexagon`, no el de `client_hexagon`. Ver [[sintesis-malphasos]] y [[evolucion-arquitectonica-crud-a-cqrs]].

Se detectó también deuda técnica real que no debe asumirse como funcional al portar: ver [[deuda-tecnica-y-riesgos]] (event_persister desconectado, mismatch de routing key en RabbitMQ, `createSuperAdminUser` sin implementar, posible ambigüedad de beans en dispatchers).

## [2026-08-27] ingest | Arranque de la construcción de MalphasOS: pom, infraestructura y base de datos

Primeros tres micro-commits del proyecto real, cada uno verificado antes de commitear:

1. `chore(pom)` — dependencias del backend: MapStruct 1.6.3 + `lombok-mapstruct-binding` con los annotation processors encadenados en orden, springdoc-openapi 3.0.2, keycloak-admin-client 26.0.9.
2. `feat(infra)` — `docker-compose.yaml` con Postgres 17 + Keycloak 26.6.1 + RabbitMQ 4.1.5, `.env.example` versionado y `.env` ignorado. Verificado: los tres contenedores llegan a `healthy`.
3. `feat(db)` — backend conectado a PostgreSQL con Flyway y tests sobre Testcontainers. `mvn test` pasa 3/3 por primera vez (venía rojo desde el proyecto generado por Initializr).

**Hallazgos que corrigieron o ampliaron el wiki:**

- **Bug nuevo en el proyecto original**: el import del realm de Keycloak nunca se ejecutó — `KEYCLOAK_IMPORT` es de la era WildFly y Keycloak 26 la ignora, además apunta a un archivo inexistente. Añadido a [[deuda-tecnica-y-riesgos]], [[docker-compose]] y [[keycloak-configuracion]].
- **Corrección**: los dos `realm-export.json` **no** son idénticos, como afirmaba [[keycloak-configuracion]]. Difieren en tamaño.
- **Corrección**: la mezcla de Jackson 2 y 3 en el `pom.xml` original no era un riesgo a evitar sino la transición del propio framework — Spring Boot 4 ya trae Jackson 3 de serie. Actualizado [[stack-tecnologico]].
- **Conocimiento nuevo**: Spring Boot 4 modularizó las autoconfiguraciones (`flyway-core` suelto no activa nada), Flyway 12 separa el dialecto por motor, y Testcontainers 2.x renombró sus artefactos. Nota nueva: [[stack-spring-boot-4-particularidades]].
- **Nota nueva**: [[decisiones-tecnicas-malphasos]], registro de decisiones tomadas con su justificación (Flyway sobre initdb, Testcontainers sobre H2, puerto 8081, `ddl-auto: validate`, PKs UUID desde el inicio).

[[checklist-reutilizacion]] actualizado con el progreso real: pasos 1 y parte del 2 completados. Siguiente paso natural: portar `shared/domain/events` ([[aggregate-root-pattern]]).
