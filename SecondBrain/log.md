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
