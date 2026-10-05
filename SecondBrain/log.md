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

## [2026-08-28] ingest | Realm de Keycloak y activación de la seguridad

El realm se adaptó **transformando el export original** en lugar de reescribirlo, de modo que se conservan los 21 flujos de autenticación, los 14 client scopes, los tres clients con sus roles y los permisos del service account.

**Bug crítico encontrado en el realm original**: `admin.full` está definido como rol pero **no asignado a ningún grupo**, y es exactamente el permiso que exigen todos los controladores. Con esa configuración, cualquier usuario recibe 403 en todos los endpoints: la API es inutilizable. `client.delete` y `super.admin.full` también quedaban huérfanos.

**Segundo hallazgo**: el realm fija `attributes.frontendUrl` en `http://keycloak:8080`, un valor que anula `KC_HOSTNAME` y que ningún navegador puede resolver. Con él, autenticarse desde fuera de Docker es imposible.

**Un error propio corregido.** Al contenerizar cambié `KC_HOSTNAME` de `keycloak.test` a `localhost` para no obligar a editar `/etc/hosts`. Eso rompió la consistencia del emisor: el token se firma con la URL pública y el backend lo validaba contra el nombre interno del servicio, devolviendo 401 con tokens válidos. Lo verifiqué empíricamente antes de asumirlo. La solución no fue revertir sino separar `issuer-uri` (público, el del claim `iss`) de `jwk-set-uri` (interno, de donde bajan las claves). Nota nueva: [[issuer-uri-vs-jwk-set-uri]].

También se endureció el realm: protección contra fuerza bruta activada con bloqueo a los cinco intentos en vez de treinta, política de contraseñas, y el client público del frontend deja de ofrecer el flujo de contraseña directa que no necesita al usar PKCE.

64 pruebas en verde, seis de ellas nuevas en `SecurityIntegrationTest`. Verificado contra la aplicación contenerizada: sin token responde 401, con un token real de Keycloak sin permisos responde 403, y tras asignar `admin.full` responde 200.

Queda pendiente una decisión: los quince roles granulares del realm describen un control de acceso fino por recurso, pero el código solo comprueba `admin.full`. El modelo existe y nunca se aprovechó.

## [2026-08-28] ingest | Login desde Swagger y un hueco de validación que encontró una prueba manual

Se configuró el inicio de sesión de Keycloak desde Swagger UI con Authorization Code y PKCE, en lugar del esquema bearer que obligaba a pegar un token caducable a mano. El realm suma la URL de retorno de Swagger y un usuario de desarrollo `dev.admin` dentro del grupo `admins`, porque no traía ninguno con el que iniciar sesión. Su contraseña queda en el archivo del realm: aceptable para desarrollo local, nunca para un entorno desplegado.

**Al probar la aplicación a mano apareció un defecto que las 64 pruebas automáticas no detectaban.** Una petición con `tipoPersona: MANAGER` y `segundoTipoPersona: ADMIN` respondía 500 con un escueto "Database error".

La causa fue un error propio, no heredado: al trasladar las reglas de combinación de tipos al dominio se portaron las dos de la función `validar_roles_persona()` pero se pasó por alto una tercera, que vivía en la restricción `CHK_segundo_tipo_persona` de la tabla. El dato inválido atravesaba el dominio, la base lo frenaba, y el cliente recibía un error de servidor sin explicación.

Nota nueva: [[reglas-de-negocio-en-el-esquema]], con los seis sitios donde un esquema SQL esconde reglas de negocio y cómo comprobar que no queda ninguna al migrar un módulo. Es directamente aplicable a `client` y `equipment`, que aún están por migrar.

Se corrigió además un defecto del original que quedaba tapado por el mismo síntoma: **toda violación de integridad se reportaba como 500**, de modo que una cédula duplicada —un error del cliente— se presentaba como fallo del sistema. Ahora responde 409 con código propio, y el detalle técnico va al log.

La lección que deja: las pruebas escritas por quien migra verifican lo que entendió, no lo que existe. Una regla que nadie identificó no aparece en ninguna prueba. De ahí el valor de ejercitar la aplicación a mano contra datos reales.

66 pruebas en verde, dos de ellas nuevas: una para el caso concreto y otra que recorre todos los valores del enum, de modo que agregar uno nuevo obligue a decidir si es válido como tipo secundario.

## [2026-08-29] ingest | Dos defectos heredados que la lectura del código no vio

Cierre del módulo `person`. Los dos defectos que quedaban los destapó la misma prueba manual —intentar registrar un ingeniero desde Swagger— con las 68 pruebas automáticas en verde. Ninguno de los dos era inventado en la migración: ambos existen también en el original, se portaron fielmente y por eso pasaron desapercibidos.

**El adaptador de identidad estaba traducido a medias.** `createUser` interpretaba los códigos HTTP de la respuesta de Keycloak, pero no capturaba nada. El cliente puede fallar **antes** de entregar respuesta —si no consigue autenticarse contra la Admin API o no alcanza el servidor lanza en vez de devolver un código—, y esa excepción escapaba hasta el servlet como un 500 con el cuerpo por defecto de Spring, fuera del contrato de errores del API. `deleteUser`, en el mismo archivo, sí lo contemplaba: la inconsistencia vivía dentro de una sola clase.

Lo que hace el caso interesante es que **la causa real viaja envuelta**: el cliente JAX-RS mete el fallo dentro de un `ProcessingException` y el código HTTP queda en alguna causa más abajo, de modo que hay que recorrer la cadena para distinguir un 401 de un DNS caído. Y la traducción obliga a decidir de quién es la culpa: cuando el servicio no logra autenticarse contra su dependencia, la respuesta correcta es **502**, no 401 — el llamante no tiene nada que corregir.

Nota nueva: [[traduccion-de-fallos-de-adaptadores]], que generaliza las dos formas en que falla un adaptador de salida y cómo probar la que nadie prueba. Aplica directamente a `client` y `equipment`, aún por migrar.

**Los secretos de los clients confidenciales eran la máscara del export.** Keycloak no exporta los secretos: escribe `"secret": "**********"`. El problema es que **al reimportar toma esa máscara literalmente como el valor real**, de modo que ambos clients quedaron con una credencial trivial y, además, con aspecto de estar oculta al mirarla en la consola, que enmascara exactamente igual. Verificado: los dos `realm-export.json` del original lo traen así, luego cualquiera que reconstruya ese entorno desde el export obtiene la misma credencial conocida.

Se optó por fijar secretos de desarrollo explícitos, cuyo propio nombre advierte que no sirven fuera de local, en vez de eliminar el campo para que Keycloak genere uno aleatorio: lo segundo es más seguro pero obliga a copiar el secreto a mano cada vez que se recrea el contenedor, y este realm ya es explícitamente de desarrollo.

Un detalle menor del mismo trabajo: `@Valid` estaba sobre las listas de contactos en vez de sobre su argumento de tipo (`List<@Valid Email>`), forma que Hibernate Validator acepta pero marca como obsoleta, llenando el log en cada petición.

Actualizadas [[keycloak-configuracion]], [[migracion-person-hallazgos]] —con una categoría nueva para los defectos heredados que sobreviven a la revisión—, [[deuda-tecnica-y-riesgos]], [[decisiones-tecnicas-malphasos]] y [[checklist-reutilizacion]], donde `person` queda cerrado. Verificado extremo a extremo: el registro responde 201, crea el usuario en Keycloak con el mismo identificador que la persona, lo asigna al grupo según su rol, y un nombre repetido responde 409. 68 pruebas en verde, dos nuevas que cubren el fallo de autenticación y el de red del adaptador.

La lección que deja el módulo completo, y que conviene tener presente al empezar el siguiente: **de los seis defectos que encontró la ejecución y no la lectura, la mitad estaba en el original y se leyó sin verla**. Los dos de hoy salieron de archivos que ya se habían revisado y corregido: en el adaptador se arreglaron cuatro cosas y se pasó por alto una quinta a diez líneas; en el realm, cinco de seis. Revisar buscando defectos conocidos no equivale a revisar entero, y la atención se agota en lo que se fue a buscar.

## [2026-08-29] ingest | El wiki afirmaba una ambigüedad que no existe

Exploración de `client_hexagon` como preparación para migrarlo. El hallazgo principal es una **corrección a este wiki**, no al código original.

La nota `relacion-cliente-persona-ambiguedad` sostenía que la relación entre `Manager` y `Person` era una decisión de diseño sin resolver, y que el vínculo era "posible pero no confirmado". **Es falso.** La relación está decidida e implementada en tres capas independientes:

1. **El esquema**, con identidad compartida: `encargado.k_identificador` es a la vez PK de la tabla y FK a `persona`. Que la PK *sea* la FK distingue "un encargado **es** una persona" de "tiene una".
2. **El servicio**: `HeadquarterService.addManagerLogicGetUUID()` crea la persona vía `PersonCommunicationPort` con `tipoPersona = MANAGER` y usa el UUID devuelto como identificador del encargado.
3. **El DTO**: `ManagerUseCaseRequest` transporta `cedula`, nombres, correos y teléfonos — campos de persona.

El error vino de revisar solo los archivos de dominio, que es justamente la única capa donde la relación **no** aparece. La conclusión práctica se invierte: no hay una decisión de diseño que tomar antes de migrar `client`, hay una relación que hacer explícita en el modelo. Lo que sigue abierto es cómo expresarla —`@MapsId` conservando la forma del esquema, o absorber el rol dentro de `Person`—, que es una pregunta bastante más pequeña.

La nota se renombró a [[relacion-manager-persona]], se reescribió con la corrección declarada arriba, y se ajustó el encuadre en las siete notas que la citaban dándola por ambigua.

**Deja una lección sobre este wiki**: una afirmación negativa —"no existe relación"— no se puede sostener revisando una sola capa. El dominio decía la verdad sobre sí mismo y mentía sobre el sistema.

Otros hallazgos de la exploración, agregados a [[deuda-tecnica-y-riesgos]]: `tipoEncargado` es un `String` cuyo javadoc documenta dos valores que la restricción `CHK_tipo_encagado` rechaza; `encargado` tiene `k_id_sede` y `k_id_area_servicio` ambas anulables sin nada que fuerce cuál corresponde según el tipo; y `Manager` carece de puertos propios, gestionándose a través de los servicios de sede y área. Los dos primeros los encontró aplicar [[reglas-de-negocio-en-el-esquema]], que para eso se escribió.

**El prerequisito real para migrar `client`**: `HeadquarterService` y `ServiceAreaService` dependen de `PersonCommunicationPort`, el puerto que quedó aplazado al migrar `person` por importar tipos de `infrastructure`. Sin resolverlo, `client` no arranca. Anotado en [[checklist-reutilizacion]].

## [2026-08-29] ingest | El modulo que este wiki llamaba ejemplar, visto por dentro

Tres pasos de construcción de una vez: el contrato publicado de `person` hacia otros módulos, el contrato de eventos de dominio, y el módulo de ubicaciones —esquema y dominio—. 130 pruebas en verde.

**El hallazgo que obliga a corregir este wiki.** [[dominio-ubicacion]] describía `location_hexagon` como *"el mejor ejemplo pedagógico de cómo se ve el patrón completo"*, y [[aggregate-root-pattern]] daba la pieza compartida por `reusable:alta` **"tal cual, sin cambios"**. La forma sí es ejemplar y es la que se está replicando. El código traía cuatro defectos:

Los agregados llevaban `@Data`, que genera un setter público por campo: se podía renombrar un país sin emitir evento y sin pasar por validación alguna, que es exactamente lo contrario de aquello para lo que un agregado existe. Tenían `@EqualsAndHashCode(callSuper = true)` sobre una superclase que no redefine `equals`, de modo que **dos países con datos idénticos nunca resultaban iguales** ni coincidían en un `HashSet`. `updateCountryPatch` escribía `"country.patch"` en la metadata mientras construía un `CountryUpdatedEvent`, así que un consumidor que filtrara por tipo y otro que filtrara por clase veían cosas distintas. Y `deleteCountry()` no modificaba estado alguno: el estado activo ni siquiera existía en el modelo, vivía solo en la columna.

Aparte, `City.createIdFromName` derivaba la llave primaria de las **dos primeras letras del nombre**. Bogotá y Boyacá producen ambas `"BO"`. La segunda ciudad que empezara igual chocaba contra la llave de la primera.

Nota nueva: [[migracion-location-hallazgos]]. Y las dos notas que lo idealizaban quedan corregidas con la constancia al principio, como pide el schema.

**La lección, que es la misma que dejó la corrección de [[relacion-manager-persona]] hace dos entradas**: un módulo puede tener la forma correcta y la implementación equivocada. Este wiki lo había leído por su estructura, que es lo que salta a la vista al abrir los archivos, y no por su comportamiento. De ahí que `reusable:alta` merezca un matiz que no tenía: **alta para el patrón, no necesariamente para el código**.

**Del contrato de eventos** salió otra fuga: `EventMetadata` llevaba un campo `eventTopic` donde el dominio escribía `"events-domain"`, el nombre del exchange de RabbitMQ —un detalle de transporte dentro del modelo, duplicando el valor que el despachador ya tenía como constante—. El campo desaparece. Se retira también `Serializable`, tras verificar que el `RabbitMQConfig` del original usa `JacksonJsonMessageConverter` y la serialización de Java no interviene en ningún punto. Y no se porta el despachador de Rabbit: no hay consumidor, su clave de publicación no casa con su propio binding, y los listeners que lo consumirían están desactivados.

**Dos decisiones tuyas quedan registradas en [[decisiones-tecnicas-malphasos]]**, y una de ellas invalida el orden que este wiki daba por bueno:

`location` va **antes** que `client`. La causa es dura: `sede.k_id_ciudad` es `NOT NULL` y apunta a `ciudad`, que a su vez necesita `pais`. Sin las tablas de ubicación la migración de clientes no se puede ni escribir. El orden anterior queda tachado en la nota, no borrado.

`representante_legal` se porta y se implementa. Es una tabla que descubrimos explorando el esquema: vincula persona y cliente por identidad compartida, igual que `encargado`, y **no tiene una sola línea de código en el original** —la única aparición del nombre en todo el backend es un `example` dentro de un javadoc—. Es lo que le falta al tipo `CEO_CLIENT`, que existe en el enum, en el catálogo de la base y en los grupos del realm, para poder asociarse a algún cliente. El modelo de datos promete algo que el sistema no hace.

Actualizadas [[aggregate-root-pattern]], [[dominio-ubicacion]], [[eventos-de-dominio]], [[dominio-cliente]], [[relacion-manager-persona]], [[deuda-tecnica-y-riesgos]] —ocho filas nuevas, 53 en total—, [[decisiones-tecnicas-malphasos]] y [[checklist-reutilizacion]].

## [2026-08-29] ingest | Location cerrado, y un agujero de seguridad en el modulo que nadie miraba

Aplicación, persistencia y REST de ubicaciones. Con esto `location` queda completo de punta a punta y `client` se queda sin bloqueos. 164 pruebas en verde.

**El hallazgo que más pesa es de seguridad.** `location_hexagon` **no tiene una sola anotación de autorización en ningún archivo**, frente a los once archivos con `@PreAuthorize` de `person` y `client`. Bastaba un token válido de cualquier usuario —cualquier ingeniero, cualquier encargado de cliente— para crear o borrar un país, que es la tabla de referencia de la que cuelgan clientes, ciudades y fabricantes. Pasa a exigir `admin.full` en las diez operaciones.

Y el borrado era **físico**: `deleteById`, pese a que la tabla tiene `b_estado_activo` y a que el resto del sistema usa borrado lógico. El agregado emitía un evento diciendo "deleted" mientras la fila desaparecía de verdad. Sobre `pais` habría chocado además con las claves foráneas de tres tablas.

Los otros dos: el `PUT` recibía el identificador por la ruta y construía el comando con el del cuerpo, pasando el de la ruta al servicio por separado, sin que nada comprobara que coincidían; y `PATCH` no validaba la petición mientras `PUT` sí.

**Un hallazgo con consecuencias más allá de este módulo**: MapStruct no sirve para construir un agregado de Generación 2. Construye el destino por setters o por builder, y un agregado no ofrece ninguno de los dos a propósito —se entra por `create`, que registra un evento, o por `rehydrate`, que no—. Generar el mapeo exigiría abrir justo la puerta que el agregado cierra. Los mappers de persistencia van a mano, y lo mismo aplicará a `client` y `equipment`. Registrado en [[patron-mapper-mapstruct]] con la regla completa por dirección.

**Queda resuelta una pendiente que este wiki arrastraba**: si el manejo de excepciones comparte una base entre módulos o se repite por contexto. Al escribir el segundo catálogo hubo que decidir, y se optó por repetir. Cada contexto acotado es dueño de su contrato de error; lo que se duplica es la forma, no el comportamiento. El coste queda anotado en [[manejo-global-excepciones]]: cuando llegue `client` habrá una tercera copia, y si algún día hay cinco sin que ninguna diverja, la decisión merecerá revisarse.

Un apunte de método, porque se repitió el patrón de las entradas anteriores. Los cuatro defectos del dominio salieron leyendo el código; **los cuatro de las capas de fuera salieron al ejecutarlo o al compararlo con lo ya migrado**. El de autorización apareció por preguntarse por qué `location` no tenía los `@PreAuthorize` que `person` sí llevaba: no se ve leyendo un archivo, se ve comparando dos módulos.

Actualizadas [[migracion-location-hallazgos]], [[patron-mapper-mapstruct]], [[deuda-tecnica-y-riesgos]] —cuatro filas nuevas, 57 en total—, [[manejo-global-excepciones]], [[dominio-ubicacion]], [[decisiones-tecnicas-malphasos]] y [[checklist-reutilizacion]]. La lista de pendientes de decidir baja a uno.

## [2026-08-30] ingest | Esquema de clientes, y una relacion que este wiki leyo mal

`V4__client.sql`: siete tablas —cliente con sus correos y teléfonos, sus representantes legales, sus sedes, las áreas de servicio de cada sede y los encargados de unas y otras—. 177 pruebas en verde.

**Corrección.** La entrada del 2026-08-29 y la nota [[relacion-manager-persona]] afirmaban que `representante_legal` vinculaba persona y cliente *"exactamente con el mismo patrón de identidad compartida"* que `encargado`. Es falso. Su llave primaria es **compuesta**, `(k_identificador, k_id_cliente)`, de modo que la relación es de muchos a muchos: una persona puede representar a varios clientes y un cliente tener varios representantes.

El error vino de mirar los nombres de las columnas —`k_identificador`, FK a persona, igual que en `encargado`— sin leer la llave primaria, que es justamente donde se decide la cardinalidad. Dos tablas con las mismas columnas y llaves distintas son dos relaciones distintas. La nota queda corregida con una tabla comparativa, y la diferencia importa al modelar: `encargado` se expresa con `@MapsId` sobre `Person` y `representante_legal` no puede, porque su identidad no es la de la persona.

**La restricción que más aporta al esquema nuevo** es la de asignación del encargado. El original dejaba `k_id_sede` y `k_id_area_servicio` ambas anulables, con un `t_tipo_encargado` que declaraba de qué se encargaba y nada que atara una cosa a la otra: cabía un `HEADQUARTER` sin sede, con área, con las dos o con ninguna. El tipo decía una cosa y las claves foráneas otra.

`equipo_cliente` no entra: su clave foránea apunta a `modelo`, que pertenece al módulo de equipos y arrastra también `equipo` y `fabricante`. Hay una prueba que fija que la tabla todavía no existe, para que no se olvide.

El resto de correcciones: llave UUID en `cliente` con el NIT como llave natural, igual que se hizo con el código ISO del país; correos y teléfonos que exigen dueño; unicidad de sede por cliente y de área por sede, que no existían; las tres columnas de dirección renombradas al prefijo del esquema; y dos nombres de restricción con letras traspuestas.

**Nueva sección en [[deuda-tecnica-y-riesgos]]: "Deuda propia de MalphasOS".** Hasta ahora esa nota solo recogía defectos del original, y conviene no mezclar. Estrena una entrada nuestra: en `V2__person.sql` dejamos anulable el dueño de los correos y teléfonos de una persona, y en `V4__client.sql` decidimos lo contrario para los del cliente. Los dos módulos hacen cosas distintas con el mismo problema; corregirlo exige una migración propia.

## [2026-09-02] ingest | Client cerrado: el primer modulo que se reconstruye en vez de portarse

Esquema, cuatro agregados, aplicación, persistencia y REST. Con esto `client` queda completo salvo `equipo_cliente`, y solo falta `equipment`. 275 pruebas en verde.

**Es el primer módulo que no se porta.** `person` y `location` se migraron corrigiendo defectos sobre la estructura del original; aquí el original es Generación 1 —CRUD anémico, sin agregados ni eventos— y este wiki lo marca como patrón a no replicar. Se conservó la jerarquía conceptual y se rehízo todo lo demás.

**La decisión que dio forma a todo** fue elegir cuatro agregados pequeños que se referencian por identificador, en vez de un `Client` que contuviera sus sedes, sus áreas y sus encargados. Cargar un cliente no arrastra su organización entera, y dos personas editando sedes distintas no compiten por la misma fila. El coste, asumido: ninguna transacción abarca cliente y sede a la vez.

**`representante_legal` se implementó por primera vez.** La tabla existía en el esquema original sin una sola línea de código —la única aparición del nombre en todo el backend era un `example` dentro de un javadoc—, y era justo lo que le faltaba al tipo `CEO_CLIENT` para poder asociarse a algún cliente. El modelo de datos prometía algo que el sistema no hacía.

**Aparecieron las primeras reglas que ningún esquema puede expresar.** No se abre un área en una sede cerrada, y no se pone a nadie al frente de algo cerrado. Una clave foránea comprueba que una fila exista, no que esté activa, y con borrado lógico esas dos cosas dejan de ser la misma. Viven en los servicios, con sus pruebas, y el patrón se repetirá en `equipment`.

**El original siempre creaba una persona nueva** al añadir un encargado, de modo que un ingeniero de la empresa no podía figurar además como encargado sin duplicarse. Ahora hay dos caminos: `register` crea la persona, `assign` parte de alguien que ya existe. Y `register` valida el destino **antes** de crear la persona: al revés, un fallo dejaría una persona huérfana en la base.

**Es también el primer módulo que habla con otros dos**, `person` para el representante legal y el alta de encargado, `location` para la ciudad de la sede. Eso obligó a que su advice traduzca `CityNotFoundException` y `PersonNotFoundException`, que si no escaparían al manejador transversal como un 500.

Nota nueva: [[migracion-client-hallazgos]], con los ocho defectos del original corregidos y el detalle de las decisiones.

**Dos entradas nuevas en la deuda propia.** La primera incomoda: **la batería es intermitente**. Una ejecución dio 3 errores por la comprobación de salud de RabbitMQ contra `localhost:5672`, y la siguiente, sin tocar nada, dio 251/251. Una batería que da dos resultados distintos deja de servir como señal; conviene desactivar esa comprobación en el perfil de pruebas, que ningún test usa la mensajería. La segunda es el aplazamiento consciente de `equipo_cliente`.

Actualizadas [[dominio-cliente]], [[decisiones-tecnicas-malphasos]], [[deuda-tecnica-y-riesgos]] y [[checklist-reutilizacion]], donde `client` queda cerrado y `equipment` pasa a ser lo único pendiente.

## [2026-09-02] ingest | Equipment cerrado en cinco pasos: termina la migracion del backend

Esquema, seis agregados, aplicación, persistencia y REST. Con esto **los cuatro módulos de dominio están completos** y la migración del backend termina, salvo la segunda tanda de `equipment`. 338 pruebas en verde.

Este wiki llevaba cinco pasos de retraso: su última entrada era el cierre de `client`, y ni el esquema de equipos ni sus agregados ni sus tres capas de fuera estaban registrados. Esta entrada los cubre todos.

**El hallazgo de modelado que más aporta está en `EquipmentType`, y es un booleano que sobraba.** El original tenía `b_verificable` como campo y `n_tipo_verificacion` como columna, sin nada que los atara y sin que el dominio modelara siquiera la segunda: cabía un tipo marcado como verificable del que nadie sabía cómo se verifica. Ahora `isVerificable()` se **deriva** de si consta la modalidad —un tipo es verificable exactamente cuando se sabe cómo verificarlo—, un `CHECK` lo respalda desde el esquema, y el mapper de persistencia deriva el booleano al guardar y lo ignora al leer. La misma invariante defendida en tres capas, y el estado inconsistente deja de ser expresable. Es el ejemplo más limpio del proyecto de qué significa quitar un campo en vez de validarlo.

**La tabla `equipo` no guarda un equipo.** No tiene un solo atributo propio más allá de sus dos claves foráneas: es la asociación marca↔tipo, es decir qué tipos fabrica cada marca. Se conserva el nombre para no divergir del original, pero se documenta en el esquema, en el agregado y en el controlador. Y sus dos referencias son **inmutables**: el original ofrecía `updateEquipment` y `updateEquipmentPatch`, que habrían convertido en mentira todos los modelos colgados de esa asociación. No hay operación de cambio, y una prueba fija el 405 **como contrato, no como omisión**.

