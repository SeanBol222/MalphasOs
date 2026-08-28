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

## [2026-08-27] ingest | Estructura de paquetes, migración de bootstrap y contenedor de la aplicación

Tres avances grandes en MalphasOS, cada uno en su micro-commit.

**Estructura de paquetes** (178 directorios, sin archivos todavía). Se adoptó camelCase para los paquetes multipalabra y se eliminó el sufijo `_hexagon`. Tres correcciones sobre el original al crearla: `domain/exception` consistente en todos los módulos (el original mezcla ubicaciones), `technicalVerificationEquipment` fuera de `domain` porque el propio código original aclara que no es agregado, y `model` renombrado a `equipmentModel` por ser ambiguo con el paquete convencional de DTOs. Detalle en [[decisiones-tecnicas-malphasos]].

**Migración de `bootstrap`** (9 clases). No fue copia literal: se corrigieron seis problemas del original, entre ellos dos con impacto de seguridad — el handler de `DataAccessException` devolvía nombres de tablas y SQL al cliente, y `KeycloakRoleConverter` hacía casts sin verificar sobre los claims de un token, que es entrada externa no confiable. Todos registrados en [[deuda-tecnica-y-riesgos]] y detallados en [[manejo-global-excepciones]] y [[seguridad-keycloak-backend]].

**Hallazgo de diseño en Spring Security**: apagar `app.security.enabled` no dejaba la API abierta sino protegida por la cadena por defecto de Spring, con contraseña generada — Swagger UI devolvía 401. Ni una intención ni la otra. MalphasOS añade `SecurityDisabledConfig`, una cadena `permitAll` explícita con advertencia en el arranque. No existe equivalente en el original.

**Contenedor y proyecto de Compose**. Nota nueva: [[dockerfile-y-contenedores]], que documenta los cuatro problemas del Dockerfile original (ejecuta como root, JDK completo en runtime, sin `.dockerignore`, `CMD` en vez de `ENTRYPOINT`) y cómo se corrigieron. Se incorporó Spring Boot Actuator para tener un healthcheck real, exponiendo únicamente `health`. Trampa encontrada: el health indicator de RabbitMQ apunta a `localhost` por defecto y reporta DOWN dentro del contenedor con la aplicación sana.

Verificado en ejecución: los cuatro servicios llegan a `healthy`, Swagger UI responde con sus cinco grupos, Flyway valida la migración por red interna y el proceso corre como `uid 100`, no root.

Siguiente paso del checklist: portar `shared/domain/events` ([[aggregate-root-pattern]]).

## [2026-08-28] ingest | Migración completa de person_hexagon, primer módulo de dominio

Seis micro-commits de adentro hacia afuera: dominio, esquema, aplicación, persistencia, identidad y REST. Se conservó el patrón de **Generación 1** del original por decisión explícita del usuario, pese a que el wiki recomendaba Generación 2.

El resultado no es una copia: la migración **destapó 22 defectos**, inventariados en la nota nueva [[migracion-person-hallazgos]]. Los de mayor impacto:

- **El endpoint de creación de personas fallaba siempre**: su DTO no llevaba `tipoPersona` pese a alimentar una columna `NOT NULL`.
- **`PersonService` dependía del adaptador concreto de Keycloak, no del puerto.** `PersonIdentityPort` existía sin que nadie lo usara, lo que anulaba el propósito de la arquitectura hexagonal.
- **`validar_roles_persona()` era código muerto**: la función existía pero ningún `CREATE TRIGGER` la asociaba a la tabla. Dos reglas de negocio que nunca se ejecutaron; ahora viven en el dominio.
- **Fuga de conexiones**: el `Response` de JAX-RS nunca se cerraba al crear usuarios en Keycloak.
- **Un `switch` sin caso por defecto** habría creado usuarios sin ningún grupo —sin permisos— al agregar un `RoleType` nuevo.
- **`KeycloakUnauthorizedException` respondía 401** al llamante, culpándolo de una mala configuración del servidor.

Nota nueva también: [[antipatron-open-in-view]]. Desactivar esa opción, que Spring Boot activa por omisión, sacó a la luz un `LazyInitializationException` latente en el adaptador de persistencia del original. Es la señal de que la carga perezosa dependía de un efecto colateral del framework y no de transacciones declaradas.

**Tres de los defectos los encontraron las pruebas, no la lectura del código**: el `LazyInitializationException`, la desincronización entre el enum `PersonType` y el catálogo de la base, y un error propio al escribir `"ingeniero"` donde correspondía `ENGINEER`.

Además, en esta tanda se tradujeron al inglés los asuntos de los 26 commits del historial y todos los identificadores del código, conservando en español los campos del dominio por coincidir con las columnas de la base. Ver [[decisiones-tecnicas-malphasos]].

58 pruebas en verde. Los catorce endpoints del módulo quedaron verificados contra la base real, incluido el borrado lógico y la traducción de una regla de dominio a un 400 con mensaje útil.
