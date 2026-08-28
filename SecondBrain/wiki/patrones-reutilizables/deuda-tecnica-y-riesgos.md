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
| **`admin.full` está definido como rol pero no asignado a ningún grupo**, y es el permiso que exigen todos los controladores: cualquier usuario recibe 403 en todos los endpoints | `realm-export.json` | Alta — la API es inutilizable tal como esta configurada. **Corregido en MalphasOS** | [[keycloak-configuracion]] |
| **`attributes.frontendUrl` fijado a `http://keycloak:8080`** en el realm, un valor que anula `KC_HOSTNAME` y que ningún navegador puede resolver | `realm-export.json` | Alta — impide autenticarse fuera de Docker. **Corregido en MalphasOS** | [[issuer-uri-vs-jwk-set-uri]] |
| El realm no tiene protección contra fuerza bruta y permite 30 intentos fallidos, sin política de contraseñas | `realm-export.json` | Media. **Corregido en MalphasOS** | [[keycloak-configuracion]] |
| El client público del frontend habilita el flujo de contraseña directa, innecesario con PKCE, y usa `webOrigins: *` | `realm-export.json` | Media. **Corregido en MalphasOS** | [[keycloak-configuracion]] |
| Los 15 roles granulares del realm no se usan: el código solo comprueba `admin.full`, de modo que quien lo tiene puede hacer todo | realm + controladores | Media — decisión de diseño pendiente | [[keycloak-configuracion]] |
| Los dos `realm-export.json` (`imports/` y `configuration/`) **no son idénticos** — difieren en tamaño; el wiki afirmaba lo contrario hasta el 2026-08-27 | `keycloak/` | Media — verificar cuál es la fuente real antes de clonar el realm | [[keycloak-configuracion]] |
| Mismatch de routing key: dispatcher publica `events-domains.*` (con "s"), binding declara `events-domain.#` (sin "s") | `RabbitMQDispatcher` / `RabbitMQConfig` | Alta si se usa Rabbit como dispatcher activo — mensajes no llegarían a la cola | [[eventos-de-dominio]] |
| `event_persister_hexagon` tiene el pipeline completo pero los dos listeners de entrada están comentados/desactivados — no persiste eventos hoy | `EventListenerRabbitMQ`, `EventListenerSpring` | Alta — no asumir auditoría de eventos funcionando | [[event-persister-outbox]] |
| `EventPersisterService` crea un `ObjectMapper` nuevo por llamada en vez de reutilizar el bean configurado | `event_persister_hexagon` | Baja (ineficiencia, no bug funcional) | [[event-persister-outbox]] |
| Handler de validación en el advice "global" retorna `ClientErrorResponse` (tipo de un hexágono específico), no el `GlobalErrorResponse` genérico | `bootstrap/exception/GlobalControllerAdvice` | Media — el contrato de error no es realmente uniforme. **Ya corregido en MalphasOS** | [[manejo-global-excepciones]] |
| **El handler de `DataAccessException` devuelve `ex.getMessage()` al cliente**, exponiendo nombres de tablas, columnas y fragmentos de SQL en la respuesta HTTP | `bootstrap/exception/GlobalControllerAdvice` | Alta — divulgación de información interna. **Ya corregido en MalphasOS**: va al log, no a la respuesta | [[manejo-global-excepciones]] |
| **`KeycloakRoleConverter` hace casts sin verificar** sobre los claims del token (`(Map<String,Object>)`, `(List<String>)`): un token con estructura inesperada lanza `ClassCastException` en pleno filtro de seguridad | `bootstrap/config/security` | Media — el token es entrada externa no confiable. **Ya corregido en MalphasOS** con pattern matching | [[seguridad-keycloak-backend]] |
| `CLIENT_ID = "sigma-api"` hardcodeado en el converter de roles | `bootstrap/config/security/KeycloakRoleConverter` | Baja — impide reutilizar la clase sin editarla. **Ya corregido en MalphasOS** | [[seguridad-keycloak-backend]] |
| Los códigos del catálogo de errores no coinciden con su propio javadoc (`ERR_DATABASE_001` documentado como `ERR_DATABASE_003`) | `bootstrap/exception/utils/GlobalErrorCatalog` | Baja | [[manejo-global-excepciones]] |
| OpenAPI declara licencia **MIT**, pero es metadata copiada: verificar siempre la licencia real del proyecto al portar | `bootstrap/config/open_api/OpenApiConfig` | Baja | [[openapi-swagger]] |
| **El endpoint de creación de personas falla siempre**: su DTO no lleva `tipoPersona` pese a alimentar una columna `NOT NULL` | `PersonCreateRequest` | Alta — endpoint inoperante. **Corregido en MalphasOS** | [[migracion-person-hallazgos]] |
| **`PersonService` depende de `PersonIdentityAdapter`**, la clase concreta, en vez del puerto. `PersonIdentityPort` existe y nadie lo usa | `person_hexagon/application/service` | Alta — anula la arquitectura hexagonal. **Corregido en MalphasOS** | [[migracion-person-hallazgos]] |
| **`validar_roles_persona()` es código muerto**: la función existe pero ningún `CREATE TRIGGER` la asocia a la tabla, así que sus dos reglas nunca se ejecutaron | esquema SQL | Alta — reglas de negocio inexistentes en la práctica. **Trasladadas al dominio en MalphasOS** | [[migracion-person-hallazgos]] |
| **El adaptador de persistencia mapea colecciones perezosas fuera de transacción**; solo funciona porque `open-in-view` viene activo por omisión | `PersonPersistenceAdapter` | Alta al desactivar esa opción. **Corregido en MalphasOS** | [[antipatron-open-in-view]] |
| **El `Response` de JAX-RS nunca se cierra** al crear usuarios en Keycloak | `PersonIdentityAdapter` | Media — fuga de conexiones. **Corregido en MalphasOS** | [[migracion-person-hallazgos]] |
| **El `switch` de grupos de Keycloak no tiene caso por defecto**: un `RoleType` nuevo crearía usuarios sin ningún grupo y sin aviso | `PersonIdentityAdapter` | Media. **Corregido en MalphasOS** con una expresión switch | [[migracion-person-hallazgos]] |
| El advice del módulo captura `Exception` y devuelve `ex.getMessage()` al cliente | `PersonGlobalControllerAdvice` | Media — divulgación de información. **Corregido en MalphasOS** | [[migracion-person-hallazgos]] |
| `KeycloakUnauthorizedException` responde **401** al llamante, cuando quien carece de permisos es el servicio frente a Keycloak | `PersonGlobalControllerAdvice` | Media — culpa al cliente de una mala configuración del servidor | [[migracion-person-hallazgos]] |
| Las tres rutas de registro cuelgan de versiones distintas del API: `/vi/` (typo), `/v1/` y `/v2/` | `PersonRestAdapter` | Media | [[migracion-person-hallazgos]] |
| `n_segundo_apellido` es obligatorio en la entidad JPA pero anulable en el esquema | `PersonEntity` | Media — rechaza a quien tiene un solo apellido | [[migracion-person-hallazgos]] |
| `b_estado_activo` de `persona` está declarada `varchar(50)` siendo booleana, a diferencia de las otras dos tablas del módulo | esquema SQL | Baja. **Corregido en MalphasOS** | [[esquema-bd-v4]] |
| `PersonCommunicationPort`, un puerto de la capa de aplicación, importa tipos de `infrastructure` | `person_hexagon` | Media — invierte la dirección de las dependencias | [[migracion-person-hallazgos]] |
| **El Dockerfile ejecuta la aplicación como root** y usa `eclipse-temurin:21` (JDK completo) como imagen de runtime en vez de un JRE | `Backend/sigma-bb/Dockerfile` | Media — privilegios innecesarios y superficie de ataque mayor. **Ya corregido en MalphasOS** | [[dockerfile-y-contenedores]] |
| No existe `.dockerignore` en el backend: el contexto de build incluye `target/`, `.git/` y cualquier `.env` presente | `Backend/sigma-bb/` | Media — riesgo de filtrar secretos a una imagen. **Ya corregido en MalphasOS** | [[dockerfile-y-contenedores]] |
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