**Un patrón de método que vale la pena repetir.** `V4__client.sql` dejó `equipo_cliente` fuera a propósito, y para que no se olvidara `ClientSchemaTest` llevaba una prueba que fijaba que la tabla *no existía*. Al aparecer, esa prueba se rompió — exactamente lo que se le pedía— y se retiró. Una omisión consciente marcada con una prueba que falla cuando deja de ser una omisión envejece mucho mejor que un comentario que nadie relee.

**Se invierte por primera vez la dirección entre módulos.** Hasta ahora `client` consultaba a `person` y a `location`; ahora `equipment` consulta a `client` por el área de servicio. No hay ciclo, porque `client` no conoce a `equipment`. Y con tres invariantes nuevas del tipo "está activo, no solo existe" —modelo sobre asociación retirada, unidad de modelo retirado, equipo en área cerrada— ya van **cinco** en el proyecto, todas en los servicios porque ninguna clave foránea puede encargarse de ellas.

Seis defectos del esquema original corregidos, entre ellos un `d_amperaje numeric(2)` que redondeaba 2.5 A a 3 y no admitía 120 A, cuatro columnas anulables que dejaban entrar modelos que no pertenecían a nada y unidades de las que no se sabía qué eran ni dónde estaban, y la ausencia total de unicidad en las cinco tablas del catálogo.

**Lo que apareció al revisar la capa REST antes de commitearla**, y que compilaba y pasaba las 337 pruebas:

Los grupos de OpenAPI **no casaban con las rutas**. `OpenApiConfig` se escribió al portar la configuración transversal, mucho antes que estos controladores, y pedía `/equipment`, `/equipment-models` y `/client-equipment` cuando las rutas son `/equipments`, `/models` y `/client-equipments`. Tres de los seis recursos no aparecían en ningún grupo de Swagger. **El fallo no avisa**: un patrón que no casa con ninguna ruta no es un error, simplemente deja el recurso fuera. Al buscar más instancias apareció una cuarta, `/v1/api/managers`, que el grupo `client` nunca declaró y que **sigue pendiente**. Registrado en [[openapi-swagger]] con la lección de método: si el grupo se declara antes que el controlador, la única forma de saber que casa es una prueba contra el documento generado.

Tres `PUT` donde la convención dice `PATCH`, en rutas de sub-recurso. Eran defendibles —reemplazan del todo y son idempotentes—, pero dos verbos con la misma semántica repartidos según quién escribiera cada controlador cuestan más que la precisión del matiz.

Y un código de error que servía para dos cosas incompatibles: un área inexistente respondía **404** con `ERR_EQUIPMENT_007`, "Invalid equipment data", el mismo código que sale con **400** cuando una regla rechaza la petición. Ahora cada referencia externa lleva código propio.

**Corrección a lo que se dijo durante esa revisión.** Se afirmó que `client` daba código propio a cada referencia externa y que `equipment` se desviaba del patrón. Es falso: `ClientControllerAdvice:62` agrupa `CityNotFoundException` y `PersonNotFoundException` bajo `INVALID_CLIENT_DATA` exactamente igual. No era una desviación de `equipment` sino un defecto del patrón entero, y `client` **sigue sin corregir**. Anotado como deuda propia.

**Y una corrección al propio checklist**, que arrastraba contradicciones internas desde hacía tres pasos: daba por pendientes `shared/domain/events`, el dispatcher, el primer hexágono de Generación 2 y la jerarquía de clientes, cuatro cosas que sus propias entradas de la sección 5 declaraban hechas. Quedan marcadas con su fecha real. El dispatcher se marca **parcial**, que es lo cierto: `SpringEventDispatcher` está en marcha y `RabbitMQDispatcher` no se ha portado.

Nota nueva: [[migracion-equipment-hallazgos]]. Actualizadas [[dominio-equipo-mantenimiento]] —que pasa a advertir que describe el original y no lo que MalphasOS hace—, [[openapi-swagger]], [[decisiones-tecnicas-malphasos]] con nueve decisiones del módulo, [[deuda-tecnica-y-riesgos]] —siete defectos nuevos del original, 63 en total, y tres entradas nuevas de deuda propia— y [[checklist-reutilizacion]], donde la migración del backend queda cerrada salvo verificaciones técnicas y datos metrológicos.

## [2026-09-02] ingest | Las dos deudas que abrio la revision de equipment, cerradas el mismo dia

Dos correcciones en `client`, salidas de revisar la capa REST de `equipment` unas horas antes. 339 pruebas en verde.

**`/v1/api/managers` no aparecía en ningún grupo de OpenAPI.** El grupo `client` declaraba sedes, áreas y clientes, y nunca declaró encargados. Era la **cuarta** instancia del mismo fallo silencioso, tras las tres de `equipment`, y apareció justamente al ir a buscar más casos de aquellas: es la única forma en que este fallo se encuentra, porque nunca se manifiesta solo —no hay error de arranque, no hay log, el recurso simplemente no está en Swagger—.

Cada módulo lleva ahora una prueba que consulta su propio `/v3/api-docs/<grupo>` y exige que aparezcan todos sus recursos. **Los cinco grupos del proyecto casan hoy con las rutas reales**, y esta vez comprobado por una prueba y no leyendo dos listas en paralelo, que es exactamente lo que falló cuatro veces.

**Los códigos de error de `client` no distinguían "no existe" de "datos inválidos".** Una ciudad o una persona inexistentes salían como 404 con `ERR_CLIENT_005`, *"Invalid client data"*, el mismo código que sale con 400 cuando una regla rechaza la petición. Ahora llevan código propio, `006` y `007`.

Con esto los dos módulos que hablan con otros tratan sus referencias externas de la misma forma, y la regla queda enunciable sin excepciones: **un código de "no existe" no se comparte nunca con uno de "datos inválidos"**, porque salen con estados HTTP distintos y un cliente que solo mire el código no podría separarlos.

**Sobre cómo aparecieron las dos.** Ninguna la encontró leer el módulo donde vivían. La primera salió de preguntarse si el fallo recién corregido en `equipment` tenía más instancias; la segunda, de verificar una afirmación que se había hecho a la ligera —que `client` ya daba código propio a cada referencia externa— y descubrir que era falsa. Es el mismo patrón que ya registraron [[migracion-location-hallazgos]] y [[migracion-client-hallazgos]]: **los defectos de las capas de fuera aparecen comparando módulos entre sí, no leyendo uno solo**.

Actualizadas [[deuda-tecnica-y-riesgos]] —dos entradas de deuda propia cerradas, quedan tres—, [[openapi-swagger]], [[migracion-equipment-hallazgos]] y el `CONVENCIONES.md` de la raíz, cuyas convenciones REST ganan las dos reglas aprendidas hoy y estrenan una sección de OpenAPI.

## [2026-09-02] ingest | La ERS contrastada contra el codigo: seis defectos que ningun modulo delataba

Primer encargo real al subagente `documentador`: contrastar la sección 3.2 de la ERS contra lo que el código implementa de verdad. Trabajó en `docs/ieee830-alcance-implementado`, sin mergear. **Los seis defectos de código que reportó se verificaron uno a uno antes de anotarlos aquí.**

**El resultado del contraste, en una línea: de 31 requisitos funcionales, 8 están implementados y 23 no tienen una línea de código detrás.** Órdenes de trabajo (7), reportes (6), firma digital (2), inventario de existencias (2), alertas y calibración (2), módulo comercial (2) y dos de hojas de vida. Lo implementado es la gestión de clientes, el registro de equipos y los cinco requisitos de usuarios y seguridad.

**Los dos hallazgos que más pesan son de seguridad, y los dos estaban a la vista sin que nadie los mirara.**

Las **83** operaciones REST exigen `hasAuthority('admin.full')` —83 mappings, 83 anotaciones idénticas— y el realm solo concede ese rol al grupo `admins`. Los grupos `engineers` y `clients` reciben cuatro roles de lectura que **ninguna operación comprueba**. Un ingeniero autentica correctamente y recibe 403 en toda llamada. Es exactamente el defecto que [[keycloak-configuracion]] ya registraba del original —"los 15 roles granulares no se usan"— heredado intacto, y que nadie notó porque todas las pruebas de seguridad usan `admin.full`.

Y **dar de baja a una persona no le quita la entrada**: `PersonService.delete` marca `estadoActivo=false` y guarda, pero `personIdentityPort.deleteUser` solo se invoca desde el rollback de un alta fallida. La cuenta de Keycloak sobrevive y sigue emitiendo tokens válidos. El borrado lógico, que en el resto del sistema es una virtud, aquí deja una puerta abierta: el dato dice inactivo y la identidad dice que pase.

Los otros cuatro: `PersonService.update` no propaga nada a Keycloak; `EquipmentType.update` viola la convención propia de no emitir evento cuando nada cambia, porque solo compara `nombre` contra el valor actual; el NIT duplicado se deja a la restricción del esquema en vez de comprobarse en el servicio, al revés del criterio que el proyecto aplica a las referencias; y quedan seis directorios de andamiaje vacíos en `equipment`.

**Sobre el método, que es lo que hay que retener.** Ninguno de los seis lo habría encontrado leer el módulo donde vive. Aparecieron al preguntarle a un documento qué promete y al código qué cumple — la misma mecánica que ya habían registrado [[migracion-location-hallazgos]] y [[migracion-client-hallazgos]] al comparar módulos entre sí, aplicada ahora entre dos artefactos distintos. **Contrastar una especificación contra su implementación es una técnica de detección de defectos, no solo de documentación.**

**Y una deuda de documentación que no se arregla escribiendo**: los fuentes `.puml` de los diez diagramas de casos de uso **no están en el repositorio** —solo los `.svg` y `.pdf` compilados—, de modo que no se pueden regenerar ni corregir. Varios describen dominios que no existen. Junto con `RF-49` dependiendo de sí mismo y las dependencias a quince requisitos ausentes de la sección, queda todo en [[deuda-tecnica-y-riesgos]].

El `.tex` **estaba sin versionar**: el único commit que había tocado `Documentation/` añadió el PDF y los diagramas y dejó el fuente fuera del `git add`. Ya está en git, con un `.gitignore` propio para los artefactos de LaTeX.

Actualizadas [[deuda-tecnica-y-riesgos]] con dos secciones nuevas —seis defectos de código propio y cinco de la ERS— y el conteo de la batería, que subió a 339 al añadirse la prueba de OpenAPI de `client`.

## [2026-09-02] lint | Los diagramas no estaban perdidos: estaban fuera de main

Limpieza del repositorio antes de abrir un worktree, y salió una corrección que importa.

**Corrección.** La entrada anterior y [[deuda-tecnica-y-riesgos]] afirmaban que **los fuentes `.puml` de los diez diagramas de casos de uso no estaban en el repositorio** y que por tanto los diagramas no se podían regenerar. **Es falso.** Estaban en git desde el principio, en la rama `feat/client-headquarter`, 38 commits por detrás de `main`. Quien lo comprobó miró `main` y el árbol de trabajo —donde efectivamente solo hay `.svg` y `.pdf`— y concluyó lo razonable; el error fue mío al registrarlo sin verificar contra todas las referencias.

La lección se anota en la nota de deuda porque va a volver a pasar: **"no está en el repositorio" y "no está en `main`" no son lo mismo.** Una rama vieja sin mergear puede ser la única copia de algo, y `git log --all --diff-filter=A -- '<patrón>'` responde la pregunta de verdad en un segundo.

Rescatados a `main` los diez `.puml`, sus `.png`, el documento de constitución con sus imágenes, el devcontainer con la configuración de PlantUML que hace falta para compilarlos, y el `CONVENCIONES.md` de `Documentation/`, que resulta ser **el único sitio donde constan el cliente (BolívarBioingeniería LTDA), la duración estimada y el presupuesto del proyecto** — datos que ninguna otra parte del repositorio recoge.

Se rescató por archivo y no mergeando la rama: su `malphasos/` es anterior al módulo `equipment` y mergearla habría borrado 8.267 líneas de código. Quedaron fuera los artefactos de compilación y su `IEEE830.tex`, anterior al marcado de requisitos.

Lo que sigue en pie de esa deuda es lo que no se arregla recuperando archivos: **varios diagramas describen dominios que no existen** —órdenes de trabajo, firma digital, módulo comercial—. Ahora al menos se pueden corregir.

## [2026-09-08] ingest | El modelo de permisos: 83 anotaciones identicas dejaron de serlo

Cierre en el wiki de la tanda del modelo de permisos, construida en `feat/permission-model` (nueve commits, `e68f83e`…`2de115f`) y **todavía sin mergear a `main`**. Todo lo que sigue se verificó contra esa rama, no contra los resúmenes de quien la construyó.

**El defecto, que llevaba meses a la vista.** Las 83 operaciones REST exigían `hasAuthority('admin.full')` y el realm solo se lo daba al grupo `admins`. Los grupos `engineers` y `clients` recibían cuatro roles de lectura que **ninguna operación comprobaba**: autenticaban bien y recibían 403 en toda llamada. Y los dos tenían **exactamente los mismos cuatro**, de modo que un técnico de campo y un cliente externo eran indistinguibles para el sistema. Es el mismo defecto que [[keycloak-configuracion]] ya registraba del original —«los 15 roles granulares no se usan»— heredado intacto al migrar.

**Por qué nadie lo vio: todas las pruebas de seguridad usaban el rol que sí funcionaba.** Una batería que solo recorre el camino del administrador no dice nada sobre los demás perfiles, y no se distingue de una que los cubre todos mirando el porcentaje en verde. Es la misma mecánica que ya registraron [[migracion-location-hallazgos]] y [[migracion-client-hallazgos]]: **los defectos de las capas de fuera aparecen al comparar dos cosas entre sí** —aquí, lo que el realm reparte contra lo que el código comprueba—, no al leer el módulo donde viven.

**Las decisiones que valía la pena registrar**, todas en [[decisiones-tecnicas-malphasos]] y desarrolladas en la nota nueva [[modelo-de-permisos]]:

- **Expandir en un sitio en vez de repetir en 83.** Cada operación nombra solo la autoridad de su recurso. La alternativa, `hasAnyAuthority('admin.full','client.read')` en cada una, deja el modelo repetido 83 veces donde basta olvidar una para abrir un agujero silencioso. Una prueba impide que un controlador vuelva a nombrar al administrador.
- **Dos capas, a propósito.** El converter expande, y un bean `RoleHierarchy` declara lo mismo. No es duplicación: el converter solo interviene cuando la autenticación nace de un JWT que pasa por esa cadena de filtros. El coste —que las dos diverjan— se paga derivando ambas de la misma constante y probándolas por separado.
- **`expand()` añade y nunca quita.** Filtrar lo desconocido habría hecho desaparecer en silencio un rol recién creado en el realm, dentro de un converter, sin log. La pertenencia se decide por igualdad exacta, no por prefijo.
- **`equipment.assign` aparte de `equipment.write`**, porque asignar un equipo cambia quién responde por él, que es lo que un ingeniero hace y un administrativo no. Y **no existe `engineer.write`**: al encargado se le asigna.

**Tres afirmaciones del wiki resultaron falsas al verificarlas, y quedan corregidas con constancia.**

1. [[stack-spring-boot-4-particularidades]] explicaba el desfase entre los dos conteos de Surefire diciendo que **los `.txt` cuentan un `@ParameterizedTest` como una sola prueba**. Es falso: `ApiAuthorityTest` tiene un parametrizado y ningún `@Nested`, y `.txt` y XML dicen 19 los dos. La diferencia sale **entera de las clases con `@Nested`**, cuyo `.txt` dice literalmente `Tests run: 0`. Las dos que existían el 2026-09-02 suman 23 + 9 = **32**, exactamente el desfase que aquella medición atribuyó a los parametrizados. La cifra era correcta; la explicación apuntaba al mecanismo equivocado.
2. [[seguridad-keycloak-backend]] y [[decisiones-tecnicas-malphasos]] afirmaban que **la seguridad está desactivada en `application.yaml` porque el realm no existe todavía**. Dejó de ser cierto el 2026-08-28, cuando el realm se importó: hoy la propiedad es `enabled: ${APP_SECURITY_ENABLED:true}` y solo el `application.yaml` de pruebas la apaga.
3. [[deuda-tecnica-y-riesgos]] cerraba la deuda de OpenAPI del 2026-09-02 con que **«los cinco grupos del proyecto casan hoy con las rutas reales»**. El grupo `reports` apunta a `/v1/api/reports/**` y ese módulo no existe: no casa con nada. `location` y `reports` no tienen prueba de cobertura, y la de `person` solo comprueba que el documento responde 200 sin mirar qué trae. Es justamente el fallo silencioso que esa convención persigue, sobreviviendo dentro de la fila que lo daba por cerrado.

**Sobre el conteo de la batería, medido y no copiado.** Dos ejecuciones completas de `./mvnw test` el 2026-09-08 sobre `feat/permission-model`: **472** por el atributo `tests=` de los XML y **358** por los `.txt`, idénticas las dos veces, 41 clases, cero fallos. En `main` son 339 y 307. Y salió una trampa de método que explica por sí sola la «inestabilidad» que esta nota registraba: **`mvn test` no borra `target/surefire-reports`**. Se plantó a mano un XML falso con `tests="7"`, se corrió otra prueba y el archivo seguía ahí sumando; los informes de otra rama también sobreviven a un `git checkout`. **Al citar un conteo hay que borrar el directorio primero**, o el número no mide una ejecución sino la unión de todas las que pasaron por ahí.

**La intermitencia de la batería estaba mal diagnosticada.** Se atribuía entera a la comprobación de salud de RabbitMQ contra `localhost:5672`. La causa demostrada era otra: el ayudante `unico()` de las pruebas recortaba `nanoTime()` con `substring(0, 10)` y lanzaba cuando el resto tenía nueve dígitos, con **10 % de probabilidad por llamada en `ManagerPersistenceAdapterTest`**. Corregido. Lo de RabbitMQ sigue anotado pero sin caso reproducido: las dos ejecuciones de hoy salieron limpias **sin nada escuchando en 5672**, y la única prueba que consulta `/actuator/health` ya tolera un 503.

**Lo que queda abierto y quedó escrito** en [[deuda-tecnica-y-riesgos]], como deuda propia y no heredada: el **filtrado por dueño no existe** —un usuario del grupo `clients` ve todos los clientes y el catálogo entero, y ninguna clase fuera de `bootstrap/config` toca siquiera `Authentication`—, dejado fuera por decisión explícita y no por olvido; los dos grupos de OpenAPI sin prueba; las tres autoridades de `work-order` sin módulo detrás, marcadas con una prueba que fallará el día que aparezcan sus endpoints; y que **no es verificable que el realm que Keycloak importa sea el del repositorio**, porque las pruebas leen el archivo versionado y una edición en la consola no la ve nadie.

Y una deuda que **no** se cierra de rebote: «una persona retirada conserva su acceso» sigue abierta, reverificada hoy sobre el código. Decidir qué puede hacer cada rol es una cosa y quitarle la entrada a quien ya no está es otra.

**Y un cuarto dato falso, menor pero contable**: el `CONVENCIONES.md` de la raíz decía «57 defectos conocidos del original». Son **63**, contados fila a fila hoy; el log del 2026-09-02 ya daba esa cifra y el archivo de la raíz no se actualizó entonces.

Nota nueva: [[modelo-de-permisos]]. Actualizadas [[decisiones-tecnicas-malphasos]], [[deuda-tecnica-y-riesgos]], [[seguridad-keycloak-backend]], [[keycloak-configuracion]], [[stack-spring-boot-4-particularidades]], [[checklist-reutilizacion]] y el `CONVENCIONES.md` de la raíz, que afirmaba `@PreAuthorize("hasAuthority('admin.full')")` en todas las operaciones y una batería de 339 pruebas.

## [2026-09-09] ingest | Dar de baja a alguien ya le quita la entrada, pero solo a partir del proximo token

Cierre en el wiki de la tanda de seguridad construida en `fix/person-identity-sync` (`9588c6d`, `308cbb9`, `5fde17c`), **sin mergear a `main`**. Todo lo que sigue se verificó contra esa rama y contra los cuerpos de los commits, no contra los resúmenes de quien la construyó.

**Las dos brechas eran de la misma familia y las dos llevaban meses a la vista**, anotadas desde el contraste ERS↔código del 2026-09-02: `PersonService.delete` marcaba `estadoActivo = false` sin tocar Keycloak —la cuenta sobrevivía emitiendo tokens válidos— y `PersonService.update` no propagaba nada.

**El motivo que casi se registra mal.** Se deshabilita el usuario en vez de borrarlo, y la razón que primero viene a la cabeza —la simetría con el borrado lógico— es la floja. La dura es que **el identificador de la persona *es* el id del usuario de Keycloak**: `register` hace `UUID.fromString` sobre lo que devuelve `createUser`. Borrar rompería esa correspondencia para siempre y recrear la cuenta daría otro UUID, sin tabla de equivalencias que lo repare. El javadoc de `PersonService.delete` **sigue dando la razón floja** mientras el del puerto y el cuerpo del commit dan la buena; queda señalado en la nota nueva y **no se ha tocado el código**.

**Lo demás que valía la pena registrar**, todo en [[decisiones-tecnicas-malphasos]] y desarrollado en [[sincronizacion-con-proveedor-de-identidad]]: el orden **falla cerrado** —Keycloak antes de persistir, aceptando a cambio que la transacción siga abierta durante una llamada de red—, fijado con una prueba de orden porque mover una línea basta para invertirlo; un usuario inexistente no impide la baja, porque quien se dio de alta con `save` nunca tuvo cuenta; y no se añade `enableUser` porque no hay hoy ningún camino de reactivación que lo llamaría.

**Lo que no se puede propagar todavía, y por qué no es pereza.** `update` lleva a Keycloak solo nombre y apellido. **El correo no se puede sincronizar** mientras una persona tenga varios sin ninguno marcado como principal: no hay forma de saber cuál es el de la cuenta. Es un límite del modelo de datos, con migración por delante. Por eso la fila de `PersonService.update` en [[deuda-tecnica-y-riesgos]] queda **parcialmente resuelta**, no cerrada.

**El límite que se escribe en vez de disimularse.** Verificado contra un Keycloak 26.6.1 real: deshabilitar impide autenticarse y renovar —`invalid_grant`—, pero **un token emitido antes de la baja sigue abriendo el API hasta que caduca**, 300 s en este realm (`accessTokenLifespan` del `malphasos-realm-realm.json`, comprobado). El resource server lo valida solo con la firma. **La brecha queda cerrada para las autenticaciones nuevas, no para las ya emitidas**, y cerrarla del todo exige introspección por petición. Anotado también en [[seguridad-keycloak-backend]], que es donde lo buscará quien pregunte por la validación del token.

**Dos hallazgos que salieron de comparar, no de leer.**

1. Añadir `case 404` a `translateClientFailure` cambió la conducta de `deleteUser`, que ya existía: de sus **dos** caminos para un 404, el que pasa por la traducción da ahora `KeycloakUserNotFoundException`. Es inocuo —su único llamante captura `RuntimeException` y solo cambia el log—, pero era un cambio real que nadie había señalado, y deja una regla reutilizable: **un `switch` de traducción compartido tiene tantos llamantes como métodos lo usen, y añadirle un caso los modifica a todos**.
2. **Cambiar `tipoPersona` no mueve al usuario de grupo en Keycloak.** Quien deja de ser ingeniero conserva sus permisos. Verificado que es **heredado**: el adaptador original también fija el grupo una sola vez, en `createUser`. Sigue abierto y pertenece a la línea del [[modelo-de-permisos]], no a ésta.

**Y un defecto encontrado contrastando el `CONVENCIONES.md` de la raíz contra el código**, que no se ha arreglado: el archivo afirma «solo `PATCH`, sin `PUT`» sin acotarlo, y `person` **conserva tres `PUT`** —persona, correo y teléfono— del CRUD original. La convención se fijó al migrar `location` y nadie volvió sobre el módulo anterior. Anotado como deuda propia; el `CONVENCIONES.md` queda acotado.

**Sobre el conteo, medido y no copiado.** Dos ejecuciones completas de `./mvnw test` el 2026-09-09 sobre `fix/person-identity-sync`, borrando `target/surefire-reports` antes de cada una y **con nada escuchando en 5672**: idénticas, 36 clases, cero fallos, cero errores.

**Corrección: el atributo `tests=` de los XML tampoco cuenta todo.** [[stack-spring-boot-4-particularidades]] lo daba como el conteo bueno. Hay un tercero, y es mayor: **363** por el resumen de Maven —que coincide exactamente con el número de elementos `<testcase>`— frente a **361** por el atributo y **329** por los `.txt`. Los dos que faltan salen de una sola clase: `CatalogAggregatesTest` declara `tests="23"` y tiene 25 `<testcase>`, porque tres de sus clases `@Nested` definen un método con el mismo nombre, `referenciasObligatorias`, y el atributo agregado lo cuenta una vez. Comprobado sobre los 36 XML: es la única clase del proyecto con nombres repetidos y la única donde ambos números difieren. **Las cifras publicadas hasta ahora no estaban infladas: estaban ligeramente por debajo.**

