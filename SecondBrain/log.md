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