**Corrección: la intermitencia ya no se atribuye a RabbitMQ, y ahora está cerrada por su lado.** El 2026-09-08 ya se había corregido el diagnóstico —la causa demostrada era `unico()` recortando `nanoTime()` con `substring(0, 10)`—; lo que se añade hoy es que ese arreglo **vivía solo en `feat/permission-model`**, que no está en `main`, y hubo que **traerlo por separado** a `fix/person-identity-sync` (`308cbb9`), aplicándolo además a `ClientSchemaTest`. La parte de `unico()` se da por cerrada; la de RabbitMQ sigue anotada como riesgo **sin caso reproducido**.

**Y un dato de contexto que faltaba en todas partes**: hay **dos ramas de código fuera de `main` que no descienden una de otra**. `feat/permission-model` parte de un `main` anterior y `fix/person-identity-sync` del actual. 361 **no** incluye las 139 pruebas de seguridad y 472 **no** incluye la baja en Keycloak; el día que se mergeen habrá que remedir, no sumar. Queda escrito en [[checklist-reutilizacion]] y en el `CONVENCIONES.md` de la raíz, que hablaba de una sola rama pendiente.

Nota nueva: [[sincronizacion-con-proveedor-de-identidad]]. Actualizadas [[dominio-persona-identidad]], [[seguridad-keycloak-backend]], [[decisiones-tecnicas-malphasos]], [[deuda-tecnica-y-riesgos]], [[stack-spring-boot-4-particularidades]], [[checklist-reutilizacion]], `index.md` y el `CONVENCIONES.md` de la raíz.

## [2026-09-09] lint | El wiki respondia una pregunta que ya estaba contestada

Reenfoque del wiki entero. **El eje de juicio con el que nació —qué portar de `bolivarbioingenieria-app`, medido con la etiqueta `reusable:*`— dejó de servir cuando la migración terminó.** Los cuatro módulos están completos; quien llega hoy pregunta cómo funciona MalphasOS y qué falta por construir, y el índice le respondía con las categorías técnicas del sistema viejo.

**Un eje nuevo en el frontmatter, obligatorio: `describe:*`.** Dice de qué sistema habla cada nota, que es lo que los directorios no dicen —una nota de `dominio/` podía ser el hexágono original, el módulo de MalphasOS o el camino de uno al otro—. Aplicado a las 46 notas: **31 `describe:ambos`** (siguen una pieza del original hasta aquí, y son las que más se usan), **8 `describe:original`** (nada de eso está construido) y **7 `describe:malphasos`**.

**Qué se hizo con `reusable:*`, y por qué no se retiró.** Se **congela**: se conserva donde está, no se actualiza, no se usa para ordenar ni para decidir, y **no se pone en notas nuevas**. Retirarla habría borrado el registro fechado del juicio con el que se decidió portar cada pieza, que es justo lo que este wiki prohíbe hacer en silencio. Que sobreviva en tres notas sobre MalphasOS con el sentido corrido de «aplicable a otros módulos» **es la prueba de que se desgastó**, no una instrucción; se le quitó a la nota de sincronización de identidad, escrita hoy mismo, porque ahí significaba otra cosa.

**`index.md` reordenado por la pregunta con la que se llega**, no por categoría técnica: qué falta por construir · cómo funciona MalphasOS hoy · cómo se llegó hasta aquí · lo que todavía no existe · de dónde viene todo esto. **Ninguna nota se borró.** Comprobado que las 46 aparecen y que no queda ningún enlace roto salvo los intencionados.

**Nota nueva: [[hoja-de-ruta-producto]].** [[checklist-reutilizacion]] se queda como el registro **cerrado** de la migración; convertirlo en dos listas de tareas las habría desincronizado.

**Las cifras, verificadas y no copiadas**: 8 de 31 requisitos funcionales, 1 de 23 no funcionales, **cero frontend** —no existe el directorio—. Sin una sola línea: órdenes de trabajo (7), reportes (6), firma digital (2), inventario de existencias (2), alertas y calibración (2), módulo comercial (2) y dos de hojas de vida. Contrastado contra `Documentation/wiki/` y contra la sección 3.2 del `IEEE830.tex`, y lo citado comprobado en el código: 83 mappings REST, cinco migraciones, ni `@EnableScheduling` ni tabla de orden de trabajo, y el grupo de OpenAPI `reports` apuntando a un módulo que no existe.

**Lo que hace útil la hoja de ruta es el orden, y de extraer el grafo salieron cuatro dependencias que nadie había señalado:**

1. **El grafo declarado en la ERS contiene un ciclo, y está dentro de órdenes de trabajo.** RF-05 depende de RF-07, RF-07 de RF-06 y RF-06 de RF-05 —y además de RF-09, de reportes, que a su vez depende de RF-05 y RF-07—. Leído al pie de la letra, ninguno de los cuatro puede empezar. Es un defecto del documento, no un bloqueo real, pero detiene a quien planifique leyendo solo la ERS.
2. **Exportar un reporte a PDF (RF-17) depende de la firma digital (RF-21)**, y la firma es **táctil** (RF-18). Es decir: **el requisito de PDF está bloqueado por el frontend**, tres saltos más allá, y nada en el documento lo dice junto.
3. **Alertas y calibración es el caso donde los dos grafos discrepan.** La ERS declara que RF-40 depende de RF-11, del reporte. El bloqueo real es que **no hay dónde guardar una fecha de calibración**: eso son las verificaciones técnicas y los datos metrológicos, la segunda tanda de `equipment`, y ningún requisito lo expresa. Falta además un mecanismo que ningún requisito nombra: **no hay tareas programadas en el backend**, comprobado.
4. **RF-14, la configuración de protocolos, se puede construir hoy**: depende solo de RF-22, que está implementado. Es la única pieza de reportes con el camino libre, y no se ve mirando la categoría porque el resto de reportes sí está bloqueado.

**Y una dependencia de proceso que no es de requisitos**: `feat/permission-model` incluye una prueba que **falla a propósito el día que aparezca el primer endpoint de órdenes de trabajo** —`ApiAuthority` ya declara las tres autoridades de `work-order` y el realm ya se las da al grupo `engineers`—. Construir órdenes de trabajo antes de mergear esa rama significa escribir los controladores sin ese vocabulario y volver después.

**El método, otra vez, fue comparar dos artefactos y no leer uno.** Las cuatro dependencias salieron de extraer el grafo completo del `.tex` y ponerlo al lado del esquema y de los 83 mappings. Ninguna se ve leyendo la categoría donde vive.

**Dos categorías no están bloqueadas técnicamente sino por el propio documento**: inventario de existencias depende de RF-35 y el módulo comercial de RF-46, y **ninguno de los dos códigos existe** en el apartado 3.2. Antes de construirlos hay que escribir el requisito que falta.

**Lo que la hoja de ruta no puede decidir queda escrito como tal**: cuándo entra el frontend. De él dependen los seis RNF-12 a RNF-17 enteros —accesibilidad, responsivo, táctil, usabilidad, componentes de entrada y mensajes legibles—, otros tres RNF, la firma digital y los manuales de usuario. Y la ERS presenta el trabajo móvil en campo como su razón de ser: el apartado 1.2 lo declara, el 2.1 exige que funcione desde el teléfono sin instalar nada nativo y el 3.1.2 lista la pantalla táctil entre las interfaces de hardware. **Técnicamente el frontend no está bloqueado por nada** y podría empezar hoy contra los cuatro módulos que ya publican API; la decisión es del usuario.

**[[sintesis-malphasos]] se cierra en vez de borrarse.** Era la tesis de qué portar y su pregunta está contestada. Se le añade el registro de **en qué acertó y en qué no**, verificado contra el código: `person` **no** se construyó en Generación 2 pese a que la nota decía «desde el día uno»; MapStruct resultó inservible para construir agregados; el despachador de RabbitMQ, el proveedor de datos de reportes y el starter de frontend **no se portaron**; y sobre el manejo de errores **se decidió lo contrario** de lo que recomendaba, a propósito. Lo que sí resultó ser el hallazgo útil es el de fondo: el original convivía con dos generaciones de patrón y había que elegir.

**Un hueco que este reenfoque destapó y que no se ha tapado**: **no existe ninguna nota que describa el esquema de MalphasOS.** [[esquema-bd-v4]] describe las 27 tablas del original, y las cinco migraciones `V1`–`V5` solo aparecen de refilón en las notas de migración. Queda como enlace sin destino en `index.md`, `[[esquema-malphasos]]`, que es la forma que este wiki tiene de decir «esto merece una nota».

**Y una corrección de higiene**: `migracion-client-hallazgos` tenía un enlace con sintaxis de alias, `[[nota|texto]]`, que esta wiki no usa y que por tanto no apuntaba a ninguna parte. Corregido. Tras el reenfoque **no queda ningún enlace roto** salvo los dos intencionados y las plantillas del propio `CONVENCIONES.md`.

Nota nueva: [[hoja-de-ruta-producto]]. Actualizadas `SecondBrain/CONVENCIONES.md` (propósito, los dos ejes de etiquetas, reglas duras), `index.md` (reescrito), [[sintesis-malphasos]], [[migracion-client-hallazgos]], el frontmatter de las 46 notas y el `CONVENCIONES.md` de la raíz.

## [2026-09-09] ingest | El traslado que no cruza de cliente, y una prueba que pasa en vacío

Registro de `feat/relocation-same-client` (`3c002b2`, **sin mergear**), leído del commit y contrastado contra el código, no del resumen de quien lo construyó.

**El agujero que cerró.** `ClientEquipmentService.relocate` solo comprobaba que el área de destino existiera y estuviera activa. Nada impedía mover una unidad al área de **otro cliente**, y este wiki ya tenía escrito que el traslado es *el hecho que más importa de una unidad, porque cambia quién responde por ella*. Un traslado que cruza de cliente deja el historial de mantenimiento colgando de quien nunca la tuvo, sin error y sin rastro.

**Lo que esta entrada NO puede afirmar, y es su punto.** **La regla está construida y sin verificar.** `EquipmentChainServiceTest.Unidad.trasladar` **pasa en vacío**: el doble de `ServiceAreaServicePort` no tiene estubado `findOwningClient`, Mockito devuelve `null` en las **dos** consultas de propiedad y `Objects.equals(null, null)` deja pasar la guarda. Comprobado leyendo la prueba y el servicio.

La batería da **496 ejecuciones y cero fallos** sobre esa rama — medido aquí con `./mvnw test` y `rm -rf target/surefire-reports` antes—, **exactamente lo mismo que `main` en `01c3277`**, medido igual el mismo día, **porque no se añadió ninguna prueba**. Ese verde confirma que no se rompió nada; **no confirma que la regla funcione**, y así queda escrito en la nota, en la deuda propia y en el `CONVENCIONES.md` de la raíz.

**Y una circunstancia de proceso que no debe leerse como olvido**: el ciclo es `desarrollador → tester → wikista` y esta tanda **se saltó el paso central por decisión explícita del usuario**.

**Las decisiones que se registraron con su porqué**, todas en [[decisiones-tecnicas-malphasos]] y desarrolladas en la nota nueva: `client` **publica la respuesta** con `ServiceAreaServicePort.findOwningClient(UUID)` en vez de que `equipment` camine área → sede → cliente dos veces y se ate a la estructura interna de otro contexto; es una **llamada síncrona porque un evento no contesta preguntas** —con eventos habría que permitir el traslado y deshacerlo después—; la regla **vive en el servicio** porque un `CHECK` tendría que cruzar tres tablas para comparar dos clientes que ninguna guarda junta; **en el alta no se comprueba nada**, porque es el área elegida la que define de qué cliente pasa a ser la unidad; y el rechazo sale **409 con `ERR_EQUIPMENT_010`** —los datos son válidos, choca el estado—, con precedente verificado en `PersonControllerAdvice`, que responde 409 a `KeycloakUserAlreadyExistsException`.

**Una decisión de diseño que empujó el reparto de agentes, y por eso queda escrita.** Se amplió el puerto existente en vez de publicar uno dedicado al estilo de `PersonCommunicationPort`. La razón de arquitectura es buena —`equipment` ya dependía de ese puerto **y** del agregado `ServiceArea`, así que un segundo contrato no estrechaba nada—, pero **hubo una segunda**: inyectar una dependencia nueva cambia el constructor del servicio y rompe la compilación de una prueba que el `desarrollador` **tiene prohibido tocar**. Aquí coincidió con la opción correcta; no hay garantía de que siempre coincida, y el día que no coincida hay que notarlo en vez de dejarse arrastrar.

**El método, que es lo más reutilizable de esta tanda.** **El propio `desarrollador` detectó la prueba degenerada y la reportó sin poder arreglarla**, porque el archivo es del `tester`. Este wiki ya registra que los defectos aparecen **al comparar** y no al leer; éste añade una vía nueva: **apareció porque una frontera impidió el arreglo cómodo**. La única salida fue escribirlo, y quedó en el cuerpo del commit con las palabras "la regla queda SIN VERIFICAR". Una separación de dominios que impide taparlo en silencio convierte un hallazgo privado en uno público — un beneficio del reparto que no se buscaba al diseñarlo.

**Cuatro defectos anotados y no arreglados** (deuda propia en [[deuda-tecnica-y-riesgos]]): `relocate` comprueba el área **antes** que la existencia de la unidad, de modo que una unidad inexistente hacia un área cerrada da **400 y no 404** —anterior a este cambio y **fijado por una prueba**, `trasladarAAreaCerrada`, que nunca estuba la lectura de la unidad—; un traslado al área que la unidad ya ocupa recorre **seis lecturas** y llega al `save` para no emitir nada, con el área de destino leída **dos veces** en la misma transacción; y `findByServiceArea` llama a `findById` descartando el resultado.

**El cuarto no lo reportó nadie: salió de leer el javadoc del puerto al lado del catálogo de errores del módulo.** `findOwningClient` declara que lanza `HeadquarterNotFoundException`, y `EquipmentControllerAdvice` **no la mapea** —ni `GlobalControllerAdvice`—, así que saldría como **500**; y la `ServiceAreaNotFoundException` que puede venir del **área actual** de la unidad se traduce a 404 `ERR_EQUIPMENT_009`, que el llamante leerá como "el área de destino no existe". Las dos son hoy poco alcanzables porque las columnas son `NOT NULL` con clave foránea, pero el contrato del puerto las declara por escrito. Es la misma familia que [[traduccion-de-fallos-de-adaptadores]], vista desde un puerto que cruza de módulo.

**Correcciones de afirmaciones que resultaron falsas al verificarlas.**

1. **Las dos ramas que el wiki daba por «sin mergear» están en `main`.** `feat/permission-model` entró por `e6dda32` y `fix/person-identity-sync` por `258cd81`, las dos el 2026-09-09, y se borraron tras el merge. Lo afirmaban `index.md` (dos entradas), el `CONVENCIONES.md` de la raíz (la tabla de ramas y la fila de `bootstrap`, que decía que `ApiAuthority` **no existe en `main`**), [[checklist-reutilizacion]], [[hoja-de-ruta-producto]] —donde eran el **bloque 0** del orden propuesto— y dos filas de [[deuda-tecnica-y-riesgos]]. Corregido en todas, **sin borrar lo anterior**: la tabla vieja del checklist queda tachada, porque su aviso era correcto.
2. **La remedición que estaba anotada como pendiente, hecha.** El wiki decía «el día que se mergeen habrá que remedir, no sumar». `main` en `01c3277` da **496** elementos `<testcase>` —494 por el atributo `tests=`, 380 por los `.txt`—, 41 clases, cero fallos. **No es 472 + 361**: sumar habría producido un número inventado. Anotado en [[stack-spring-boot-4-particularidades]].
3. **«Los equipos se mueven dentro de una sede» se quedaba corto**, y por eso el traslado tenía un agujero. Una unidad se mueve entre áreas de una sede **y entre sedes del mismo cliente**. Corregido en [[migracion-equipment-hallazgos]].
4. **«Cinco invariantes de este tipo en el proyecto» pasa a seis**, y la sexta rompe el molde: las cinco anteriores comprueban que algo esté **activo**; ésta compara dos clientes que ninguna tabla guarda juntos. La frase «todas viven en los servicios, **con sus pruebas**» deja de ser cierta para la nueva.

**Lo que se decidió omitir por no tener valor duradero**: el detalle línea a línea del diff —está en `git show 3c002b2`, cuyo cuerpo ya explica el porqué mejor que cualquier paráfrasis—, y el texto exacto del javadoc y de la descripción de OpenAPI, que envejecen con el código y se leen en el archivo.

Nota nueva: [[regla-traslado-mismo-cliente]]. Actualizadas [[decisiones-tecnicas-malphasos]], [[migracion-equipment-hallazgos]], [[dominio-cliente]], [[dominio-equipo-mantenimiento]], [[deuda-tecnica-y-riesgos]], [[checklist-reutilizacion]], [[hoja-de-ruta-producto]], [[stack-spring-boot-4-particularidades]], `index.md`, `SecondBrain/CONVENCIONES.md` y el `CONVENCIONES.md` de la raíz.

## [2026-09-12] lint | Lo que el wiki escribió el 09 dejó de ser cierto el 10: la regla del traslado ya está verificada

**Qué lo motivó.** La entrada anterior registró la regla del traslado como **construida y sin verificar**, y lo era. El **2026-09-10** el `tester` pasó por `feat/relocation-same-client` y añadió `43de295`, encima de `3c002b2`. Esta pasada solo corrige el wiki; no hay código nuevo aquí.

**Verificado antes de escribir**, no copiado del resumen del `tester`: se leyó `git show 43de295` entero y se contaron los `@Test` añadidos archivo a archivo —4 en `ServiceAreaServiceTest`, 7 en `EquipmentChainServiceTest`, 1 en `EquipmentRestAdapterTest`, 1 en el nuevo `ClientEquipmentRelocationPersistenceTest`: **13**—, y se corrió la batería aquí con `./mvnw test` y `rm -rf target/surefire-reports` antes: **509** elementos `<testcase>`, **507** por el atributo `tests=`, **386** por los `.txt`, **42** clases, cero fallos, cero errores, cero omitidas. 496 + 13 cuadra, y aquí sumar **sí** es legítimo porque es la misma rama con pruebas encima, no dos ramas que no descienden una de otra.

**Tres afirmaciones del wiki quedaron falsas y se corrigieron sin borrarlas.**

1. **«La regla está construida y sin verificar».** Lo estuvo un día. La prueba degenerada está reparada —`trasladar` estuba ahora `findOwningClient` para el área actual y para la de destino— y con ella entran los dos casos frágiles que dan valor a la tanda: trasladar a **otra sede del mismo cliente** se permite —quien compare sedes en vez de clientes pasa todo lo demás y solo falla ahí— y un área **inactiva que además es de otro cliente** responde **400 por cerrada y no 409 por cliente**, porque el área se comprueba antes. Corregido en [[regla-traslado-mismo-cliente]] —la sección vieja se conserva entera bajo un aviso, porque es el registro de lo que costó saltarse el paso central del ciclo—, [[dominio-equipo-mantenimiento]], [[migracion-equipment-hallazgos]], [[decisiones-tecnicas-malphasos]], `index.md` y el `CONVENCIONES.md` de la raíz.
2. **«La prueba que pasa en vacío» figuraba como deuda propia pendiente.** Marcada **resuelta el 2026-09-10** en [[deuda-tecnica-y-riesgos]], con la fila conservada y tachada.
3. **«`feat/relocation-same-client` (`3c002b2`), también 496, porque no añadió ninguna prueba».** La rama está en `43de295` y en **509**. Corregido en [[checklist-reutilizacion]], [[stack-spring-boot-4-particularidades]] —donde la tabla de conteos gana una columna— y el `CONVENCIONES.md` de la raíz.

**El matiz del 500 que esta sesión no tenía, y cambia cómo se lee el defecto.** El wiki registró que `findOwningClient` declara `HeadquarterNotFoundException`, que ningún advice mapea, y que por tanto saldría **500**. Sigue siendo cierto —comprobado hoy: `EquipmentControllerAdvice` tiene diez `@ExceptionHandler` y ninguno es ése, y `GlobalControllerAdvice` solo cubre tres familias—, pero **hoy es inalcanzable por el API**: `area_servicio.k_id_sede` es `NOT NULL` con `FOREIGN KEY ... REFERENCES sede` y **ninguna sede se borra de verdad** —borrado lógico universal; los puertos de `client` ni siquiera declaran `delete`—, así que un área apunta siempre a una sede existente por construcción. Es un hueco del contrato de error, **no un fallo explotable**. El `tester` lo confirmó y **decidió no escribir la prueba REST a propósito**, porque habría fijado ese 500 como el comportamiento esperado; lo que sí dejó es una prueba de que el puerto lanza lo que promete. Anotado como **precisión**, no como corrección: la afirmación no era falsa, era menos precisa de lo que debía.

**El hallazgo de método, que es lo que hay que llevarse de esta pasada.**

**Reparar la prueba ciega no destapó ningún defecto: la guarda funcionaba.** Es información útil justamente por ser el resultado aburrido. Sirve para dos cosas opuestas: **calibrar la alarma** —en este proyecto ya hay un caso documentado de prueba que pasaba en vacío sin nada detrás, así que no hay que darlo por bug confirmado— y **no ablandar la exigencia de repararla**, porque el daño no era el bug escondido sino que la batería podía quedarse verde para siempre sin ejercer la regla ni una vez.

**Y lo que lo permitió, que sigue vivo**: **`MockitoExtension` en modo estricto no detecta esto.** `STRICT_STUBS` falla cuando **sobra** un estubado y calla cuando **falta**: la llamada sin estubar devuelve el valor por defecto del tipo, `null` para un `UUID`, y `Objects.equals(null, null)` es cierto. La herramienta que se supone que aprieta las pruebas es ciega a esta forma exacta de degeneración. Entra como **fila nueva de deuda propia** en [[deuda-tecnica-y-riesgos]] —la deuda propia pasa de 22 a 23 contando las dos secciones— porque no tiene arreglo automático: la única defensa es `verify(...)` sobre las llamadas que importan, y hay que acordarse de escribirlo.

**Un efecto lateral que conviene saber antes de abrir la corrección.** El defecto del **400 en vez de 404** —`relocate` mira el área antes que la existencia de la unidad— pasó a estar fijado por **dos** pruebas en vez de una, y la nueva, `trasladarUnidadInexistenteConAreaCerrada`, describe ese orden como intencionado en su nombre y en su javadoc. Corregirlo ya no rompe una prueba de refilón: contradice una que lo afirma. El arreglo se encarece; a cambio, el comportamiento dejó de ser accidental. Anotado en la fila correspondiente.

**Se cierra también el circuito del hallazgo del 09.** El `desarrollador` vio la prueba degenerada y **no pudo arreglarla** porque el archivo es del `tester`; la escribió en el commit y el wiki la publicó. El `tester` la recogió al día siguiente. El coste del reparto —quien ve el hueco no lo tapa— se pagó en **un día**; a cambio quedó por escrito en tres sitios en vez de arreglado en silencio en uno.

**Lo que se decidió omitir.** El detalle de cada una de las 13 pruebas: está en `git show 43de295`, y el cuerpo del commit explica el porqué mejor que cualquier paráfrasis. Se registran solo las dos frágiles, porque **fijan una decisión de diseño que una implementación perezosa rompería sin que nada más fallara**, y eso no se deduce leyendo el código. Tampoco se registra que la prueba de persistencia usa Testcontainers ni cómo arma sus datos: es la convención del proyecto y ya está escrita.

**Sigue sin mergearse a `main`.** La rama de código es `feat/relocation-same-client` (`43de295`); esta pasada de wiki va en `docs/wiki-traslado-mismo-cliente`.

## [2026-09-12] ingest | Órdenes de trabajo, tanda 1 de 4: el esquema, y lo que el original nunca tuvo

**Qué se documenta.** La rama `feat/work-order-schema`, **sin mergear**: `378cab2` (la migración `V6__work_order.sql`, 132 líneas) y `24e7640` (`WorkOrderSchemaTest`, 571 líneas). Es la **primera de cuatro tandas** del quinto módulo —esquema, dominio, aplicación y persistencia, REST—, el mismo orden que llevaron los otros cuatro. El ciclo se recorrió entero esta vez: `desarrollador → tester → wikista`, con la batería en verde y **cero defectos encontrados** por el `tester`.

**Verificado antes de escribir, no copiado de los resúmenes.** Se leyeron los cuerpos de los dos commits, la migración entera y el archivo de pruebas; se contaron los métodos (**19 `@Test` + 4 `@ParameterizedTest` = 23 métodos, 32 ejecuciones**, que es lo que dice el XML de Surefire de esa clase); y se corrió la batería aquí con `./mvnw test` y `rm -rf target/surefire-reports` antes: **541** elementos `<testcase>`, **539** por el atributo `tests=`, **418** por los `.txt`, **43** clases, cero fallos, cero errores, cero omitidas. 509 + 32 = 541, y sumar es legítimo porque es la misma línea con pruebas encima.

**Lo que se registró, y por qué cada cosa merecía quedar.**

- **Nota nueva [[dominio-orden-trabajo]]**: el módulo entero. Las decisiones con su porqué —cliente y sede redundantes para proteger el histórico; la clave foránea **compuesta** contra `sede` que hace que el motor, y no la convención, rechace una sede de otro cliente; el área congelada en la tabla puente; las áreas del formulario que no se guardan; `date` en vez de `timestamp`; el ingeniero anulable—, las tres enumeraciones **con su procedencia**, las **siete reglas** que el esquema deja al servicio y la trampa que espera a la tanda REST.
- **Nota nueva [[congelar-una-referencia-historica]]**: el caso de la clave foránea compuesta que **no** hay que añadir al puente. Tiene nota propia y no un párrafo porque es la clase de «mejora» que alguien añade de buena fe: parece la restricción correcta, y PostgreSQL la comprobaría también al **actualizar** la fila referenciada, de modo que una orden vieja bloquearía un traslado legítimo. La forma general —si una columna guarda *dónde estaba* algo, no es una referencia al estado actual— aplica a cualquier dato histórico.

**El hallazgo de método, que es lo más reutilizable de la tanda.** Los valores de las enumeraciones se le **preguntaron al usuario tres veces** antes de que apareciera que las periodicidades y los estados estaban escritos en un `CHECK` del esquema heredado. Nadie lo abrió porque el original **no sirve de plantilla para este módulo**: su `orden_trabajo` no tiene cliente, ni sede, ni tipo de servicio, ni vínculo con los equipos. La regla: **un esquema puede ser inútil como diseño y seguir siendo la única fuente escrita del vocabulario**; antes de pedir una decisión de diccionario, agotar el original. Encaja con lo que [[reglas-de-negocio-en-el-esquema]] ya decía de los `CHECK`, con un uso que esa nota no contemplaba.

**Una afirmación del wiki resultó falsa y se corrigió sin borrarla.** [[esquema-bd-v4]] decía que `reporte_servicio` tenía «FK a orden_trabajo + equipo_cliente». **No existe ninguna**: esa tabla solo tiene declarada su `PRIMARY KEY`, y sus dos columnas de referencia son **`varchar(10)` contra llaves primarias `uuid`**, así que la restricción no podría declararse aunque alguien la escribiera. `grep "REFERENCES orden_trabajo"` sobre `A_Sigma_DB_V4.sql` no devuelve nada. **El error de origen es identificable y vale más que el dato**: la afirmación salió de leer los **comentarios de columna** del SQL, que sí dicen «es la llave foránea que referencia a…», en vez de las restricciones. Corregido en [[esquema-bd-v4]] con la línea vieja conservada bajo un aviso, y con fila propia en [[deuda-tecnica-y-riesgos]].

**Dos afirmaciones más quedaron desfasadas por el simple paso del tiempo y se corrigieron igual.** [[hoja-de-ruta-producto]] decía que «tipo de servicio y periodicidad no tienen columna en ningún sitio» —cierto hasta el 2026-09-12— y que ese vocabulario era **nuevo del dominio**, cuando dos de las tres enumeraciones estaban en el esquema heredado. Y `index.md` hablaba de **cinco** migraciones `V1`–`V5`: son **seis**.

**Deuda registrada.** Cinco filas nuevas de deuda **heredada** —de 63 a **68**—, todas de la misma tabla y todas verificadas sobre el archivo: la tabla sin cliente, sede, tipo ni equipos; ninguna restricción que la referencie más la FK imposible de `reporte_servicio`; `f_fecha_mantenimiento` como `timestamp` documentada como fecha de creación; el `CHK_periodicidad` traducido a medias —`MONTHLY, QUARTERLY, BIANNUAL, ANUAL`, tres en inglés y uno en español, con `BIANNUAL` significando a la vez «dos veces al año» y «cada dos años»—; y el comentario de `t_estado_ejecucion`, que documenta los valores de `v3` mientras el `CHECK` de `v4` admite otros. Y una fila nueva de deuda **propia** —de 23 a **24**—: las siete reglas que el esquema admite y nadie defiende todavía, anotadas para que una migración en verde con 32 pruebas no se lea como un esquema que las cubre.

**La trampa que hay que saber antes de la tanda REST.** `RestAuthorizationCoverageTest.lasAutoridadesDeWorkOrderSiguenSinModulo` **se pondrá roja a propósito** con el primer controlador del módulo, y hay que retirarla en ese mismo commit junto con el `filter(a -> !a.startsWith("work-order."))` de `ningunaAutoridadSobra`. Es el patrón de omisión consciente funcionando, y sin este aviso parecerá una regresión. **Corrección de un informe previo, dejada por escrito**: el `tester` afirmó que esa prueba «no vive en esta rama». Es falso —entró en `main` con `e6dda32` el 2026-09-09— y se comprobó con `git show main:…/RestAuthorizationCoverageTest.java`. No afectó a su trabajo.

**Lo que se decidió omitir.** El listado columna a columna de `V6` y el detalle de las 23 pruebas: están en `git show 378cab2` y `git show 24e7640`, cuyos cuerpos explican el porqué mejor que cualquier paráfrasis, y el SQL lleva los comentarios encima. Tampoco se registran los cuatro índices ni los `COMMENT ON`: son convención del proyecto y ya está escrita. Sí se registró, en cambio, que los valores inválidos de las pruebas son **cortos a propósito** —las tres columnas están dimensionadas justas y uno más largo fallaría por longitud y no por el `CHECK`—, porque es una prueba que pasaría por el motivo equivocado y eso no se deduce leyendo el archivo.

**No se mergea nada.** El código sigue en `feat/work-order-schema` (`24e7640`); esta pasada de wiki va en `docs/wiki-work-order-schema`.

**Una corrección más, encontrada al final de esta misma pasada y no por leer el wiki.** Al comprobar de qué desciende `feat/work-order-schema` —`git merge-base --is-ancestor 43de295 24e7640`— apareció que **`feat/relocation-same-client` ya está en `main`**, mergeada por `1ef55cf`, y su pasada de wiki por `55e0a5d`. Todo lo que el wiki escribió esta misma mañana la daba por **rama pendiente**. Quedaron falsas cuatro afirmaciones y se corrigieron sin borrarlas: `main` no está en `01c3277` sino en **`55e0a5d`**; su batería no es 496 sino **509**; la fila de `equipment` del `CONVENCIONES.md` de la raíz situaba el traslado «en rama sin mergear»; y la tabla de conteos de [[stack-spring-boot-4-particularidades]] etiquetaba como «`main`» una columna que ya no lo describe. **Las 509 de `main` no se remidieron**, y se dice así en vez de afirmar una medición que no se hizo: `git diff 43de295 main -- malphasos/` sale vacío, de modo que el código es el mismo que se midió horas antes. Tocadas [[regla-traslado-mismo-cliente]], [[checklist-reutilizacion]], [[stack-spring-boot-4-particularidades]], [[hoja-de-ruta-producto]] y el `CONVENCIONES.md` de la raíz.

**La lección de método, que es la parte reutilizable**: **el estado de las ramas caduca más rápido que ninguna otra cosa que este wiki escribe**, y no hay ninguna nota que avise cuando caduca. Lo encontró un `git merge-base` hecho para otra cosa. Conviene que toda pasada de wiki empiece comprobando `git branch --contains` de las ramas que el wiki declara pendientes, en vez de fiarse de lo que dijo la pasada anterior — aunque fuera del mismo día.

## [2026-09-13] ingest | Órdenes de trabajo, tandas 2 a 4: el módulo cerrado, y la regla que faltaba

**El wiki se había quedado atrás tres tandas.** La pasada anterior registró el esquema y dio el módulo por «existe el esquema y nada más». El dominio se mergeó a `main` (`97ef74f`) **sin pasada de wiki**, y la aplicación y el REST se escribieron después. Esta entrada cubre las tres.

**Lo construido**: el agregado `WorkOrder` de Generación 2 con siete eventos; `WorkOrderService` con las reglas que exigen preguntar a otros tres módulos; la persistencia con conciliación del alcance; y nueve operaciones REST con catálogo de errores propio y grupo de OpenAPI. Reescrita [[dominio-orden-trabajo]] de arriba abajo.

**El hallazgo de la pasada, y no salió de leer código.** La tanda 1 había dejado listadas **siete reglas** que el esquema no podía defender. Al construir la tabla de estado de esas siete —columna por columna, regla contra código— apareció que **una no está construida**: `requireEquipmentBelongsTo` comprueba que el equipo sea del **cliente** de la orden, y nunca que su área sea de la **sede** de la orden. Una orden del cliente A en la sede Norte admite un equipo del cliente A que está en la sede Sur. Es **la misma mentira histórica que el módulo se diseñó para impedir**, un nivel más abajo, y ahora sí es explotable porque ya hay API que crea órdenes. El arreglo es barato: el `ServiceArea` con su `idSede` **ya está cargado en esa misma línea**.

**La lección de método.** La lista de siete se escribió sin columna de estado, y **una lista de reglas sin columna de estado es una lista que nadie contrasta**: se lee como inventario, no como pendiente. La tabla que ahora tiene la nota existe para eso. Es exactamente la disciplina que `CONVENCIONES.md` ya enunciaba —«los defectos aparecen al comparar»— aplicada al propio wiki en vez de a dos módulos.

**Segundo hallazgo, por `grep`.** **Nada prueba la persistencia de este módulo**: ni una mención de `WorkOrderPersistenceAdapter`, `WorkOrderPersistenceMapper` ni `WorkOrderRepository` en `src/test`. Queda sin ejercer la pieza con más lógica fuera del dominio —la conciliación que desactiva filas en vez de borrarlas y reactiva con el área nueva— y el `@Query` que filtra por `estadoActivo`. `client`, `location` y `person` sí tienen su `…PersistenceAdapterTest`; `equipment` tampoco. **La ausencia se repite en los dos módulos más recientes**, que es lo que la convierte en patrón y no en olvido.

**La centinela hizo su trabajo de punta a punta.** `lasAutoridadesDeWorkOrderSiguenSinModulo` se puso roja con el primer controlador, tal como estaba anunciado en `CONVENCIONES.md`, en [[modelo-de-permisos]] y en [[dominio-orden-trabajo]]; se retiró en ese mismo commit junto con el `filter` que eximía a `work-order` en `ningunaAutoridadSobra`. La sustituyen dos pruebas, y la segunda —**`assign` es la única operación que exige `work-order.assign`**— es la que impide que la separación entre repartir trabajo y alterarlo desaparezca en silencio. Segundo caso del patrón de omisión consciente cerrándose limpiamente, tras el de `equipo_cliente`.

**Qué se verificó por mutación, porque el verde no basta.** Se rompió el patrón del grupo de OpenAPI (`/work-orders/**` → `/workorders/**`) y `recursoDocumentado` falló; se aflojó la guarda de filtros excluyentes (`> 1` → `> 99`) y `filtrosExcluyentes` falló. Producción restaurada en los dos casos. Importa sobre todo en el primero: **un grupo que no casa con ninguna ruta no da ninguna señal**, y una prueba mal escrita contra un fallo silencioso deja el proyecto donde estaba pero creyendo lo contrario. Ampliado [[openapi-swagger]] con el caso y con el detalle de que `/work-orders/**` casa también con `/work-orders` a secas.

**El marcador de requisitos sube de 8 a 11, no a 15.** El módulo está terminado y aun así **cuatro de sus siete RF siguen abiertos**, porque describen un formulario y no hay frontend: RF-03, RF-04, RF-06 y RF-07 son pasos de interfaz. El criterio aplicado queda escrito en [[hoja-de-ruta-producto]] para que se pueda discutir: cuenta como implementado lo que el backend satisface por completo; dar por hecho lo demás inflaría la cifra y haría desaparecer de la cuenta el trabajo de frontend sin haberlo hecho.

**Confirmado de paso**: el ciclo del grafo declarado de la ERS —RF-05 → RF-07 → RF-06 → RF-05— **no bloqueó nada**. El módulo se construyó ignorándolo, y las tres piezas salieron juntas como partes del mismo agregado, tal como [[hoja-de-ruta-producto]] predijo. Es la prueba de cuál de los dos grafos manda.

**Conteos, todos medidos borrando `target/surefire-reports` antes.** `main` (`97ef74f`) da **580** elementos `<testcase>` y 44 clases; `ceadba1` da **612** y 46. Los puntos intermedios se marcan como **derivados** en [[dominio-orden-trabajo]] y no como medidos: 580 + 17 + 14 + 1 = 612 cuadra con lo medido, y ese +1 es el centinela sustituido por dos pruebas. Cero fallos en ambos.

**Una cifra mal dicha, anotada en vez de reescrita.** El cuerpo del commit `ceadba1` dice «ocho operaciones»; **son nueve**, contadas sobre las anotaciones de mapeo. El error es del mensaje, no del código, y queda en [[dominio-orden-trabajo]] porque un cuerpo de commit no se corrige sin reescribir la historia.

**Deuda.** Una fila propia nueva —la persistencia sin pruebas—, de **24** a **25**. La fila de las siete reglas pendientes se reescribe: ya no son siete pendientes sino **una**, con severidad media y explotable. Y la fila de las autoridades de `work-order` sin módulo detrás **se cierra**.

**Dos hallazgos más, aparecidos en la revisión de esta misma pasada y no al escribirla.** Al remedir para poder citar las tres fuentes de conteo: (1) los `.txt` dan **418 tanto en `24e7640` como en `main`** —ni un punto de diferencia— pese a las **39** pruebas que añade `WorkOrderTest`, porque esa clase tiene 13 clases `@Nested` y **ninguna prueba suelta**; es el caso extremo de algo que esta nota ya decía, y el mejor argumento para no volver a citar esa fuente. (2) El desajuste `<testcase>` – atributo `tests=` **pasa de 2 a 3 por primera vez**: hay un segundo caso además de `CatalogAggregatesTest`, y es `WorkOrderServiceTest`, donde **`ordenInexistente` aparece en dos clases `@Nested`**. El desajuste no es una rareza de una clase: **crece sin avisar** según se escriben pruebas. Ampliado [[stack-spring-boot-4-particularidades]] con una tabla de dos columnas más.

**Tres notas más llevaban afirmaciones ya falsas y se corrigieron dejando constancia**, encontradas al revisar y no al escribir: [[regla-traslado-mismo-cliente]] seguía diciendo «seis construidas y siete previstas» y situando `main` en `55e0a5d`; [[checklist-reutilizacion]] daba `feat/work-order-schema` como la rama de código fuera —**tercera corrección a esa misma línea en dos días**—. Esa reincidencia es el dato: el estado de las ramas caduca más rápido que nada de lo que este wiki escribe.

**Y una predicción del wiki se cumplió literalmente.** [[regla-traslado-mismo-cliente]] había escrito que una de las siete reglas «es hermana de ésta y probablemente reutilice el mismo `findOwningClient`». Lo reutiliza exactamente, en `requireEquipmentBelongsTo`. Es la mejor justificación que ha dado el proyecto de haber publicado ese contrato como **puerto síncrono** y no como evento: la segunda pregunta llegó cuatro días después y no hubo que tocar `client` para contestarla.

**Tocadas**: [[dominio-orden-trabajo]] (reescrita), [[deuda-tecnica-y-riesgos]], [[modelo-de-permisos]], [[hoja-de-ruta-producto]], [[openapi-swagger]], [[decisiones-tecnicas-malphasos]], [[stack-spring-boot-4-particularidades]], [[regla-traslado-mismo-cliente]], [[checklist-reutilizacion]], `index.md` y el `CONVENCIONES.md` de la raíz.

**Comprobado al empezar, siguiendo la lección de la pasada anterior**: `git branch --contains` y `git log main..HEAD` antes de escribir nada sobre ramas. Sirvió — el `CONVENCIONES.md` de la raíz situaba `main` en `55e0a5d` con una sola rama fuera, y **estaba en `97ef74f`** con el dominio ya dentro.

## [2026-09-13] ingest | La regla que faltaba, cerrada el mismo día que se encontró

**Cierre de lo que abrió la entrada anterior.** La regla 1 de las siete —que el área del equipo sea de la sede de la orden— estaba sin construir, y se cerró con `0cf56c5` en `fix/work-order-headquarter-scope`. Van **siete de siete**, y trece de trece contando todo el proyecto.

**El arreglo fue tan barato como se había previsto**: el `ServiceArea` ya se cargaba en esa misma línea para comprobar que el área estuviera abierta, así que la sede venía en la mano y la comprobación **no cuesta ninguna consulta más**. `requireEquipmentBelongsTo` pasa a `requireEquipmentInScopeOf` y recibe la orden entera, porque ahora mira dos de sus campos.

**Lo que no estaba previsto, y es lo que vale de esta tanda: la guarda nueva subsumía a la vieja.** Un equipo de otro cliente está **necesariamente** en otra sede —si el área fuera de la sede de la orden, su cliente sería el de la sede, y la clave foránea compuesta garantiza que ése es el de la orden—, así que las dos negativas son ciertas a la vez y **una de las dos comprobaciones iba a quedar muerta**.

La salida no fue borrar ninguna sino **ordenarlas**: el dueño se comprueba delante, porque «es del cliente X y la orden es del cliente Y» es más informativo que «está en la sede X», y con ese orden las dos siguen alcanzables y cada una contesta su caso. Lo fija `elClienteSeCompruebaAntesQueLaSede` con un `withMessageNotContaining`.

**La regla general que deja**: cuando una guarda nueva subsume a una vieja, la pregunta no es cuál borrar sino **en qué orden dejarlas**. Borrar la subsumida pierde el mensaje bueno; ponerla delante la mata en silencio. Registrada en [[decisiones-tecnicas-malphasos]].

**Verificado por mutación, dos veces.** Anular la comprobación de sede deja **una sola** prueba en rojo, `equipoDeOtraSedeDelMismoCliente`. Intercambiar las dos guardas pone en rojo `elClienteSeCompruebaAntesQueLaSede` y además hace que Mockito estricto marque `findOwningClient` como estubado que sobra — dos señales independientes del mismo cambio. Producción restaurada en ambos casos.

**Y una prueba que fija un estado imposible**, encontrada de paso: `equipoDeOtroCliente` monta un área **en la sede de la orden** pero **de otro cliente**, que no puede existir. Sigue en verde porque la guarda de cliente salta primero, pero lo que pincha no es alcanzable por el API. No se tocó; queda anotado en [[dominio-orden-trabajo]].

**Conteo**: **614** elementos `<testcase>`, 46 clases, cero fallos, medido borrando `target/surefire-reports` antes. Las otras dos fuentes **no se midieron** en este punto y se dejan con guiones en [[stack-spring-boot-4-particularidades]] en vez de deducirlas: restar de memoria es cómo se inventan cifras.

**Deuda: el total se queda en 25 filas y las abiertas bajan de 18 a 17.** La fila de la regla se tacha el mismo día que se abrió, pero no desaparece: el porqué de un defecto sigue valiendo después de arreglarlo, y por eso esa lista cuenta **lo registrado y no lo pendiente** — algo que este log había dado por equivalente. La que queda viva de esta tanda es **la persistencia de `work-order` sin pruebas**, ahora lo único del módulo sin cubrir.

**Tocadas**: [[dominio-orden-trabajo]], [[deuda-tecnica-y-riesgos]], [[regla-traslado-mismo-cliente]], [[decisiones-tecnicas-malphasos]], [[stack-spring-boot-4-particularidades]] y el `CONVENCIONES.md` de la raíz.


## [2026-09-13] lint | Los tres merges, y la vez que no deducir salió a cuenta con número

**El quinto módulo entra en `main`.** Tres merges `--no-ff` en orden —`e69437d` las tandas 3 y 4, `1adc2fc` la regla que faltaba, `b9563a9` las dos pasadas de wiki— y las cuatro ramas borradas, incluida `feat/work-order-application`, que llevaba colgada apuntando a un ancestro. **No queda ninguna rama de código fuera de `main`**, por primera vez desde el 2026-09-09.

`main` (`b9563a9`) mide **614** elementos `<testcase>`, 46 clases, cero fallos, cero errores, cero omitidas. `git fsck --strict` limpio, sin stashes y sin worktrees.

**Y una comprobación que esta vez tiene número.** La pasada anterior dejó dos casillas de la tabla de conteos **con guiones**, diciendo que afirmar «**611 y 435**» sin haberlos medido sería inventarlos. Medidos tras el merge: **611 y 433**. **Uno acertado y el otro no.**

El fallo habría sido en los `.txt`, y por la razón que [[stack-spring-boot-4-particularidades]] lleva repitiendo: las dos pruebas nuevas viven dentro de una clase `@Nested` ya existente, así que esa fuente **no las cuenta y se queda clavada en 433**. Quien dedujera «+2 en todo» se equivocaría justo en la fuente cuyo comportamiento raro está documentado en la misma nota, tres párrafos más arriba.

**Es la mejor defensa que ha tenido la regla de no deducir**: no falla por principio, falla **una de cada dos veces**, y la que falla es la que uno cree tener entendida. Registrado con la cifra en [[stack-spring-boot-4-particularidades]].

**Cuarta corrección en dos días a la línea de «qué rama queda fuera»**, en [[checklist-reutilizacion]] y en el `CONVENCIONES.md` de la raíz. A estas alturas la reincidencia ya no es un dato sino una regla: **toda pasada de wiki empieza por `git log main..HEAD`**, nunca por lo que dijo la anterior.

**Tocadas**: [[stack-spring-boot-4-particularidades]], [[dominio-orden-trabajo]], [[checklist-reutilizacion]] y el `CONVENCIONES.md` de la raíz.

## [2026-09-13] lint | El hash de una rama no se escribe; el de un commit sí

**Convención nueva, nacida de un error propio cometido horas antes.** El `CONVENCIONES.md` de la raíz fijó el hash de `main` cuatro veces en dos días y las cuatro caducó. La cuarta es la que enseña algo: **caducó por su propio merge** — la pasada de wiki que anotaba «`main` está en `b9563a9`» dejaba `main` en otro commit al mergearse. Es regresión infinita, y ninguna cantidad de `git log main..HEAD` la arregla, porque la regla anterior —comprobar antes de escribir— no cubre el caso en que **escribir es lo que invalida el dato**.

La distinción que queda, y que no es «no escribir hashes»:

| Un hash que nombra… | ¿Se escribe? |
|---|---|
| un commit concreto: una medición, un merge, un arreglo | **Sí.** Es inmutable y verificable |
| el valor de hoy de un puntero que se mueve (`main`) | **No.** Caduca sola, y a veces al escribirla |

Por eso «614 medidos sobre `b9563a9`» y «mergeada por `1ef55cf`» **se quedan**, y las tablas de conteo pasan a nombrar **commits en vez de ramas**: una medición pertenece al commit donde se contó y ahí se queda, mientras que `main` apunta cada día a otro sitio.

**El log no se toca.** Sus entradas están fechadas y eran ciertas al escribirse; reescribirlas para que sigan siendo ciertas hoy sería justo lo contrario de lo que un registro cronológico hace. La regla vale para las notas que describen el presente, no para las que fechan el pasado.

**Tocadas**: `CONVENCIONES.md` de la raíz (con la tabla de la distinción), `SecondBrain/CONVENCIONES.md` (regla dura nueva), [[dominio-orden-trabajo]] y [[stack-spring-boot-4-particularidades]].


## [2026-09-13] ingest | La persistencia probada, y el defecto que la prueba destapó antes de pasar

**Cierra el último pendiente del quinto módulo.** `WorkOrderPersistenceAdapterTest`, **10 pruebas** contra un PostgreSQL real vía Testcontainers. Hasta hoy `grep` sobre `src/test` no devolvía ni una mención del adaptador, el mapper ni el repositorio de `work-order`.

**La primera ejecución no falló por una aserción: falló con `LazyInitializationException`.** `WorkOrderPersistenceAdapter` **no llevaba `@Transactional`** y el mapper recorre el alcance, que es una colección perezosa con `open-in-view` desactivado.

**Por qué sobrevivió a cuatro tandas**: `WorkOrderService` siempre abre transacción, así que desde el API no se veía. El adaptador era inservible por su cuenta **y nadie lo llamaba por su cuenta, precisamente porque no había pruebas**. El defecto y su invisibilidad tenían la misma causa. De los **tres** adaptadores con colecciones propias —`client`, `person`, `work-order`— era el único sin la anotación, y el de `client` hasta lo explica en su javadoc.

**Se arregló en producción, no se rodeó en la prueba.** Envolver el test en una transacción lo habría puesto en verde describiendo el defecto en vez de detectarlo, que es exactamente lo que `CONVENCIONES.md` prohíbe. La regla entró como convención de persistencia: **un adaptador que mapea una colección perezosa lleva `@Transactional`**; sin él depende de que el llamante abra una, y eso es una dependencia que el tipo no declara.

**Qué comprueban las diez**, y por qué no es el ida y vuelta: la **conciliación del alcance**. Retirar deja la fila **inactiva y no la borra**; readmitir **reactiva esa misma con el área nueva** y sigue habiendo una sola; `toDomain` no vuelve a cargar lo retirado; guardar dos veces no duplica; un traslado del equipo no reescribe el área congelada —comprobado ahora desde el lado de la orden, no solo desde el del esquema—; y `findByEquipment` ignora lo que salió. **Las comprobaciones van contra la tabla con SQL directo**: preguntarle al agregado lo contestaría el mapper, que es la pieza bajo prueba.

**Cuatro mutaciones, cuatro señales.** Quitar la lectura previa del adaptador, quitar el filtro `estadoActivo` del `@Query`, dejar de desactivar la fila que sale, y no actualizar el área del readmitido. Cada una la detecta la prueba que le toca, y dos de ellas **solo** esa prueba.

**Un tercer dato sobre el conteo, y ya es tendencia.** Los `.txt` dan **433 antes y después** de añadir diez pruebas, porque la clase nueva es enteramente `@Nested`. Sumando la tanda: de **83** pruebas añadidas desde `24e7640`, esa fuente recoge **15**. Deja de ser una rareza y pasa a ser **una fuente que miente por defecto** con el estilo de pruebas de este proyecto. Registrado con la tabla en [[stack-spring-boot-4-particularidades]].

**Deuda: 27 registradas, 17 abiertas.** Se cierran dos —la persistencia sin probar y el `@Transactional` ausente— y se abre una: **`equipment` tampoco prueba su persistencia**, la misma ausencia que se señaló en dos módulos y se cerró en uno.

**Tocadas**: [[dominio-orden-trabajo]], [[deuda-tecnica-y-riesgos]], [[stack-spring-boot-4-particularidades]] y el `CONVENCIONES.md` de la raíz.

## [2026-09-13] ingest | El frontend se decide, y una etiqueta `reusable:alta` resulta ser falsa

**El proyecto pasa del backend al frontend.** Hay documento oficial —`Documentation/FrontendDesign/`— y un manual de marca que llegó ya hecho. Esta pasada traduce lo decidido a algo con lo que se pueda escribir código: **dos notas nuevas**, [[arquitectura-frontend-malphasos]] y [[sistema-de-diseno-malphasos]].

**Angular**, y la razón no fue popularidad: **correspondencia estructural con el backend**. Un servicio inyectable es un puerto, un guard de ruta es un `@PreAuthorize`, una carpeta de feature es un módulo hexagonal. Las dos mitades se razonan con el mismo vocabulario. Alrededor: Tailwind con los tokens de la marca, spartan/ui sobre Angular CDK, TanStack Query aislado tras un servicio por módulo, y **cliente TypeScript generado desde OpenAPI** — si el backend renombra un campo, el frontend deja de compilar.

**Lo que cuesta, y es el hallazgo de la pasada.** [[integracion-keycloak-frontend]] llevaba desde el 2026-08-27 marcada `reusable:alta`, con la frase «portable sin cambios estructurales, sólo actualizando `realm`/`clientId`». **Elegir Angular la vuelve falsa**: esas cuatro piezas son React. Es la **primera vez que el proyecto desecha algo clasificado como reutilizable**.

La corrección no fue bajar la etiqueta y callar. Se separó **lo que se porta de lo que no**: el código no, pero sí el diseño —cuatro responsabilidades únicas, PKCE, refresco anticipado, cierre de sesión si el refresco falla—. `reusable:media`, y dicho por qué.

**La lección de método**: `reusable:*` **describe una relación entre dos sistemas, no una propiedad del código evaluado**. Cambia el sistema de destino y la etiqueta caduca sin que el archivo evaluado se haya tocado. Las 49 notas anteriores asumían implícitamente un destino que nadie había fijado.

**Corregidas por el mismo motivo** [[arquitectura-frontend]] —decía que el starter de Vite era «una base moderna y válida para arrancar MalphasOS»— y [[hoja-de-ruta-producto]], que daba por bueno que «el arranque no parte de cero». Las dos acertaban en lo que era responsabilidad de MalphasOS decidir; ninguna podía saber qué se decidiría.

**El sistema visual tiene autoridad nueva y no es este wiki.** El manual de marca manda: radio cero, espaciado en múltiplos de 8, retícula 12/24/48, una sola familia (Archivo), iconos Lucide, y el acento **nunca por encima del 10 %** de una composición. Su decisión más contraria al reflejo habitual: **los estados operativos se distinguen por peso tipográfico y regla, no por colores nuevos** — una orden vencida **no se pinta de rojo**.

**Y un número que hubo que medir.** El manual ya traía su regla de contraste —`#AE1800` para texto en acento— y funciona: 6,41:1. Pero describe la acción principal como «relleno acento», y con `#EC3013` de relleno la etiqueta da **3,76:1**: el control más repetido de una interfaz no alcanzaría el AA que el propio documento declara. Se resolvió **extendiendo la regla del manual** a ese caso, no corrigiéndola. Todas las cifras están medidas, no estimadas — la primera estimación que hice a ojo, «4,3:1», resultó ser 4,2 y encima miraba el par equivocado.

**Aplazado con su porqué**: instalación en el dispositivo y consulta sin conexión. No incumple nada —la ERS pide acceso desde el teléfono **sin instalar nada nativo**, y una web responsiva lo cumple literalmente—. La **escritura** sin conexión sí tiene bloqueo real: sin idempotencia ni bloqueo optimista en el backend, un reintento duplicaría órdenes.

**Tocadas**: [[arquitectura-frontend]], [[integracion-keycloak-frontend]], [[hoja-de-ruta-producto]], `index.md` y el `CONVENCIONES.md` de la raíz, que decía que el código vivía **exclusivamente** en `malphasos/`. **Nuevas**: [[arquitectura-frontend-malphasos]], [[sistema-de-diseno-malphasos]].

## [2026-09-13] ingest | La escalera de usuarios, y un rol que llevaba meses esperando titular

**Quién puede crear a quién.** El SuperUsuario da de alta administradores, representantes de cliente e ingenieros; el administrador solo representantes; el resto, a nadie. Y el SuperUsuario **se crea a mano en Keycloak**, nunca por el API. Crear, editar y retirar siguen la misma escalera.

**Lo mejor de esta tanda es que el código la había anticipado.** El javadoc de `ApiAuthority` decía que `super.admin.full` y `admin.full` se conservaban separados «porque el realm los distingue y un realm futuro podría dar al segundo capacidades que el primero no tenga». **Ese futuro era esto.** No hubo que inventar el mecanismo: estaba escrito, esperando. La expansión pasó de un escalón a dos y `super.admin.full` dejó de ser un rol sin consecuencia.

**Basta una autoridad nueva, no cuatro**, porque la escalera tiene dos peldaños. `super.person.write` cubre a la gente de la casa —ingenieros y administradores— y `person.write` a la del cliente —representantes y encargados—. **La línea tiene significado**, y es la misma que sostendrá el cupo cuando llegue.

**El prefijo `super.` no es decorativo**: marca lo que `admin.full` no concede. Tres invariantes lo sostienen, y el segundo es el que importa: **que los dos conjuntos no se toquen**. Añadir `super.person.write` a `RESOURCE_AUTHORITIES` parece lo correcto —es una autoridad más— y devolvería al administrador justo lo que se le acaba de quitar, sin que nada fallara. Ahora falla.

**No se llama `engineer.write`**, y merece quedar por qué: `engineer.read` y `engineer.assign` ya existen y hablan del *encargado* —lo que el código llama `Manager` y la ERS «profesional responsable»—. Ese nombre ya estaba tomado por otro concepto, y meter un cuarto sinónimo en esa confusión habría sido peor que un nombre largo.

**La primera excepción a «una autoridad literal y una sola».** Al crear, el tipo está en la ruta; al editar o retirar, está en la fila, y una anotación estática no lo puede saber. Esas operaciones delegan en `PersonWriteGuard`.

Tres decisiones que sostienen esa excepción sin que se convierta en un agujero:

1. **El bean vive en el módulo de personas, no en `bootstrap`.** Se comprobó que `bootstrap` no importa nada de ningún módulo de negocio —lleva cinco siéndolo— y ponerlo allí habría invertido esa dependencia.
2. **El servicio sigue sin saber quién llama.** Ninguna clase de `application` ni de `domain` toca `Authentication`.
3. **La excepción está acotada por una prueba.** Solo vale bajo `/v1/api/persons` y recibiendo un identificador. Sin ese límite sería la vía para esquivar cualquier autoridad literal.

**Dos puertas traseras cerradas.** Los correos y teléfonos delegan igual, porque cuelgan de la misma persona: sin eso se podían editar los contactos de alguien a quien no se puede editar. Y `POST /persons` —la única alta que no crea usuario— aceptaba cualquier tipo, incluido `SUPER_ADMIN`: no daba acceso, pero permitía escribir una fila que dice ser algo que no es. Ahora solo admite encargados.

**Dos hallazgos al escribir las pruebas.** La **validación del cuerpo corre antes que `@PreAuthorize`**, de modo que un cuerpo incompleto responde 400 y tapa el 403: la prueba del super usuario **pasaba sin ejercer nada**, porque el ayudante `autorizado()` acepta cualquier cosa por debajo de 500. Y `SecurityIntegrationTest` tenía un doble sin estubar que ahora devolvía `null` al guard, con un 500 que se habría leído como fallo de autorización. Misma familia que la prueba que pasaba comparando `null` con `null`.

**`MANAGER` no era un cabo suelto y `SUPER_ADMIN` sí lo era.** Se investigaron los dos antes de decidir: el primero tiene razón escrita —«un encargado puede existir como contacto de una sede sin acceder nunca a la aplicación»—, uso real y respaldo de esquema en tres capas. Del segundo, `RoleType` dice que el alta «quedó sin implementar en el original». Desde hoy esa ausencia es deliberada. Registrado en [[dominio-persona-identidad]].

**Y una discrepancia de la ERS, encontrada al agotar la fuente antes de nombrar nada.** El documento declara **tres roles** —SuperUsuario, Administrador, Ingeniero Técnico— y el representante de cliente no figura como rol en ninguna parte, pese a tener grupo en el realm y ser el centro de la escalera. Es trabajo de `Documentation/` y queda anotado como deuda.

**Conteo**: **636** elementos `<testcase>`, 47 clases, cero fallos. Ocho pruebas nuevas, verificadas por mutación: el ingeniero cambiado de peldaño, el alta devuelta a `person.write`, y el bean usado en otro módulo para esquivar una autoridad —esta última la caza la prueba que acota la excepción—.

**Tocadas**: [[modelo-de-permisos]], [[dominio-persona-identidad]], [[deuda-tecnica-y-riesgos]], `index.md` y el `CONVENCIONES.md` de la raíz.

## [2026-09-26] ingest | El frontend habla con el API: dos defectos que solo aparecen con un navegador delante

**El backend llevaba cinco módulos y la batería en verde sin que una sola petición hubiera salido de un navegador.** Al abrir el frontend por primera vez contra el API real aparecieron dos fallos seguidos, y **ninguno de los dos era detectable con lo que había**: `curl` no manda `Origin` y `MockMvc` no tiene inyector de Angular.

**1. Página en blanco, sin redirección y sin error.** `withAutoRefreshToken` exige `AutoRefreshTokenService` y `UserActivityService`, y **ninguno es `providedIn: 'root'`**. Sin declararlos en `providers`, el inyector falla antes de pintar nada. Está en el README de la librería: no leerlo costó el fallo entero.

**2. `Access-Control-Allow-Origin missing`, con un 401 que engañaba.** El 401 **no era del token: era del preflight.** Un `OPTIONS` no lleva cabecera de autorización, así que caía en `anyRequest().authenticated()` y se rechazaba antes de que nadie mirase ningún token. El navegador no informa de ese 401, informa de la cabecera que falta, y eso manda a buscar al sitio equivocado.

**La configuración de CORS va fuera de las dos cadenas de seguridad**, y es la decisión que más se va a agradecer: CORS es un asunto del navegador, no de si el API exige token. Dentro de `SecurityConfig` habría dejado el frontend roto justo con `app.security.enabled=false`, que es el modo en que se desarrolla sin Keycloak.

**Sin comodín y sin `allowCredentials`.** Los orígenes se declaran en `app.security.cors.allowed-origins`, y el `@Value` **no tiene valor por omisión**: desplegar sin declararlos tumba el arranque en vez de servir una política equivocada en silencio. Las cookies no se piden porque el token va en la cabecera — misma razón que CSRF apagado.

**La mutación dijo algo que no se esperaba.** Quitar `.cors(...)` de `SecurityConfig` **dejó las tres pruebas en verde**: `HttpSecurityConfiguration.applyCorsIfAvailable` lo aplica solo en cuanto existe el bean. La línea es redundante hoy y **se queda**, porque esa aplicación automática mira el tipo **concreto** del bean: si `CorsConfig` devolviera una lambda, dejaría de aplicarse y el preflight volvería al 401 sin que nada en la cadena cambiase. Las otras dos mutaciones fueron las que contaron: sin el bean y con `.cors(...)`, **200 sin cabeceras** —borra el 401, que era la única pista—; sin las dos cosas, **401**, el número exacto del navegador.

**Una prueba que sigue verde al quitar una línea no es mala si fija el resultado y no el mecanismo.** Lo malo habría sido no hacer la mutación y creer que fijaba el mecanismo.

**Y una trampa de configuración que habría tumbado la batería entera**: `src/test/resources/application.yaml` **no hereda** del de producción, lo sustituye. Sin la clave también allí, el `@Value` no resuelve y no arranca ni una prueba, tenga o no que ver con seguridad.

**Verificado contra el servidor en marcha**, que es donde ocurrió el fallo: preflight permitido **200** con las cuatro cabeceras y `Max-Age: 1800`; origen ajeno **403 sin cabeceras**; `GET` sin token **401 pero ya con la cabecera de origen**, de modo que el navegador puede leer el estado real.

**Deuda nueva anotada, y una es de seguridad**: el realm de desarrollo lleva **credenciales en claro versionadas** —la contraseña de `dev.admin` y los secretos de los clients—, dentro de `origin/main` desde el 2026-08-28. Empujar no expone nada nuevo; lo que hay que hacer es rotarlas y sacarlas a variables de entorno.

**Conteo**: **645** elementos `<testcase>`, 49 clases, cero fallos, medido borrando `target/surefire-reports` antes. Tres pruebas nuevas.

**Tocadas**: [[seguridad-keycloak-backend]], [[integracion-keycloak-frontend]], [[deuda-tecnica-y-riesgos]], `index.md` y el `CONVENCIONES.md` de la raíz.

## [2026-09-26] ingest | La sección de clientes, cerrada entera, y una autoridad inventada que nadie habría notado

**Se cerró lo que estaba a medias**, por decisión del usuario y antes de seguir con órdenes de trabajo: ficha del cliente, edición, retiro, correos y teléfonos, **sedes**, **áreas de servicio** y **encargados**. Nueve pantallas, cuatro puertas al API —una por agregado— y **154** pruebas de frontend, de 84 que había.

**No fue un desvío del plan sino su condición**: sin sedes ni áreas no hay dónde registrar un equipo, y una orden de trabajo **solo puede tocar equipos de áreas de su propia sede**. El formulario de órdenes —que es lo que cierra sus cuatro RF de pantalla— no tenía contra qué construirse.

**El hallazgo de la tanda: una autoridad que no existe no falla, desaparece.** Las rutas de las sedes se escribieron con `headquarter.read` y `headquarter.write` por simetría con el nombre del recurso. **Las sedes las protege `client.*`**: el guard mandaba a `/sin-permiso` y la pantalla quedaba inalcanzable para todo el mundo, incluido el administrador, sin un solo error en ninguna consola. Es exactamente el riesgo que [[arquitectura-frontend-malphasos]] tenía escrito —«dos listas escritas por separado se desincronizan»— ocurriendo en la primera ocasión que tuvo.

**Ahora lo caza una prueba**, y se escribió **antes** de corregir la ruta para verla fallar: lee `app.routes.ts`, extrae cada `requiereAutoridad('…')` y exige que el realm conceda ese rol. La acompaña otra que comprueba que la lista no esté vacía, porque una prueba que recorre cero elementos pasa siempre y este proyecto ya sabe lo que cuesta eso.

**Un servicio por agregado y no por módulo.** El wiki decía «servicio del módulo»; con cuatro agregados dentro de `client` habría sido el archivo más grande del frontend, y el backend tampoco lo hace así. Lo que la regla protege —que TanStack Query no se escape a las pantallas— se cumple igual.

**Las claves de caché son jerárquicas**, y es lo que evita el defecto de «creé algo y no aparece hasta recargar»: invalidar `['clientes']` alcanza por prefijo a la ficha y a su lista de sedes. Lo que **no** se invalida también está decidido: renombrar un área no toca la rama del cliente. Verificado por mutación: quitar la invalidación del alta de un correo pone roja la prueba que exige la segunda consulta.

**Zoneless: `whenStable()` no se puede esperar con una petición en vuelo.** Una petición sin responder cuenta como tarea pendiente, así que las primeras pruebas **agotaban su tiempo en vez de fallar**, que es la peor forma de romperse. Se espera por tics vacíos, y los ayudantes viven ahora en `src/testing/pantalla.ts` en lugar de copiarse en cada pantalla.

**Dos decisiones de honestidad en pantalla.** Los identificadores no se escriben nunca: país y ciudad se eligen de un catálogo, y **las ciudades se recortan al país del cliente** —abrir una sede colombiana en Lima es un error que nadie detecta hasta que un ingeniero viaja—. Y se distingue **«no tiene» de «no se pudo consultar»**: el grupo `clients` no tiene `location.read`, y decir «sin país» de un cliente que sí lo tiene es afirmar algo falso.

**Lo que el backend no da, se dice.** `ManagerResponse` no trae el nombre de la persona y `GET /managers` no admite filtros, de modo que la pantalla se trae las dos listas y las cruza en memoria. Queda como deuda con su porqué: la alternativa eran N peticiones para pintar N filas.

**Aplazado con nombre**: los **representantes** se ven y no se tocan. Darlos de alta exige la sección de personas, que no existe, y arrastra el **cupo** por cliente que el usuario aplazó el 2026-09-13.

**Conteo**: **154** pruebas de frontend, 20 archivos, cero fallos; el backend sigue en **645**. El `build` de producción no da avisos de presupuesto: cada pantalla es su propio trozo diferido, el mayor de 12,5 kB.

**Tocadas**: [[arquitectura-frontend-malphasos]], [[deuda-tecnica-y-riesgos]], [[hoja-de-ruta-producto]], `index.md` y el `CONVENCIONES.md` de la raíz.

## [2026-09-26] ingest | Todo lo necesario para crear un equipo, que resultó ser una cadena de cinco piezas

**Lo que se pidió fue «crear un equipo» y lo que hacía falta eran seis pantallas**, porque un equipo de un cliente no se crea solo: exige un **modelo**, que exige un **equipo del catálogo** —tipo × marca— y un **fabricante**. Saltarse cualquiera deja el desplegable siguiente vacío, y sin explicación nadie adivina que lo que falta está dos pantallas atrás.

**La cadena es ahora la pantalla.** El catálogo es una sola página con sus cinco secciones **en el orden en que se usan**, y cada una dice para qué sirve la siguiente. Se prefirió a cinco rutas separadas por una razón concreta: las cinco listas se consultan juntas de todos modos —ninguna respuesta trae nombres y hay que cruzarlas para pintar una fila legible—, así que separarlas costaría lo mismo y esconderería la dependencia.

**«Equipo» significa dos cosas y esa es la confusión probable del módulo.** `Equipment` es una **categoría** —«tensiómetro Welch Allyn»— y `ClientEquipment` es **una máquina**, con su serie. La pantalla lo dice en voz alta en lugar de dejar que alguien busque su tensiómetro en el catálogo.

**Cuatro listas para una etiqueta.** `ModelResponse` trae `idEquipo` e `idFabricante`; `EquipmentResponse`, `idTipoEquipo` e `idMarca`. Para ofrecer «Tensiómetro · Welch Allyn · Medtronic» en un desplegable hay que cruzar cuatro. Es la **segunda vez** que un módulo devuelve identificadores sin nombres —la primera fue `ManagerResponse`—, y por eso la fila de deuda pide decidirlo como convención antes de que sea la tercera.

**La modalidad de verificación tiene ruta propia y no está en el cuerpo de edición.** Un formulario que la incluyera parecería funcionar y el cambio se perdería en silencio, así que la pantalla de edición **no la ofrece** y hay una prueba que fija esa ausencia. El cambio se hace desde la lista, que es donde vive la operación.

**Lo obligatorio del alta son dos campos**, `idModelo` y `serie`, y los otros tres no viajan si están vacíos: una cadena vacía no es un número de inventario y un cero no es «no se sabe». Verificado por mutación —mandarlos siempre pone rojas dos pruebas—, igual que el recorte a piezas activas, porque el backend rechaza una referencia retirada.

**Instalar exige `equipment.assign`, no `equipment.write`.** Es del backend y la distinción es buena: repartir una máquina a un área no es editar un catálogo. La prueba de autoridades contra el realm cubrió las cuatro rutas nuevas sin que hubiera que tocarla, que es para lo que se escribió ayer.

**Aplazado con nombre**: el **traslado** entre áreas. El backend lo publica con su regla de no cruzar de cliente, y ofrecerlo exige listar las áreas de todas las sedes del cliente, que hoy son N peticiones. Es la pantalla que ejercería [[regla-traslado-mismo-cliente]] desde un navegador.

**Conteo**: **210** pruebas de frontend, de 154. El backend sigue en **645**. El `build` no da avisos: el catálogo entero son 28 kB en su propio trozo diferido, 5,2 kB transferidos.

**Tocadas**: [[dominio-equipo-mantenimiento]], [[deuda-tecnica-y-riesgos]], [[hoja-de-ruta-producto]], `index.md` y el `CONVENCIONES.md` de la raíz.

## [2026-09-26] ingest | El listado de equipos toma el sitio del catálogo, y el modelo se crea sin salir del alta

**Tres cambios pedidos por el usuario sobre lo que se había hecho horas antes**, y los tres apuntan a lo mismo: la cadena del catálogo existe, pero **no debe obligar a recorrerla**.

1. **El menú pasa a Clientes · Equipos · Catálogo.** Lo que se consulta a diario es qué equipos hay, no qué marcas existen. El listado muestra todos los equipos de cliente con serie, modelo, inventario, área y estado.
2. **El catálogo conserva entrada propia**, y era parte de lo pedido: administrarlo es una tarea aparte y no puede exigir empezar a registrar un equipo para crear una marca.
3. **El alta se entra por dos caminos** —desde un área, o desde el listado eligiendo cliente → sede → área encadenados— **con una sola pantalla**. Duplicarla habría sido la vía directa a que uno de los dos se quedara sin un arreglo.

**Y lo que más cambia el uso: el modelo se crea desde el formulario.** El panel elige o crea tipo, marca y fabricante, **deduce el equipo del catálogo** —reutiliza la combinación si existe, la crea si no— y deja el modelo nuevo ya seleccionado. Preguntar por ese eslabón intermedio obligaría a explicar un concepto que a quien rellena el formulario no le dice nada.

**Tres defectos encontrados construyéndolo, y ninguno se veía leyendo el código.**

**Un formulario reactivo no es una señal.** Un `computed()` sobre `getRawValue()` no vuelve a calcularse nunca —no tiene de qué depender— y con `OnPush` se queda con el primer valor para siempre. Los avisos del panel y los desplegables encadenados dependen de lo que se escribe, así que el valor entra con `toSignal(valueChanges)`.

**La invalidación de caché se esperaba, y en una cadena eso es un freno.** TanStack aguarda la promesa de `onSuccess` antes de resolver la mutación: cada una de las cinco altas se quedaba esperando la recarga completa del catálogo que no necesitaba. **Se descubrió porque la prueba se colgaba en el segundo paso en vez de fallar**, que es la peor forma de romperse — la misma familia que el `whenStable()` de ayer.

**Una consulta con identificador vacío es un 404 seguro.** Los desplegables encadenados empiezan sin elección, así que las consultas de sedes, áreas y ficha de área llevan ahora `enabled`. Sin eso se pedía `/clients//headquarters` y `/service-areas/` a secas.

**Dos deudas nuevas, las dos del backend y las dos con su razón escrita**: crear un modelo con sus piezas son hasta cinco llamadas **sin transacción** —si falla la última, la marca nueva ya existe, y la pantalla lo avisa porque es lo único que puede hacer—, y **no existe consulta para listar áreas**, de modo que el listado emite una petición por área distinta.

**Conteo**: **232** pruebas de frontend, de 210. Verificado por mutación lo que importa: quitar la reutilización de la combinación existente y quitar el borrado de la sede al cambiar de cliente ponen rojas sus pruebas.

**Tocadas**: [[dominio-equipo-mantenimiento]], [[arquitectura-frontend-malphasos]], [[deuda-tecnica-y-riesgos]], `index.md` y el `CONVENCIONES.md` de la raíz.

## [2026-09-26] ingest | Países y ciudades de verdad, y una restricción que se escribió mirando dos países

**Una instalación nueva nacía inservible**: sin países no se puede registrar un fabricante, sin ciudades no se puede abrir una sede, y **no hay pantalla que los cree** —el catálogo de ubicaciones se consulta, no se administra—. `V7__seed_location_reference_data.sql` siembra **249 países** y **1.350 ciudades**.

**Es una migración y no un script suelto, a propósito.** Flyway la aplica al arrancar la aplicación, una vez y en cualquier base de datos, que es lo que se pedía. Un script en `docker/postgres/init` solo corre **al crear el volumen**: no habría tocado la base de datos de este equipo y no volvería a correr nunca. **No hubo que tocar el `compose`** para conseguirlo; lleva ahora un comentario que lo explica, porque es donde alguien lo buscará.

**Los datos salen de fuentes verificables, no de la memoria**: los países de `iso-codes` —con su **traducción oficial al español**, que es lo que se lee en un desplegable—, los municipios de un dataset de departamentos de Colombia, y las capitales del dataset `mledoze/countries`. Las tres fuentes quedan escritas en la cabecera del archivo por si hay que regenerarlo.

**El hallazgo: `UQ_ciudad_nombre_por_pais` se escribió razonando sobre dos países.** El comentario de `V3__location.sql` dice que «el nombre de una ciudad solo es único dentro de su país: hay un Córdoba en España y otro en Argentina». Es cierto entre países y **falso dentro de Colombia**: hay **64 nombres de municipio compartidos entre departamentos**, con La Unión, Villanueva y Buenavista repetidos **cuatro veces cada uno**. La salida aplicada es meter el departamento en el nombre —`La Unión (Nariño)`—, porque es lo único que cabe sin cambiar el esquema; **lo correcto es un nivel de división administrativa**, y queda como deuda.

**Lo que vale de esto**: la restricción llevaba dos meses escrita y ninguna prueba la habría delatado, porque **todas creaban una ciudad cada una**. Apareció al contrastarla con los datos reales del país donde opera la empresa.

**Un defecto de la fuente, corregido al generar**: el dataset trae `Chibolo` dos veces en Magdalena. Los pares departamento-municipio se deduplican antes de etiquetarlos, de modo que la migración no depende de que la fuente esté limpia.

**La prueba lee el archivo y no la base de datos**, y también por un motivo concreto: **`LocationSchemaTest` hace `DELETE FROM ciudad` y `DELETE FROM pais` tras cada método**, así que al terminar esa clase el contenedor se queda sin datos de referencia. Contar filas habría dado una prueba que pasa o falla **según el orden de ejecución**. Queda avisado en la propia clase y anotado como trampa.

**Aplicado y comprobado sobre la base de datos en marcha**: `V7` en 97 ms, 249 países y 1.350 ciudades, de las cuales 1.103 municipios colombianos.

**Conteo**: **659** elementos `<testcase>`, 50 clases, cero fallos —14 nuevas—. Verificado por mutación: quitar la traducción al español y repetir un municipio ponen rojas las suyas.

**Tocadas**: [[dominio-ubicacion]], [[deuda-tecnica-y-riesgos]], `index.md` y el `CONVENCIONES.md` de la raíz.

## [2026-09-26] ingest | El desplegable deja de servir cuando el catálogo tiene 1.350 filas

Sembrar los datos de referencia dejó dos desplegables **inservibles el mismo día**: 249 países y 1.350 ciudades no se recorren con la vista, y en un teléfono se abren como una rueda infinita. Pedido por el usuario y construido como **un campo de texto que predice**, `app-buscador`, usado ya en seis pantallas.

**Tres decisiones, las tres del usuario y las tres con consecuencia técnica:**

1. **Sin escribir nada solo se ofrece lo ya usado**, y «usado» es **lo que se eligió antes en este navegador** —`localStorage`—, no lo que existe en los datos. Lo segundo se comparte entre compañeros pero cuesta consultas: hoy no hay forma de pedir todas las sedes de una vez. Lo elegido: instantáneo, empieza vacío, y cuando está vacío **lo dice** en lugar de parecer roto.
2. **Al escribir se busca en el catálogo completo.** Si solo buscara entre lo usado, el primer cliente de Boyacá no se podría registrar nunca.
3. **No inventa valores**: un texto que no case con nada deja el formulario sin valor y lo dice. Ofrecer «crear esta ciudad» abriría la puerta a un Bogota sin tilde junto al Bogotá con tilde.

**Se compara sin tildes y sin mayúsculas**, porque quien teclea «medellin» en un teléfono espera encontrar Medellín.

**Es un `ControlValueAccessor`** y no un campo con su propio protocolo: las seis pantallas lo usan con `formControlName` y sus validadores, como cualquier otro control. Y **muestra el nombre mientras guarda el identificador**, incluso cuando el catálogo llega después que el valor —el caso de toda pantalla de edición—, lo que exigió derivar el texto del valor con un efecto.

**Hallazgo del entorno: el corredor de pruebas de Angular no expone `localStorage`.** Acceder a él da `undefined`, aunque `document` sí está. El navegador lo tiene, así que lo que faltaba era el doble, y vive en `src/testing/almacenamiento.ts`. Es además la confirmación de por qué el código envuelve cada acceso: si el propio corredor puede no tenerlo, una ventana privada tampoco, y un formulario no puede caerse por no recordar la última elección.

**Dos trampas al reescribir las pruebas viejas**, y las dos por escribir el filtro de memoria: «Medellín» contiene «li», así que buscar Lima con «li» lo trae también; y no hay ninguna letra común a Bogotá, Medellín y Lima, de modo que el ayudante que «escribe una vocal para ver todo» no existía. Las pruebas ahora buscan por trozos concretos.

**Y una corrección de convención en el mismo repaso**: se marca **lo obligatorio** con `*` —más `aria-required` en el control y una leyenda por formulario— en vez de escribir «Opcional.» bajo cada campo que no lo es. Con tres opcionales en un formulario eran tres líneas de ruido, y el ojo aprende a saltarlas. El `*` va `aria-hidden`, para que un lector no lea «asterisco» quince veces.

**Conteo**: **265** pruebas de frontend, de 245 —17 del campo nuevo y su historial, 3 de la convención—. Verificado por mutación: ofrecer el catálogo entero sin escribir, y dejar de aceptar el nombre exacto tecleado, ponen rojas seis pruebas entre las nuevas y las viejas.

**Tocadas**: [[sistema-de-diseno-malphasos]], `index.md` y el `CONVENCIONES.md` de la raíz.

## [2026-09-26] ingest | El catálogo deja de ser una página con cinco secciones y pasa a ser cinco páginas

**Tercera corrección del mismo día sobre la misma pantalla**, y las tres del usuario: primero el catálogo dejó de ocupar la entrada del menú, luego sus cinco piezas se metieron en cajas con desplazamiento propio, y ahora **cada pieza tiene su propia página**. Lo que no funcionaba era el fondo: cinco listas que crecen no caben en una pantalla, y con treinta marcas registradas llegar a los fabricantes eran cuatro pantallas de desplazamiento.

**`Catálogo ▾` se despliega en la cabecera** con las cinco piezas, y ya dentro hay una **subnavegación** con las mismas, para saltar sin volver arriba. Las dos salen de `NAVEGACION`, que sigue siendo la única fuente del menú y de las rutas.

**Rutas y no pestañas con estado interno**, y el motivo es concreto: `/catalogo/fabricantes` **se puede enlazar, recargar y volver con el botón de atrás**. Unas pestañas habrían dejado una sola URL que siempre cae en la primera. Efecto lateral que no se buscaba y se agradece: **cada pieza es ahora su propio trozo diferido**, así que quien entra a corregir una marca no descarga los modelos.

**`NAVEGACION` gana un nivel, y con él una distinción:** una entrada con hijas **no tiene página propia**. `catalogo` no es una pantalla, es el sitio donde están cinco, de modo que su ruta redirige a la primera y el menú la pinta como **botón** y no como enlace. Entrar en `/catalogo` y encontrarse una página con solo un menú habría sido peor que no tenerla.

**El desplegable se abre al pulsar, no al pasar el ratón.** Con el ratón por encima no se navega con el teclado, y en un teléfono no hay ratón. Es un `button` con `aria-expanded` y `aria-controls`: sin lo segundo, un lector se entera de que algo se abrió pero no de qué.

**La tecnología predominante pasa a ser el mismo campo de búsqueda, con lista cerrada.** El backend la guarda como texto y aceptaría cualquier cosa, así que la restricción vive solo en el frontend: es vocabulario, no regla de negocio. Evita que la misma tecnología acabe escrita de cuatro maneras y que agrupar por ella deje de servir. **Lo que la empresa ya usa cuenta como autorizado y va primero**, porque su grafía vale más que una lista escrita de antemano y porque sin eso abrir un tipo antiguo dejaría el campo en blanco sin explicar nada. Para autorizar una nueva se edita `tecnologias.ts`.

**Y un modo que nació y murió el mismo día.** La primera versión lo hizo al contrario —texto libre con sugerencias— y el usuario pidió lo opuesto una hora después. El modo libre se retiró entero: sin ningún uso, es mantenimiento a cambio de nada. El truco que permite la lista cerrada con un dato de texto es que **el identificador de cada opción es su nombre**.

**Y una prueba que pasaba en vacío, detectada por mutación.** «Una tecnología ya usada está autorizada» usaba «Electrónica», que **también** está en la lista de partida: quitar del código la parte que autoriza lo ya usado no la ponía roja. Se reescribió con «Peristáltica», que solo cuenta por estar en los datos, y entonces sí falla. Es el mismo patrón que este proyecto ya tiene anotado dos veces —la prueba del traslado y la del super usuario—: **la mutación es lo único que distingue una prueba que verifica de una que acompaña**.

**Y un orden que no es alfabético ni casual**: «Tipos de equipo» va antes que «Marcas», pedido por el usuario. Es la pieza que describe **qué es** el aparato y la que más trabajo cuesta dar de alta —cuatro campos obligatorios y su ficha técnica—; la marca es un nombre. Es también el orden en que se piensa: primero «un tensiómetro», después «de qué marca». Con ello, entrar en `/catalogo` deja en `/catalogo/tipos`.

**Y las cajas de ayer se retiran, porque ya no hacen falta**: delimitaban una pieza de las otras cuatro, y ahora no comparten página. El tope de alto de cada lista se queda —subido a 512 px—, para que el formulario de alta no se vaya de la pantalla con mil filas.

**Conteo**: **274** pruebas de frontend, de 265. Las nuevas cubren el desplegable, la subnavegación, la redirección de `/catalogo` y que **solo se vea una pieza a la vez**. Verificado por mutación: quitar la redirección y escribir mal el destino de una hija ponen rojas seis.

**Tocadas**: [[arquitectura-frontend-malphasos]], [[dominio-equipo-mantenimiento]] y el `CONVENCIONES.md` de la raíz.

## [2026-09-27] ingest | Arranca la segunda tanda de equipment: con qué y cuántas veces se verifica

**Lo pidió el usuario para poder llenar el reporte**, y no era un cambio de formulario: **esos dos datos no existían** —ni en el esquema, ni en el dominio, ni en el contrato—. Un tipo decía *cómo* se verifica y no *con qué* ni *cuántas veces*. Es el primer trozo de la segunda tanda del módulo, la única parte del backend que seguía sin construirse, y el javadoc de `EquipmentType` la tenía anunciada desde el 2026-09-02.

**Cuatro decisiones de modelado se preguntaron antes de escribir una línea**, porque una columna es caro cambiarla después: viven **en el tipo** —no en cada equipo—, la cantidad aplica **solo a las dos modalidades constantes**, los puntos son **varios** y llevan **unidad**. De ahí sale la lectura del modelo: **N lecturas en cada punto**, que es la práctica metrológica normal y lo que dice la pantalla, porque confundirlo daría un reporte con un tercio de los datos.

**Los tres datos se cambian juntos, en una sola ruta.** Por separado existiría el instante en que un tipo dice verificarse contra un patrón constante **sin decir contra qué valor**, y ese estado no debe poder escribirse.

**Dos trampas de SQL, y las dos las delató una prueba escrita a propósito para ellas:**

1. **Un `CHECK` se satisface con `NULL`, no solo con `TRUE`.** La primera versión unía tres ramas con `OR` y para un tipo no verificable con cantidad fijada daba `NULL OR NULL OR FALSE` = `NULL`: **pasaba**. Con `CASE` el resultado es siempre `TRUE` o `FALSE`. El comentario de la migración afirmaba justo lo contrario, y queda corregido ahí mismo.
2. **Un `CHECK` nuevo se aplica a lo que ya está.** En una instalación con tipos constantes ya registrados, la migración **habría fallado al arrancar**. Rellena antes con `1`, que es la afirmación más débil posible: cualquier valor mayor sería inventarse una práctica.

**El `@Transactional` del adaptador se puso antes de que doliera**, y es la primera vez. El tipo tiene ahora una colección perezosa, que es exactamente lo que hizo fallar a `work-order` el 2026-09-13; verificado quitándolo, tres pruebas caen con `LazyInitializationException`. La convención dejó de pagarse después del defecto.

**Reconfigurar retira los puntos anteriores, no los borra**, porque con ellos se hicieron los reportes anteriores. Y por eso el índice de unicidad es **parcial**: con una restricción normal, **volver a un punto anterior sería imposible**. Hay prueba que va a 200 mmHg y regresa a 100.

**El frontend lo refleja en un solo componente** usado en dos sitios —el alta de un tipo y el panel de la lista—, porque tener la regla en dos habría sido la vía directa a que se desviaran. Oculta lo que no aplica en vez de ofrecerlo para que el servidor lo rechace.

**Y una trampa del DOM que costó un rato**: la opción elegida se marca **en la opción** y no con `[value]` en el `select`. Al abrir el panel con algo ya guardado, la asignación del valor ocurre antes de que existan las opciones y el navegador la descarta **en silencio**. Lo delató la prueba que abre el panel de un tipo ya configurado.

**Dos mutaciones, y la segunda encontró un hueco**: quitar el recorte de «solo con modalidad constante» no rompía nada, porque ninguna prueba cubría que **un dato incoherente venido del servidor no se reenvíe**. Es alcanzable de verdad —una fila con puntos y modalidad variable—, así que ahora tiene su prueba.

**Conteo**: backend **684** —de 659, con 25 nuevas entre esquema, dominio, persistencia y REST—; frontend **293**, de 278. `V8` aplicada al contenedor en marcha.

**Tocadas**: [[dominio-equipo-mantenimiento]], [[deuda-tecnica-y-riesgos]] y el `CONVENCIONES.md` de la raíz.

## [2026-09-27] ingest | Las órdenes de trabajo se pueden usar, y el marcador de requisitos sube por el frontend

**El módulo estaba terminado desde el 2026-09-13 y no se podía usar.** Cuatro de sus siete requisitos describen un formulario, y era el trabajo que la cuenta de requisitos tenía marcado como pendiente a propósito para que no desapareciera. Ahora existen cuatro pantallas: listado, alta, ficha con el ciclo de vida, y la elección del alcance.

**El estado no se edita, se avanza.** Dos botones —iniciar y ejecutar—, y cada uno aparece **solo cuando el estado lo permite**, que es lo mismo que comprueba el backend. Un desplegable de estados habría invitado a saltarse el orden y a que el servidor rechazara lo que la pantalla acababa de ofrecer.

**La séptima regla se ejerce por fin desde un navegador.** La pantalla de alcance pregunta las áreas de la sede de la orden y **solo** ofrece equipos de ellas: es la regla que se construyó el 2026-09-13 al descubrir que faltaba, y hasta hoy ninguna interfaz la había puesto a prueba. Tampoco ofrece lo que ya está en la orden, ni las áreas cerradas.

**Asignar el ingeniero se ve solo con `work-order.assign`.** La separación es del backend y es buena —repartir trabajo no es alterarlo—, y hay allí una prueba que impide que otra operación se cuele en esa autoridad. Aquí se refleja sin inventar nada: el resto de la ficha sigue disponible con `work-order.write`.

**Varias altas sin transacción, y la pantalla lo dice.** El API suma un equipo por llamada: elegir diez son diez llamadas en serie, y si la séptima falla las seis primeras quedan dentro. Se informa de cuántas entraron. Mismo patrón que el panel del modelo, y misma conclusión: lo que el backend no ofrece compuesto, el frontend solo puede contarlo con honestidad.

**La invalidación que se espera, otra vez.** La prueba del alcance se quedaba colgada en la segunda alta: `onSuccess` aguardaba la recarga completa de la orden antes de resolver la mutación. Es la **segunda** vez que este proyecto lo paga —la primera fue `CatalogoApi`—, así que queda escrito en los dos sitios: en una cadena, la invalidación se lanza y no se espera.

**Y un manejador que no se llamaba nunca.** Limpiar la sede al cambiar de cliente se intentó con `(change)` en el campo del cliente, que es el buscador y **no emite ese evento**: el formulario aceptaba un cliente nuevo dejando puesta la sede del anterior. Va en un efecto, con el cliente anterior guardado para no limpiar en bucle.

**El marcador sube de 11 a 14 requisitos, y RF-04 NO entra.** Es una desviación consciente: el requisito describe elegir áreas y después ver sus equipos, y la pantalla muestra todas las áreas de la sede a la vez con «marcar toda el área». Se llega al mismo sitio con un paso menos, pero no es lo que el requisito dice, y darlo por bueno sería justo lo que la cuenta de requisitos existe para evitar. **Es la primera vez que el marcador sube por trabajo de frontend.**

**Conteo**: **330** pruebas de frontend, de 293 —37 nuevas entre las cuatro pantallas—. Backend sin tocar, en 684. Verificado por mutación: los botones que dejan de mirar el estado y el filtro de lo que ya está en la orden ponen rojas tres pruebas.

**Tocadas**: [[dominio-orden-trabajo]], [[hoja-de-ruta-producto]] y el `CONVENCIONES.md` de la raíz.

## [2026-09-27] ingest | Reportes de servicio: el sexto módulo, y dos predicciones de este wiki que salieron falsas

**Cuatro tandas en un día, del esquema al REST**, con el mismo corte que `work-order`: `V9` + pruebas de esquema, agregado + eventos, aplicación + persistencia juntas —un `@Service` sin adaptador tumba el contexto—, y REST + autoridades. Mergeadas por `08038862`, `6849f463`, `c9142635` y `baf4a8db`.

**Un reporte por equipo de la orden**, que es lo que RF-09 dice literalmente y lo que ocurre en campo. La llave foránea es **compuesta contra el puente** `orden_trabajo_equipo`: con dos foráneas sueltas cabría un reporte de un equipo que la orden nunca incluyó, y contra el puente lo impide el esquema solo porque el par ya es su llave primaria.

**El reporte no copia nada de la orden, y eso *es* RF-11.** Consultar por el identificador de la orden es autocompletar; copiar cliente, sede, tipo de servicio y responsables sería una tercera copia que mantener de acuerdo. El original sí copiaba el cliente, y tenía el vínculo hacia la orden roto por tipos —`varchar(10)` contra `uuid`—.

**El resultado de verificar vive con el reporte, no con el equipo.** `V8` había configurado *dónde* y *cuántas veces* se mide; `dato_verificacion` guarda *lo que salió*, y va aquí porque se mide durante el servicio y se imprime en el reporte de ese servicio. Eso **parte en dos la «segunda tanda de `equipment`»** y deja solo el vencimiento de calibración pendiente.

**El hallazgo que costó una escritura extra: el orden en que una ORM escribe no es el del código.** Corregir una lectura retira la vieja e inserta la nueva, y **Hibernate vacía los `INSERT` antes que los `UPDATE`**: la base veía dos lecturas activas del mismo punto con el mismo número y saltaba `UQ_dato_verificacion_activo`. Un índice único **parcial** no se puede declarar diferido en PostgreSQL —`DEFERRABLE` es de las restricciones y una restricción no admite `WHERE`—, así que el adaptador vuelca lo existente, hace `saveAndFlush` y solo entonces inserta. **Lo encontró la prueba de persistencia antes de pasar ninguna vez**, y con dobles no habría aparecido nunca. Por eso esta tanda trajo sus pruebas de persistencia desde el primer día y no cuatro tandas después, como `work-order`.

**Dos mutaciones corrigieron comentarios míos, y las dos están escritas donde las puse.** El `CASE` del `CHECK` de cierre **no** está ahí por la trampa de `V8`: `t_estado_reporte` es `NOT NULL`, así que la versión con `OR` es equivalente —comprobado, batería verde—. Y `compareTo` sobre `BigDecimal` en lugar de `equals` no cambia hoy ningún resultado, porque `of()` y `rehydrate()` ya normalizan la escala. Las dos precauciones se conservan por ser correctas, no por tener una prueba que las exija.

**Se cierra una deuda propia de las viejas**: el grupo `reports` de OpenAPI llevaba **desde el principio** apuntando a un módulo que no existía —un `pathsToMatch` que no casa no falla ni avisa—, y era el fallo silencioso que la convención del proyecto persigue, vivo dentro del propio proyecto. Ahora tiene rutas y prueba de cobertura, **vista fallar** a propósito. `contracts/openapi/reports.json` pasa de dos líneas a 727.

**Dos predicciones falsas de este wiki, corregidas donde estaban escritas**: que el reporte sería «el consumidor natural de los siete eventos de la orden» —no escucha ninguno, los siete **siguen sin consumidor**—, y que `ReportDataProviderPort` del original sería el patrón a usar — aquello agrega datos de varios hexágonos y esto es una entidad con ciclo de vida, así que el patrón **sigue sin gastar** para el PDF de RF-17.

**Requisitos: 14 → 16.** Entran RF-09 y RF-15. **RF-11 no**, con el criterio estricto de siempre: el backend hace imposible teclear esos datos, pero «mostrarlos» es una pantalla y no hay ninguna. Lo mismo que dejó cuatro requisitos de la orden esperando su formulario.

**Conteo**: backend **810** elementos `<testcase>`, de 684 —33 de esquema, 34 de dominio, 37 de aplicación y persistencia, 20 de REST y las de contrato de seguridad ajustadas—, 55 clases, cero fallos, borrando `target/surefire-reports` antes. Frontend sin tocar, en 330.

**Tocadas**: nueva [[dominio-reporte-servicio]] —el wiki pasa a **52** notas—, más [[dominio-reportes]], [[dominio-orden-trabajo]], [[dominio-equipo-mantenimiento]], [[hoja-de-ruta-producto]], [[decisiones-tecnicas-malphasos]], [[deuda-tecnica-y-riesgos]], [[modelo-de-permisos]], `index.md` y el `CONVENCIONES.md` de la raíz.

## [2026-09-28] ingest | El frontend de los reportes, y un fallo de limpieza que se leyó como ochenta y seis

**Cuatro tandas, y el hueco entre backend y pantalla duró un día** — contra las dos semanas que pasaron las órdenes de trabajo en ese estado. `3f3c170e` la ficha con los cinco campos de RF-15, `87b8cf3b` la tabla de verificación, `2c184d6e` el historial de un equipo, `30982c0f` el bloque que autocompleta desde la orden.

**RF-11 cierra, y lo que faltaba era lo más sencillo de decir**: que el reporte **mostrara** los datos de su orden. El backend llevaba un día garantizando que no se pudieran teclear —no hay columna— y que no discreparan —una sola fuente—, pero el requisito habla de mostrar. Ahora hay un bloque de datos del servicio con cliente, sede, servicio, fecha, ingeniero y encargado de la sede, **leídos y no copiados**, y **sin un solo control que editar**, que es además lo que RNF-07 pide. Hay una prueba que cuenta los controles del formulario y falla si alguno de esos datos aparece como campo. **Requisitos: 16 → 17.**

**La tabla de verificación la dicta el tipo del equipo.** Un punto por tres lecturas son tres casillas, así que los dos errores que el servidor rechazaría —una lectura en un punto ajeno, la número cuatro donde se piden tres— **no se pueden escribir**. Averiguarlo cuesta las mismas cuatro consultas que camina el backend, y hay prueba de que son cuatro para toda la tabla y no cuatro por casilla.

**El historial de un equipo no es la hoja de vida, y está escrito en el javadoc.** RF-26 y RF-27 piden el historial **dentro de** una hoja de vida que no existe como entidad; esta pantalla es la consulta sobre la que se construirá. Salen también los reportes retirados —ocurrieron— y un borrador cae a la fecha programada de su orden **diciendo que es programada**, porque una fecha planificada que pasa por fecha de servicio es peor que no dar fecha.

**Dos defectos propios que encontraron las pruebas:**

1. **Un control añadido a un formulario ya desactivado nace activo.** La tabla de un reporte cerrado se podía teclear, porque las casillas se crean cuando llega el tipo —cuatro consultas después— y ya no heredan el estado. El servidor lo habría rechazado, pero la pantalla ofrecía algo que no existe.
2. **Un fallo de limpieza se disfrazó de regresión general.** Una prueba dejó una petición sin responder; el `verify()` del `afterEach` lanzó, eso **impidió a Angular desmontar el TestBed**, y las once pruebas siguientes fallaron con «el módulo ya está instanciado» arrastrando a archivos que nadie había tocado: **86 fallos, con recuentos distintos en cada ejecución**. La causa era drenar las peticiones en una sola pasada, cuando la recarga llega en un tic posterior. La lección operativa: ante decenas de fallos en archivos ajenos, buscar **el primero por orden de ejecución**.

**Y una tercera trampa del corredor**: montar el TestBed dentro de la prueba solo funciona en la primera, porque Angular ya lo reinicia en un `beforeEach` propio.

**Verificado por mutación en las tres tandas**: cinco mutaciones en la primera —una de ellas destapó una prueba mía que pasaba en vacío por no invalidar la caché antes de mirar—, cinco en la tabla y tres en el historial. Todas rompen lo que deben.

**Conteo**: frontend **368**, de 330 —38 nuevas en cuatro archivos—, 40 archivos, cero fallos. Backend sin tocar, en 810.

**Tocadas**: [[dominio-reporte-servicio]], [[arquitectura-frontend-malphasos]], [[hoja-de-ruta-producto]], [[deuda-tecnica-y-riesgos]], `index.md` y el `CONVENCIONES.md` de la raíz.

## [2026-09-28] lint | Tres deudas cerradas de un barrido, y una que no se puede cerrar desde aquí

Repaso de lo que quedaba abierto y se podía cerrar sin pedir nada.

**Los seis grupos de OpenAPI tienen ya prueba de cobertura.** Faltaban `location` y `person`, y el de `person` enseñó una distinción que merece quedarse: había una comprobación de que `/v3/api-docs/person` **responde 200** y ninguna de que trajera algo dentro — **un grupo vacío también responde 200**. Las dos pruebas se vieron fallar cambiando el patrón del grupo a `/country/**` y `/person/**`, que es lo que este proyecto exige de una prueba contra un fallo silencioso.

**Y se retiró un patrón reservado que nunca iba a casar**: `/technical-verifications/**` esperaba en el grupo de equipos desde el principio, y la pieza que esperaba **se construyó en otro módulo** —el resultado de verificar vive con el reporte desde `V9`—. Un patrón reservado es indistinguible de uno roto, así que reservarlos va contra la propia convención.

**La comprobación de salud de RabbitMQ queda apagada en el perfil de pruebas**, que es lo que [[deuda-tecnica-y-riesgos]] recomendaba desde el 2026-09-08. No se apaga porque se haya demostrado que causaba la intermitencia —sigue sin caso reproducido, y la causa que sí se demostró era el ayudante `unico()`—, sino porque **una dependencia externa que ninguna prueba usa no tiene por qué poder influir en el resultado**. Ninguna prueba se dobló: la que toca `/actuator/health` solo exige que la seguridad no la bloquee, valía con 503 y vale con 200.

**Lo que no se puede cerrar desde esta sesión**: no hay clave SSH disponible para `git@github.com`, de modo que ni se puede empujar ni comprobar el estado real del remoto. `main` va **34 commits por delante** del `origin/main` que este clon conoce. Queda como lo único pendiente de una acción del usuario.

**Conteo**: backend **812**, de 810 —las dos pruebas nuevas de cobertura—, 55 clases, cero fallos. Frontend sin tocar, en 368. Deuda propia: **33 abiertas** de 46 filas, recontadas sobre el archivo.

**Tocadas**: [[deuda-tecnica-y-riesgos]], [[arquitectura-frontend-malphasos]] y el `CONVENCIONES.md` de la raíz.

## [2026-09-28] lint | Los cuatro CLAUDE.md pasan a llamarse CONVENCIONES.md

**Decisión del usuario, y es de privacidad y no técnica**: el nombre aparecía en la portada del repositorio en GitHub y no lo quería ahí. Se renombraron los cuatro con `git mv` —raíz, `SecondBrain/`, `Documentation/` y `Documentation/wiki/`—, de modo que **el contenido y el historial siguen enteros**: `git log --follow CONVENCIONES.md` los recorre.

**Qué significa esto al leer el wiki.** Las 70 referencias de todo el repositorio se actualizaron, incluidas las de este registro, **también en las entradas anteriores al cambio**. No es reescribir el pasado: aquellas entradas hablan de este mismo archivo, que hoy se llama de otra manera. Queda dicho aquí y en la cabecera de [[CONVENCIONES.md]] para que nadie busque un archivo que no existe.

**Lo que hay que saber si se clona el proyecto**: la herramienta de sesión carga sola un archivo con el nombre viejo, y ese archivo **está fuera del control de versiones**. En la raíz hay uno con cuatro importaciones —una por cada `CONVENCIONES.md`— y `.gitignore` lo excluye. Si no está, **lo primero en una sesión nueva es leer los cuatro**: sin ellos no se conocen las reglas de commit ni las convenciones de código. La receta literal está en «Cómo se trabaja aquí».

**Por qué se importan los cuatro y no solo el de la raíz**: los de subdirectorio se cargaban **por estar en su carpeta con ese nombre**, y el nombre es justo lo que se quitó. Importarlos explícitamente es lo que conserva esa función.

**Lo que este cambio NO arregla, y conviene no confundirlo**: la atribución. Ese nombre de archivo nunca convirtió a nadie en contributor de GitHub — eso lo deciden el correo del autor y los trailers `Co-authored-by`, y la auditoría del 2026-09-28 sobre los 292 commits publicados dio **cero** en las dos cosas. El renombrado es cosmético y está bien que lo sea.

## [2026-10-02] ingest | La marca en Keycloak, el módulo de personas, y la decisión que se tomó dos veces

Dos bloques en la misma sesión, y los dos dejaron hallazgos que valen más que el código.

### El tema de Keycloak

**Dos mecanismos y no uno**: Keycloakify para el login —una interfaz de verdad— y un **tema clásico** para la consola de administración, donde cambiar logo y colores son **cuatro archivos**. Comparten el nombre de tema porque Keycloak resuelve por *(nombre, tipo)*. Nota propia: [[tema-de-keycloak]].

**La decisión se tomó dos veces y la primera estaba mal argumentada**, y eso es lo que conviene conservar. Se recomendó Angular invocando el error del `auth/` React del original — una analogía con una herida vieja que **no aplicaba**, porque existe librería de Angular mantenida justo en Angular 22. Lo que de verdad decidía era otra cosa: *«only React supports custom Admin UIs»*, y el encargo **era** la consola. El patrón de error: un argumento por precedente suena sólido y puede no venir al caso.

**`initialize-admin-theme` copia la consola entera**: 692 archivos, 89.000 líneas, 12 MB, 25 dependencias, ancladas a Keycloak 26.7. Se ejecutó, se midió y se revirtió. **Y no se arregla ignorándolo en git**, que fue la primera idea del usuario y es razonable: el código tiene que estar para que el build —que corre en Docker— lo compile, y los cambios de marca viven dentro de esos archivos, así que ignorarlos deja sin versionar justo lo propio.

**Tres trampas de despliegue**, las tres documentadas en el compose: `--import-realm` usa **`IGNORE_EXISTING`** y editar el JSON versionado **no cambia nada** en un entorno levantado; el tema se aplica **por realm** y la consola que usa quien desarrolla es la de `master`, que no se importa de ninguna parte; y montar un JAR como volumen falla **creando un directorio** y arrancando sin tema en silencio, de donde que el JAR se construya dentro de la imagen.

### El módulo de personas

Tenía **catorce operaciones en el API y cero pantallas**: RF-51 a RF-53 contaban por el backend y a la vez no había forma de dar de alta a nadie sin `curl`. Cuatro tandas: listado con filtro, las cuatro altas, la ficha con edición y baja, y los contactos.

**Cuatro altas y ningún selector de tipo**, porque las dos reglas que las sostienen son del backend: en las tres con cuenta **el tipo lo dice la ruta**, y la cuarta **solo admite `MANAGER`** —lo impone `PersonService.save` para que nadie escriba una fila que dice ser administrador sin serlo—.

**La ficha se edita en sí misma**, rompiendo con el precedente de cliente y sede, porque **el permiso depende de la fila**: una ruta aparte tendría que declarar una autoridad fija antes de saber a quién carga. Es la misma razón por la que el backend lo resuelve en un bean.

**Y el menú aprendió a ocultar por autoridad**, que estaba anotado como pendiente en `navegacion.ts` desde que se escribió. Lo hizo necesario «Personas»: el primer destino que un grupo legítimo del realm no puede usar. La autoridad la declara la **entrada de navegación**, de donde salen a la vez el menú y el guard.

### Tres defectos propios que encontraron las pruebas

1. **Un `input.required()` no se puede leer en el constructor** — nueve pruebas en rojo con el mismo mensaje. Va en un efecto, y lo que pone tiene que quitarlo en la misma pasada.
2. **Quitar el guard de las rutas no rompía nada**: ocultar estaba probado y proteger no. Es la mitad que se olvida.
3. **Un selector de prueba laxo pulsaba el botón equivocado**: la ficha tiene tres «Retirar», y la prueba pedía retirar un correo mientras **retiraba a la persona**. Texto exacto y clic acotado a su sección.

### Y una nota de higiene del repositorio

Los cuatro `CLAUDE.md` pasaron a `CONVENCIONES.md` por decisión del usuario —el nombre aparecía en la portada de GitHub—, con las 70 referencias actualizadas. El archivo que la herramienta carga **no se versiona** y por tanto **git no lo puede restaurar**: desapareció entre sesiones y la del 2026-10-02 arrancó sin las convenciones cargadas. Está recreado, con una línea en texto plano además de las importaciones.

**Conteo**: frontend **395**, de 368 —27 nuevas en cuatro tandas de personas, más dos del armazón y una de la navegación—, 42 archivos, cero fallos. Backend sin tocar, en **812**. Deuda propia: **50** filas, **37 abiertas**, recontadas sobre el archivo.

**Lo pedido para la sesión siguiente**: una **revisión completa y exhaustiva de todo lo desarrollado**. Queda anotada en [[hoja-de-ruta-producto]] con por dónde empezar, porque no es seguir construyendo: es contrastar lo construido contra lo escrito.

**Tocadas**: nueva [[tema-de-keycloak]] —el wiki pasa a **53** notas—, más [[decisiones-tecnicas-malphasos]], [[arquitectura-frontend-malphasos]], [[hoja-de-ruta-producto]], [[deuda-tecnica-y-riesgos]], `index.md` y el `CONVENCIONES.md` de la raíz.

## [2026-10-03] ingest | el esquema de la base de datos, por fin descrito y dibujado

**Lo pidió el usuario preguntando dónde estaba el diagrama de la base de datos.** No estaba en ningún
sitio, y la respuesta honesta era que no existía: este wiki tenía dos notas en `base-de-datos/` y las
dos describen el **sistema original**. Peor, la primera se anunciaba en su propia descripción como «el
esquema PostgreSQL **actual**» y estaba etiquetada `describe:ambos`, de modo que quien llegaba
preguntando por el estado de hoy leía las 27 tablas de Enterprise Architect del sistema viejo.

**Y este índice ya lo sabía.** Tenía `[[esquema-malphasos]]` anotado como «el hueco más notorio» desde
la reorganización del 2026-09-09, con enlace sin destino. Llevaba ahí casi un mes. Que lo destapara una
pregunta del usuario y no una pasada de lint es el dato que conviene recordar: un hueco anotado como
pendiente no se cierra por estar anotado.

Entra [[esquema-bd-malphasos]], y queda corregida [[esquema-bd-v4]] con constancia de lo que decía.

**Generada leyendo la base en marcha, no las migraciones.** Son diez archivos y el estado final no se
ve en ninguno: `information_schema` sí lo ve. La nota lleva las consultas dentro para que se pueda
rehacer, porque la alternativa es que caduque como caducó la otra.

Lo medido, el 2026-10-03: **26** tablas de dominio —el original tenía 27, y no son las mismas menos
una—, **35** foráneas de las cuales **4 compuestas**, **28** `CHECK` propios, **4** índices únicos
parciales y **26 de 26** tablas con borrado lógico. Esa última no es una convención declarada sino una
afirmación comprobada: la consulta que busca tablas sin `b_estado_activo` devuelve cero filas.

**Ocho diagramas Mermaid, y son los primeros del wiki.** Uno de módulos y siete de entidades, uno por
módulo: un único diagrama de 26 tablas no se puede leer, que es el problema que tiene cualquier ER
generado por una herramienta. Markdown con Mermaid se versiona y se revisa en un diff; un `.png`
exportado no.

**Se validaron ejecutando el analizador de Mermaid, y hacía falta.** La primera versión escribía
`PK_FK` para las claves compuestas de `orden_trabajo_equipo`, que **no es sintaxis válida** —se separan
por coma—, y dos de los ocho bloques no habrían renderizado. El validador se vio fallar a propósito
reintroduciendo el error, que es lo que este proyecto exige de una comprobación: el paquete modular
`@mermaid-js/parser` **no** cubre `graph` ni `erDiagram`, así que hace falta `mermaid` entero con un DOM
de `jsdom`.

## [2026-10-03] ingest | un tipo de equipo se verifica en varias magnitudes

**La primera corrección de la revisión no fue de código, fue de modelo, y la trajo el usuario con un
contraejemplo de una línea**: «hay equipos que pueden tener dos tipos de verificación o hasta más, por
ejemplo un termohigrómetro, pues mi temperatura y mi higrometría». El catálogo construido el 2026-09-27
daba por supuesto que un aparato mide una sola cosa, y la consecuencia práctica era que **un
termohigrómetro había que registrarlo como dos tipos de equipo**: dos fichas técnicas, dos valores de
mantenimiento, dos hojas de vida, para un aparato con un reporte.

`V10__verification_magnitudes.sql` mete un nivel en medio y baja a él la modalidad, la cantidad de
lecturas y la unidad. Los detalles están en [[dominio-equipo-mantenimiento]], el modelo entero en
[[esquema-bd-malphasos]] y las decisiones en [[decisiones-tecnicas-malphasos]].

**Cuatro preguntas al usuario antes de escribir nada, y en las cuatro eligió la opción más expresiva**:
catálogo cerrado de magnitudes, unidad filtrada por magnitud, modalidad por verificación y cantidad de
lecturas por verificación. La primera vez que se le preguntaron fue con el widget de opciones y las
rechazó pidiendo aclarar; se le hicieron en prosa, pidió volver al widget, y ahí las contestó. Queda
anotado porque es información sobre cómo trabajar con él, no sobre el dominio.

### Seis defectos, y dos llevaban semanas latentes

Ninguno lo encontró leer el código: los seis salieron de ejecutar.

1. **La trampa del vaciado de Hibernate estaba latente en `equipment` desde el 2026-09-26.** El índice
   único parcial de los puntos tenía el mismo choque que el de las lecturas y **la batería entera
   pasaba**, porque todas las reconfiguraciones de la batería cambiaban el **valor** del punto y un
   índice parcial deja de ver la fila retirada. Reconfigurar manteniendo el valor habría fallado. `V10`
   lo hizo imposible de ignorar: con la clave en `(tipo, magnitud)`, que no cambia al reconfigurar,
   salta en el caso normal. **Lo que generaliza**: un índice parcial tiene dos caminos que desde fuera
   se ven iguales, y una prueba que ejercita la operación no garantiza que ejercite los dos.
2. **El índice único de las lecturas se quedó corto por el propio cambio.** `(reporte, punto,
   secuencia)` era correcto mientras hubiera una modalidad por tipo; con dos magnitudes variables, la
   lectura 1 de temperatura y la 1 de humedad chocaban. No estaba mal escrito: estaba escrito contra un
   modelo que se movió. **Primera vez en este proyecto que un índice hay que rehacerlo por eso.**
3. **Un `setter` que faltaba y compilaba perfectamente**: el mapper del reporte no escribía la columna
   nueva, y la base la habría rechazado por `NOT NULL`. Lo encontró arrancar, no compilar.
4. **El catálogo metrológico se quedó fuera de su grupo de OpenAPI al nacer** —controlador escrito,
   patrón sin añadir—, y es la **quinta** vez que este proyecto se encuentra con ese fallo silencioso.
   **Primera vez que lo caza una prueba** en vez de alguien leyendo dos listas en paralelo.
5. **La limpieza de siete pruebas del frontend no era a prueba de fallos**: `http.verify()` y después
   `desinstalarAlmacenamiento()`, de modo que al lanzar la primera el doble de `localStorage` se quedaba
   instalado y **el fichero siguiente heredaba sus datos**. Así falló `historial.spec.ts`, que nadie
   había tocado. Es la variante **con contaminación** del «fallo en `afterEach` que se lee como ochenta
   y seis» del 2026-09-28.
6. **Una clave de caché en el prefijo equivocado**: el catálogo metrológico se volvía a pedir en cada
   escritura del catálogo de equipos. Lo encontró una prueba que acabó con dos peticiones abiertas.

### Y un defecto del propio wiki, que es el que más conviene recordar

**El conteo de deuda propia ya estaba mal el día que se escribió.** El `CONVENCIONES.md` de la raíz
declaraba «46 filas, 37 abiertas, contadas una a una el 2026-10-02», y el archivo tenía **51 y 38**
antes de que esta sesión añadiera nada. Cuarta vez que ese número envejece solo y **la primera en que
envejeció en menos de un día**: lo contado el 2 de octubre no coincidía con el archivo del 2 de octubre.

La lección ya estaba escrita tres veces y no bastó, así que esta vez la nota lleva **la receta del
recuento dentro**, en una línea de `awk`. Hoy: **58 filas, 18 tachadas, 40 abiertas**.

**Y una afirmación del wiki que era falsa**: la hoja de ruta decía «`equipment` sigue sin pruebas de
persistencia» como cosa a mirar con lupa en la revisión. `EquipmentCatalogPersistenceTest` existe desde
el 2026-09-26 con nueve casos —hoy doce—. Venía de la fila de deuda de `work-order`, que al cerrarse
dijo «`equipment` sigue sin las suyas» y nadie volvió a comprobarlo. **Es el ejemplo exacto de lo que la
propia revisión pedía buscar**: los defectos aparecen al comparar el wiki con el código.

### Las cifras, medidas y no citadas

Backend **834** pruebas en 55 clases, cero fallos, cero errores, cero omitidas, borrando
`target/surefire-reports` antes. Frontend **409** en 42 ficheros. El esquema, **26** tablas y **35**
foráneas. Las autoridades siguen en **22**: el catálogo metrológico se sirve con `equipment.read`, y
añadir `magnitude.read` habría obligado a tocar el realm, los tres grupos y la expansión del
administrador para separar algo que nadie va a separar.

**El marcador de requisitos no se mueve**, y la razón merece quedar escrita: ningún RF de la ERS
describe magnitudes ni unidades. Lo que el cambio hace es que **RF-15 deje de ser una verdad a medias**
—un termohigrómetro no se podía reportar sin inventarse dos tipos de equipo— y el requisito se daba por
implementado igualmente. Primer caso en que algo marcado como hecho mejora sin cambiar de estado.

## [2026-10-04] ingest | un modelo tiene nombre, y un BUILD SUCCESS que no significaba nada

**Lo pidió el usuario con el mismo tipo de ejemplo que la vez anterior**: «si la marca es Lenovo, el
modelo puede ser IdeaPad 3». Y como la vez anterior, el contraejemplo destapó una ausencia y no una
preferencia: **`modelo` no tenía columna de nombre**.

Lo único legible que un modelo llevaba era su registro INVIMA, que es un número de trámite, **es
anulable** y se obtiene *después* de dar de alta el modelo. Cabía un modelo sin nombre y sin INVIMA,
identificado solo por un UUID, y el listado empezaba la fila por «tipo · marca» —la combinación a la que
el modelo pertenece— sin nombrar el modelo en sí. Viene del esquema del original.

**Cuarta ausencia de este módulo que destapa un contraejemplo del usuario y no una revisión del
código.** Las otras tres: la cadena que obligaba a recorrerla, las verificaciones que faltaban, y el
termohigrómetro de ayer. Conviene tenerlo presente al planear la revisión: lo que el original no
modelaba se nota usándolo, no leyéndolo.

Está en `V11__model_name.sql`, con el detalle en [[dominio-equipo-mantenimiento]] y el diagrama al día
en [[esquema-bd-malphasos]]. Obligatorio con `CHECK` contra blancos —`''` y `'   '` pasan un `NOT
NULL`—, único **por `equipo`** porque «Serie 3» puede ser de dos marcas y la marca vive ahí, e índice
**parcial** como los otros cuatro. Se renombra por su propia ruta, igual que una marca.

El relleno de la migración es un marcador, no un dato: la única fila que había quedó como «Sin nombre».
Usar su INVIMA habría sido peor — un número de registro puesto en la columna del nombre **parece** un
nombre y nadie lo corregiría.

### Y una trampa de herramienta que vale más que el cambio

**`./mvnw test-compile` dijo BUILD SUCCESS con las pruebas llamando a una firma que ya no existía.**
Se añadió un parámetro a `Model.create` y `Model.rehydrate`; las pruebas pasaban cinco argumentos a seis
parámetros. Eso no compila nunca.

No es magia: `maven-compiler-plugin` decide si recompilar comparando marcas de tiempo **de los fuentes
de su propio ámbito**, y ningún fuente de prueba había cambiado. Dejó `target/test-classes` compilado
contra la versión anterior de producción: clases obsoletas y aparentemente sanas.

Lo delató **leer el código y ver que no podía compilar**, no una herramienta. `mvn test` lo habría
destapado también, pero con `NoSuchMethodError` en ejecución en vez de un error de compilación — el
mismo defecto, más tarde y peor explicado.

La regla queda en [[stack-spring-boot-4-particularidades]]: **al cambiar una firma pública que las
pruebas usan, el `test-compile` incremental no es una comprobación**; hay que borrar
`target/test-classes` antes. Es el segundo verde falso de esta revisión, y el primero que no venía de
una prueba mal escrita sino de la herramienta que las compila.

### Las cifras, medidas

Backend **842** en 55 clases, frontend **410** en 42 ficheros, **once** migraciones, 26 tablas, 29
`CHECK` y **cinco** índices únicos parciales. Deuda propia: **60** filas, 20 tachadas, 40 abiertas,
contadas con la receta que la nota lleva dentro.

## [2026-10-04] lint | la pasada de mutaciones, y doce columnas que me invente

Segunda tanda de la revisión, y esta vez el método fue **desactivar reglas** y ver si alguna prueba se
enteraba. Tres de cinco mutaciones sobrevivieron a la batería entera.

### Lo que sobrevivió

**`EquipmentTypeService` no tenía ninguna prueba.** Ni una clase de `src/test` mencionaba su nombre, de
modo que sus tres reglas cruzadas del catálogo metrológico llevaban sin verificar desde que se
escribieron el día anterior. Desactivar «la unidad tiene que ser de esa magnitud» dejó **842 pruebas en
verde**.

Lo que lo hace peor que una ausencia simple es el **disfraz**: había tres clases alrededor que parecían
cubrirlo. `EquipmentRestAdapterTest` simula el puerto de entrada y nunca entra al servicio;
`EquipmentCatalogPersistenceTest` entra por el adaptador; y `CatalogAggregatesTest` cubre el agregado,
que **no puede** consultar un catálogo. Tres pruebas vecinas y ninguna tocaba la regla.

**La regla de cierre por verificación estaba afirmada aquí y sostenida por nada.** El 2026-10-03 se
escribió en el wiki y en el mensaje del commit que un termohigrómetro con las dos magnitudes variables
ya no se podía cerrar a medias. Quitar el filtro no rompía nada.

**Y el camino hasta la prueba que lo caza es la lección de hoy.** La primera que escribí usaba dos
magnitudes **constantes** y la mutación **siguió viva**: con modalidad constante el identificador del
punto ya discrimina, así que el filtro por verificación no cambia el resultado. El caso que de verdad lo
distingue es dos verificaciones **variables** con lecturas de una sola, donde no hay punto que separe
nada. **Una prueba del caso correcto por el camino equivocado se lee igual que una buena**, y lo único
que las distingue es ver fallar la mutación.

### Dos cuidados del método, que no son evidentes

- **`-Dtest=` no avisa si no casa con nada.** La primera corrida nombró `EquipmentTypeServiceTest`, que
  no existía, y el «nadie la caza» salió de una invocación que apenas ejecutó pruebas. Un arnés de
  mutación tiene que **decir cuántas pruebas corrieron**, o su veredicto no vale.
- **Hay que borrar `target/test-classes`.** Es la trampa de ayer: el `test-compile` incremental no
  recompila si solo cambió producción, así que una mutación puede correr contra clases de prueba viejas.

### Y un hallazgo que no vino de mutar, sino de comparar el wiki contra la base

**[[esquema-bd-malphasos]], escrita ayer, tenía doce nombres de columna inventados** en seis tablas, más
una llave primaria que no existe en `encargado`. La nota decía de sí misma que estaba «generada leyendo
la base de datos en marcha», y era **verdad a medias**: la lista de tablas, el grafo de foráneas y los
conteos sí; **los nombres de columna se escribieron de memoria**.

Lo encontrado: `pais.n_codigo_iso` era `k_codigo_iso`; la llave de `persona` es **subrogada** y el
documento vive en `k_cedula`, no al contrario; los nombres de una persona van en **cuatro** columnas;
`cliente.n_nit` no existe —es `k_documento` más `n_tipo_identificacion`, porque el documento de un
cliente puede no ser un NIT—; `encargado` **no tiene identificador propio**; y `orden_trabajo` tenía la
fecha y dos columnas más con el nombre equivocado.

**El remedio no es «mirar mejor».** El problema era que la parte verificada y la inventada iban en la
misma nota sin distinguirse. Entra `SecondBrain/herramientas/` —documentado en el schema de este wiki—
con `verificar-esquema.py`, visto fallar cambiando `k_cedula` por `n_cedula`. Y la nota declara ahora
qué se lee de la base y qué no, **incluido que el guion no caza tipos ni marcas `PK`/`FK`**: la llave
inventada de `encargado` no la habría detectado.

### Lo que sí aguantó, que también es resultado

Ningún controlador usa `hasAnyAuthority` ni `admin.full`. Los cinco adaptadores que mapean una colección
perezosa llevan `@Transactional`, y los diez que no la tienen correctamente no lo llevan. Ningún puerto
de persistencia declara `delete` ni `update`. Y tres mutaciones del reporte —unidad inventada, punto de
otra verificación, verificación retirada— se cazan todas.

Backend **851** pruebas en **56** clases. Deuda propia: **63** filas, 23 tachadas, 40 abiertas.

## [2026-10-04] lint | once mutaciones sobre los seis módulos: cuatro huecos, todos con cobertura vecina

Se extendió el barrido de mutaciones a `client`, `work-order`, `person` y `location`, que no se habían
tocado. **Once mutaciones en total sobre los seis módulos, cuatro supervivientes** — las dos de
`equipment` y `report` ya registradas, más dos nuevas.

**Encargar a alguien de un área cerrada.** `ManagerService` tiene dos guardas paralelas,
`requireActiveHeadquarter` y `requireActiveServiceArea`, idénticas salvo el puerto y la excepción.
Quitarle a la del área el `.filter(area -> area.isEstadoActivo())` dejó las 851 pruebas en verde,
mientras la misma mutación sobre la de la sede cae. Cerrada con dos pruebas, una por entrada: `register`
crea la persona y `assign` usa una existente, y un arreglo que mirara solo una de las dos habría pasado
con una sola.

**La cuarta alta de personas admitía cualquier tipo.** `PersonService.save` es la única puerta que **no**
crea usuario en Keycloak, y `requireWithoutAccess` la restringe a `MANAGER`. Sustituir su condición por
`false` dejó la batería en verde: `PersonServiceTest` tenía **veinte** casos y **ninguno llamaba a
`save`**.

Lo que la regla sostiene no es cosmético: sin ella se escribe una fila que dice ser `ADMIN` o `ENGINEER`
**sin cuenta en el proveedor de identidad**, y se salta además la escalera que las otras tres puertas
imponen —esas exigen `super.person.write` para la gente de la casa, y esta no exige nada porque da por
supuesto que lo que entra no accede—. El frontend se apoya en ella dos veces: las cuatro altas no tienen
selector de tipo, y la cuarta se justifica por esto.

### Las cuatro supervivientes tienen la misma forma, y es la conclusión del barrido

En los cuatro casos **había cobertura alrededor de la regla y ninguna sobre ella**:

- un servicio con **tres clases vecinas** que parecían cubrirlo —REST que simula el puerto,
  persistencia que entra por el adaptador, agregado que no puede consultar un catálogo—;
- una regla de cierre con **siete pruebas de cierre** que nunca usaban dos verificaciones;
- una guarda con su **gemela probada**;
- un método con **veinte pruebas en su clase** y ninguna que lo llamara.

**La cobertura vecina es lo que hace invisible el hueco.** Un fichero de pruebas lleno junto a una regla
desnuda se lee, de un vistazo y en una revisión, exactamente igual que uno que la cubre. Lo único que
los distingue es desactivar la regla.

### Lo que aguantó

`work-order` pasó las tres mutaciones más cargadas de regla —equipo de otra sede, equipo de otro
cliente, orden en la sede de otro cliente—, que es coherente con ser el módulo cuyas siete reglas se
revisaron contra el wiki en septiembre. `location` y la guarda de la sede de `client` también.

Backend **856** pruebas en **56** clases. Deuda propia: **65** filas, 25 tachadas, 40 abiertas.

## [2026-10-04] lint | segunda vuelta de mutaciones: los agregados y el frontend

Diez mutaciones más, sobre el terreno que la primera vuelta no tocó. **Una superviviente**, y tenía
cómplice.

### La igualdad por identidad no estaba comprobada en quince de diecisiete agregados

Quitar `onlyExplicitlyIncluded = true` de `EquipmentType` —de modo que su igualdad pasa a compararse por
todos sus datos— dejó las 856 pruebas en verde. Al contarlo: **diecisiete** clases usan esa anotación y
solo **dos** tenían una aserción de igualdad por identidad, `City` y `Brand`.

**Y la prueba que parecía cubrirlo mentía en su nombre.** `CatalogAggregatesTest.identidadYRehidratacion`
se llamaba «los seis agregados comparan por identidad, y rehidratar no emite», y de los seis solo
afirmaba la igualdad de `Brand`: de los otros cinco comprobaba únicamente que rehidratar no emitiera
eventos.

**El nombre de una prueba es una afirmación sin verificar**, y en una revisión se lee como si lo
estuviera. Ese nombre es lo que mantuvo el hueco invisible, igual que la cobertura vecina en los cuatro
casos de la primera vuelta. Renombrada a lo que hace, con la razón dentro.

Entra `AggregateIdentityContractTest`, que lo comprueba **para los diecisiete a la vez** y absorbe solo
al próximo agregado. Exige tres cosas y las dos primeras están prohibidas **por razones opuestas**:
`onlyExplicitlyIncluded = true` —sin él, dos agregados distintos con los mismos datos son iguales— y
**nunca** `callSuper = true` —el defecto del sistema original: `AggregateRoot` no redefine `equals`, de
modo que la comparación acaba en la identidad de `Object` y **nunca** serían iguales—. Vista fallar con
las dos variantes.

**Y lee el código fuente en vez de usar reflexión**, que es lo que haría cualquiera. El motivo es
técnico y vale para cualquier invariante de forma: `@EqualsAndHashCode` de Lombok es
`@Retention(RetentionPolicy.SOURCE)` —comprobado con `javap` sobre `lombok-1.18.30.jar`—, así que **no
existe en el bytecode** y `getAnnotation` devuelve `null`. `RestAuthorizationCoverageTest` sí usa
reflexión porque las anotaciones de Spring son `RUNTIME`.

### Lo que aguantó

**Los agregados**: `deactivate()` idempotente, la colección entregada como copia inmutable, y retirar
una verificación arrastrando sus puntos — las tres cazadas.

**El frontend, las seis**, que es el resultado más limpio de todo el barrido: una magnitud ya usada que
se vuelve a ofrecer, la tabla de un reporte cerrado que vuelve a teclearse, una casilla a medias que se
manda, las rutas sin guard de autoridad, el botón de cerrar habilitado sin lo que el servidor exige, y
el buscador aceptando un valor inventado.

### El balance del barrido completo

**Veintiuna mutaciones, cinco supervivientes**, todas cerradas: cuatro en los servicios de aplicación
—con cobertura alrededor y no sobre la regla— y una estructural en los agregados, sostenida por un
nombre de prueba que prometía de más.

Backend **857** pruebas en **57** clases. Deuda propia: **67** filas, 27 tachadas, 40 abiertas.

## [2026-10-04] ingest | el filtrado por dueño, y una pregunta que llevaba un mes contestada

**La mayor deuda abierta del proyecto, cerrada en cuatro tandas y sin una sola migración.** Hasta hoy un
representante legal con `client.read` leía **todos** los clientes del sistema, y con las otras cuatro
autoridades de su grupo, los equipos, las órdenes y los reportes de todos. Van **18 lecturas acotadas**
en cuatro módulos. Entra [[filtrado-por-dueno]].

### Lo que más conviene recordar no es el filtro

**Tres notas afirmaban que esto «exige decidir cómo se ata una cuenta de Keycloak a un cliente del
dominio»** —[[modelo-de-permisos]], [[decisiones-tecnicas-malphasos]] y [[deuda-tecnica-y-riesgos]]— y
esa decisión estaba tomada desde la migración de `person`, escrita en el javadoc de
`PersonService.register`: el identificador de una persona **es** el que asigna Keycloak, así que el
`sub` del token es la llave de `persona`. De ahí a los clientes está `representante_legal`, con llave
compuesta desde `V4` porque una persona puede representar a varios.

La frase se repitió casi un mes y **bloqueó la pieza con mayor implicación de seguridad del proyecto**.
La lección, que vale para cualquier nota: **un bloqueo sobrevive a su causa**. Una nota que dice «esto
exige decidir X» hay que releerla cuando X se haya decidido en otra parte, porque nada avisa.

### Dos afirmaciones más que resultaron falsas

- **«Ninguna clase fuera de `bootstrap/config` toca `Authentication`.»** Cierto el 2026-09-08 y **falso
  desde el 2026-09-13**, cuando la escalera de usuarios trajo `PersonWriteGuard`; nadie volvió a
  contar. Hoy son dos. Lo que sigue en pie es la parte que importa y por la que el alcance entra como
  parámetro: **ninguna clase de `application` ni de `domain` la toca**.
- **«Ninguna tabla lleva columna de pertenencia»**, en [[esquema-bd-malphasos]], presentado como la
  razón de que el filtro no existiera. La pertenencia ya estaba; lo que no hay es una columna que la
  diga en un solo sitio, y el camino al dueño son hasta tres saltos que recorre la aplicación.

### El patrón que apareció solo, y su riesgo

**Ocho de las dieciocho lecturas no comprueban nada por su cuenta**: le pasan el alcance a quien es
dueño del recurso por el que filtran, y la comprobación de existencia y la de pertenencia se vuelven la
misma llamada. Su riesgo es el mismo las ocho veces y es **invisible en una revisión**: pasar
`unrestricted()` en lugar del alcance de quien llama **compila igual y no filtra nada**. Cada una lleva
una verificación de que el alcance viaja.

**El único sitio donde delegar sería incorrecto** es un reporte por identificador: delegando, el error
sale como «esa orden no existe» cuando lo pedido fue un reporte, y la respuesta contaría de qué es el
identificador que no se puede ver.

### Verificación: 22 mutaciones, dos supervivientes, y una regla nueva

Los dos supervivientes fueron por el mismo motivo, y de ahí sale algo reutilizable: **para probar que
una guarda es la que rechaza hay que estubar el camino que rechazaría si la guarda no estuviera**. Una
prueba pedía las sedes de un cliente ajeno sin estubar su existencia: al desactivar el filtro caía en la
comprobación de existencia, Mockito devolvía `Optional.empty()` y **rechazaba igual, por el camino
equivocado**. En modo estricto ese estubado va con `lenient()`, que es la declaración «esto está aquí
para que no sea esto lo que falle».

**Y un fallo del arnés, el tercero de esta revisión con la misma forma**: el guion de mutación había
desaparecido del disco, de modo que cuatro mutaciones no mutaron nada y salieron con cero fallos —cuatro
supervivientes aparentes que no existían—. Tras `-Dtest=` que no avisa si no casa con nada y el
`test-compile` incremental que no recompila, la regla es una: **un arnés que no encuentra nada tiene que
decirlo, porque su silencio se lee como un resultado.**

### La deuda sube, y es la primera vez que una tanda la deja más alta a propósito

**71 filas, 28 tachadas, 43 abiertas** —eran 67/27/40—: cierra una y abre **cuatro**, y las cuatro son
decisiones tomadas y no olvidos. La cuenta de Keycloak sin fila en `persona` que recibe alcance libre
—el precio de que el SuperUsuario se cree a mano—, los ingenieros sin acotar, los dos `findAll()`
muertos en los puertos de sedes y áreas, y la lista `IN` que viaja con el alcance.

**Tocadas**: [[filtrado-por-dueno]] (nueva), [[modelo-de-permisos]], [[decisiones-tecnicas-malphasos]],
[[deuda-tecnica-y-riesgos]], [[hoja-de-ruta-producto]], [[esquema-bd-malphasos]],
[[checklist-reutilizacion]], [[index]] y el `CONVENCIONES.md` de la raíz.

## [2026-10-04] ingest | una escalada de privilegios que nadie había visto, y la deuda que la volvió fuga

**Segunda entrada del día, y la primera consecuencia del filtrado por dueño: lo que esta mañana era
deuda de forma resultó ser de seguridad.** Se fue a mirar los tres `PUT` que `person` conserva —una
violación de la convención, severidad baja— y detrás había dos cosas.

### La escalada

`PersonWriteGuard` miraba el tipo que la persona **tiene**, y la edición puede cambiárselo en la misma
petición. Con `person.write` —que el grupo `admins` tiene— se podía tomar a un representante de cliente
y dejarlo `ADMIN`, o `SUPER_ADMIN`, que es exactamente lo que `POST /persons/admins` exige
`super.person.write` para hacer. **El escalón de arriba no significaba nada por la puerta de al lado.**

Cómo sobrevivió, que es lo que conviene recordar: **tres invariantes estructurales sostienen la escalera
y ninguna miraba el destino del cambio**, solo el origen. Y **no existía ninguna prueba de
`PersonWriteGuard`**, la clase donde vive la regla: la cubría de refilón la de seguridad, que comprueba
códigos HTTP y no las ramas de esta decisión. Se demostró antes de arreglarla —dos pruebas que fallaron
contra el código anterior— y se cerró el mismo día con `canUpdate`, que exige las dos autoridades.

**Y el filtrado por dueño de esta misma mañana la había convertido en una fuga de lectura**: el alcance
se decide por el tipo de la persona, así que cambiarle el tipo a una cuenta del grupo `clients` le
quitaba el filtro sin tocarle ni un rol de Keycloak. No era previsible ayer; lo es desde que el filtro
existe. **Construir una pieza puede subir la severidad de una deuda ya anotada**, y nada avisa de eso
salvo volver a leer la lista.

### La deuda heredada, cerrada por la misma razón

**Cambiar `tipoPersona` ya mueve al usuario de grupo.** Llevaba abierta desde el 2026-09-09, clasificada
en [[sincronizacion-con-proveedor-de-identidad]] como «de permisos, no de datos» — y esa clasificación
es lo que la dejó fuera de la nota que precisamente trata de dos sistemas que no comparten transacción.
Era de las dos cosas: la fila decía un tipo, el grupo daba otras autoridades, y **el sistema creía las
dos a la vez**.

`PersonType` tiene cinco valores y los grupos del realm son tres, así que el mapeo no es total y las dos
decisiones que faltaban van escritas donde se toman: un **encargado no queda en ningún grupo** —no
accede: su cuenta autentica y toda llamada responde 403— y un **`SUPER_ADMIN` queda en el de
administradores**, que es lo más que un grupo puede dar, con aviso en el log.

### Dos cosas más que salieron al pasar

- **La invariante de autorización era ciega a los argumentos con paréntesis.** Su patrón era
  `\([^)]*\)`, de modo que rechazaba cualquier `@PreAuthorize` que leyera un campo del cuerpo. No
  dejaba pasar nada indebido: **impedía escribir lo debido**. Ampliada, y comprobado que siga rechazando
  las formas que debe.
- **Un motivo del frontend caducó.** [[decisiones-tecnicas-malphasos]] decía que el tipo de persona «se
  ve y no se cambia» porque el servidor no completaba la consecuencia. Ya la completa: la pantalla sigue
  igual porque nadie la ha tocado, y queda como decisión de producto.

**Deuda: 74 filas, 30 tachadas, 44 abiertas** —eran 71/28/43—. Cierra dos y abre tres.

**Tocadas**: [[deuda-tecnica-y-riesgos]], [[sincronizacion-con-proveedor-de-identidad]],
[[dominio-persona-identidad]], [[decisiones-tecnicas-malphasos]] y el `CONVENCIONES.md` de la raíz.

## [2026-10-04] lint | un resumen truncado que indujo un error

**Tercera entrada del día, y la corrige algo que esta sesión misma hizo mal.**
[[esquema-bd-malphasos]] decía de `ciudad`: «64 nombres de municipio se repiten entre departamentos
colombianos y `UQ_ciudad_nombre_por_pais` los hace imposibles», y ahí se cortaba. Un lector concluye que
esos 64 municipios **no se pueden registrar** — y es lo que se concluyó, citando esa línea, al priorizar
el trabajo que queda.

**No es así.** `V7` los desambigua metiendo el departamento en el nombre —`La Unión (Nariño)`— y los
**1.103** municipios están sembrados; comprobado contra la base en marcha, que además devuelve **cero**
nombres repetidos dentro de Colombia, precisamente porque ya vienen desambiguados. La fila de
[[deuda-tecnica-y-riesgos]] lo cuenta completo; el resumen del esquema se quedó a medias.

Lo que queda es deuda de **modelo** y no un hueco de **datos**: el nombre carga con información que
debería ser una columna, y ordenar o buscar por departamento sigue sin poderse.

**La lección es sobre los resúmenes, no sobre `ciudad`.** Una nota que resume a otra puede quedarse en
la mitad que asusta y omitir la que tranquiliza, y entonces **miente sin decir nada falso**: cada palabra
de esa frase era cierta. Un resumen que cambia la decisión de quien lo lee tiene que llevar el remedio
junto al defecto, o no llevar ninguno de los dos.

**Tocadas**: [[esquema-bd-malphasos]].

## [2026-10-04] ingest | la hoja de vida, que no era lo que se estaba construyendo

**Octava entrada del día, y la que empieza con una corrección del usuario.** (Las dos anteriores se presentan como «segunda» y «tercera» del día y son la **sexta** y la **séptima**: antes del filtrado por dueño ya había cuatro entradas de esta fecha. Se corrige aquí y no allí porque este log solo se añade.) Se estaba construyendo un
historial de reportes y llamándolo hoja de vida. La aclaración: **la hoja de vida no sale de los
reportes**; es un compilado del cliente, el tipo, la marca, el modelo, la serie y el fabricante, que
existe desde que el equipo se registra con su historial en cero. Lo único que sale del reporte es el
historial. Entra [[hoja-de-vida]].

### Cuatro tandas y dos migraciones

`V12` crea `intervencion` —la única sección con tabla—, el oyente la escribe al cerrarse cada reporte,
`GET /client-equipments/{id}/life-sheet` compila las cuatro secciones de RF-22, y la pantalla las pinta
de solo lectura. **`V13` rellena el pasado**, y se encontró **arrancando** `V12` sobre la base de
desarrollo: los reportes cerrados antes del oyente quedaban sin su línea, y nada lo avisaba.

**El marcador sube de 17 a 19.** Entran RF-26 y RF-27, y RF-22 y RF-24 dejan de estar «en revisión»
desde la mañana: la sección que faltaba se construyó y el documento es uno.

### Tres decisiones del usuario, y lo que cada una obliga

- **Solo lectura**: cada dato se corrige donde vive. Responde a RF-24.
- **La intervención sobrevive al reporte retirado**: el mantenimiento ocurrió. Es lo que hace tabla al
  historial en lugar de consulta.
- **Nada más por ahora**: ni vencimiento de calibración ni fecha de instalación ni vida útil.

**Y una que las dos primeras dejan abierta**: corregir un reporte es retirarlo y abrir otro, y con la
intervención sobreviviendo, **el mismo mantenimiento queda anotado dos veces**. Pendiente de decisión.

### Lo que salió de ejecutar y no de leer

- **El primer consumidor de un evento de dominio del sistema**: de **51** eventos declarados, ninguno
  tenía quien lo escuchara. El javadoc del oyente decía «doce», escrito de memoria; contado, son 51, y
  el mensaje del commit que lo introdujo arrastra la cifra falsa.
- **El directorio que la ERS reservó para ese oyente está en el módulo equivocado**: en `equipment`
  habría creado un ciclo. Vive en `report`. El esquema, en cambio, **sí** tiene ahora referencias entre
  esos dos módulos en los dos sentidos, y queda registrado como aceptado.
- **Una tercera causa de intermitencia que nadie había anotado**: once clases inventaban códigos ISO de
  país sin mirar si existían, contra 249 países sembrados. Una clase fallaba una de cada tres veces.
- **Dos mutaciones supervivientes y un cambio que nada notó**, los tres cerrados. La forma de uno ya es
  conocida: **una prueba que pasa porque algo distinto de la regla rechaza** —la hoja de vida de un
  equipo ajeno se rechazaba al final, por su cuarta sección, después de leer el cliente, la sede y el
  área **de otro cliente**—. Tercera vez hoy.
- **El comprobador del esquema** confirma las 128 columnas dibujadas, incluidas las de `intervencion`.

### Un número mío que caducó en la pasada anterior

El párrafo de conteo de [[deuda-tecnica-y-riesgos]] se quedó en 71/28/43 mientras el `CONVENCIONES.md` de
la raíz ya decía 74/44: se actualizó el resumen y no la nota que lleva la receta. Hoy son **79 filas, 31
tachadas, 48 abiertas**.

**Tocadas**: [[hoja-de-vida]] (nueva), [[hoja-de-ruta-producto]], [[dominio-reporte-servicio]],
[[esquema-bd-malphasos]], [[deuda-tecnica-y-riesgos]], [[decisiones-tecnicas-malphasos]],
[[filtrado-por-dueno]], [[index]] y el `CONVENCIONES.md` de la raíz.

## [2026-10-05] ingest | lo que quedó sin registrar del 2026-10-04: `V14`, la cascada, el contenedor y el tema

Cuatro cosas entraron en `main` el 2026-10-04 después de la última pasada de wiki, y ninguna estaba
registrada. Esta pasada empezó por `git log`, no por lo que dijo la anterior.

- **`V14`**: corregir un reporte ya no deja dos líneas en la hoja de vida. La decisión fue del usuario
  —el sustituto reemplaza al anterior, al cerrarse— y la fila de deuda se cerró el mismo día que se
  abrió. [[hoja-de-vida]], [[esquema-bd-malphasos]], [[deuda-tecnica-y-riesgos]].
- **El alta de un equipo elige tipo, marca y modelo.** Destapó que la lista vieja no mostraba el nombre
  del modelo, invisible porque el doble de pruebas no lo tenía. [[dominio-equipo-mantenimiento]].
- **El frontend en su contenedor**, con nginx sin proxy y en el 5173. [[dockerfile-y-contenedores]].
- **El tema de Keycloak, terminado en lo visual**: el login con el diseño del manual y en español, y la
  consola rehecha porque en modo oscuro no se leía. [[tema-de-keycloak]].

**Tres hallazgos de la propia pasada:**

1. **La base de desarrollo estaba en `V13` horas después de mergear `V14`**: el contenedor del backend
   no se había reconstruido. Las cifras del esquema se recontaron después de reconstruirlo —38 foráneas,
   33 `CHECK`, 27 tablas, 5 índices parciales— y el guion de columnas pasa con la columna nueva.
   **Una migración mergeada no es una migración aplicada.**
2. **El realm versionado no nombra el tema en `adminTheme`** —trae `""` desde `aa089cbf`—, y
   [[tema-de-keycloak]] afirmaba que sí: describía el servidor, donde se puso con `kcadm`. Corregida con
   constancia; el arreglo del JSON es un commit de código aparte.
3. **El índice daba 4 índices únicos parciales** para el esquema, falso desde `V11`.

Recuento de deuda propia con la receta de la nota: **79 filas, 33 tachadas, 46 abiertas** (eran 79/31/48:
se cerraron dos, la de las dos líneas y la del login sin marca, y no entró ninguna).

**Tocadas**: [[hoja-de-vida]], [[esquema-bd-malphasos]], [[deuda-tecnica-y-riesgos]],
[[tema-de-keycloak]], [[dominio-equipo-mantenimiento]], [[dockerfile-y-contenedores]],
[[hoja-de-ruta-producto]], [[decisiones-tecnicas-malphasos]], [[index]] y el `CONVENCIONES.md` de la raíz.

## [2026-10-05] query | el formato impreso de la hoja de vida: del diseño al plan

El usuario pidió replantear la hoja de vida y diseñar **cómo la ve y cómo la imprime**. Se inventarió
lo que trae hoy contra el formato BB-ING-HV-30 que la empresa usa desde 2019, se hicieron tres
propuestas en Claude Design con la marca nueva de Bolívar —encontrada en `landingPage/brand-assets/`— y
el usuario eligió la «tablero de estado», con el escudo del logo centrado detrás, en el naranja de la
marca y muy transparente. Después pidió **todo lo necesario para producirla en la aplicación**: es
[[hoja-de-vida-formato-impreso]].

Lo que la nota deja escrito y conviene no perder:

- **Once datos del diseño no existen** en ninguna tabla, y **la mitad del trabajo es decidir dónde
  viven** —tipo, modelo o unidad—, porque eso decide qué se repite entre máquinas. La propuesta va con
  su porqué, para discutirla.
- **Los datos eléctricos están en el tipo por herencia del original**, y son del modelo. Añadir tres
  más al lado de voltaje y amperaje sin decidirlo dejaría el error más grande.
- **El historial necesita descripción y responsable**, y la única forma limpia es **congelarlos al
  cerrar**: `equipment` no puede leer `report` sin el ciclo que ya obligó a poner el oyente en `report`.
- **El backend no tiene ni librería de PDF ni almacenamiento de archivos.** La impresión se recomienda
  desde el navegador; el PDF del servidor es la misma decisión que RF-17, y la foto, una decisión de
  infraestructura por sí sola.
- **El diseño son dos páginas cerradas y un equipo real puede tener cuarenta servicios**: la nota
  escribe cómo fluye el historial y qué no resuelve la impresión del navegador —el «página 2 de 3»—.

Once decisiones abiertas, listadas al final de la nota. **Una se cerró en la misma conversación**: los
cinco datos eléctricos van en el **modelo**, y voltaje y amperaje se mueven desde el tipo. Quedan diez.

**Tocadas**: [[hoja-de-vida-formato-impreso]] (nueva), [[hoja-de-vida]], [[hoja-de-ruta-producto]],
[[index]] y el `CONVENCIONES.md` de la raíz. Y en `Documentation/wiki/`, [[rf-hojas-vida]], cuyo cuerpo
seguía en el estado anterior al 2026-10-04.

