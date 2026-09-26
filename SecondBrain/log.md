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

Actualizadas [[deuda-tecnica-y-riesgos]] —dos entradas de deuda propia cerradas, quedan tres—, [[openapi-swagger]], [[migracion-equipment-hallazgos]] y el `CLAUDE.md` de la raíz, cuyas convenciones REST ganan las dos reglas aprendidas hoy y estrenan una sección de OpenAPI.

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

Rescatados a `main` los diez `.puml`, sus `.png`, el documento de constitución con sus imágenes, el devcontainer con la configuración de PlantUML que hace falta para compilarlos, y el `CLAUDE.md` de `Documentation/`, que resulta ser **el único sitio donde constan el cliente (BolívarBioingeniería LTDA), la duración estimada y el presupuesto del proyecto** — datos que ninguna otra parte del repositorio recoge.

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

**Y un cuarto dato falso, menor pero contable**: el `CLAUDE.md` de la raíz decía «57 defectos conocidos del original». Son **63**, contados fila a fila hoy; el log del 2026-09-02 ya daba esa cifra y el archivo de la raíz no se actualizó entonces.

Nota nueva: [[modelo-de-permisos]]. Actualizadas [[decisiones-tecnicas-malphasos]], [[deuda-tecnica-y-riesgos]], [[seguridad-keycloak-backend]], [[keycloak-configuracion]], [[stack-spring-boot-4-particularidades]], [[checklist-reutilizacion]] y el `CLAUDE.md` de la raíz, que afirmaba `@PreAuthorize("hasAuthority('admin.full')")` en todas las operaciones y una batería de 339 pruebas.

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

**Y un defecto encontrado contrastando el `CLAUDE.md` de la raíz contra el código**, que no se ha arreglado: el archivo afirma «solo `PATCH`, sin `PUT`» sin acotarlo, y `person` **conserva tres `PUT`** —persona, correo y teléfono— del CRUD original. La convención se fijó al migrar `location` y nadie volvió sobre el módulo anterior. Anotado como deuda propia; el `CLAUDE.md` queda acotado.

**Sobre el conteo, medido y no copiado.** Dos ejecuciones completas de `./mvnw test` el 2026-09-09 sobre `fix/person-identity-sync`, borrando `target/surefire-reports` antes de cada una y **con nada escuchando en 5672**: idénticas, 36 clases, cero fallos, cero errores.

**Corrección: el atributo `tests=` de los XML tampoco cuenta todo.** [[stack-spring-boot-4-particularidades]] lo daba como el conteo bueno. Hay un tercero, y es mayor: **363** por el resumen de Maven —que coincide exactamente con el número de elementos `<testcase>`— frente a **361** por el atributo y **329** por los `.txt`. Los dos que faltan salen de una sola clase: `CatalogAggregatesTest` declara `tests="23"` y tiene 25 `<testcase>`, porque tres de sus clases `@Nested` definen un método con el mismo nombre, `referenciasObligatorias`, y el atributo agregado lo cuenta una vez. Comprobado sobre los 36 XML: es la única clase del proyecto con nombres repetidos y la única donde ambos números difieren. **Las cifras publicadas hasta ahora no estaban infladas: estaban ligeramente por debajo.**

**Corrección: la intermitencia ya no se atribuye a RabbitMQ, y ahora está cerrada por su lado.** El 2026-09-08 ya se había corregido el diagnóstico —la causa demostrada era `unico()` recortando `nanoTime()` con `substring(0, 10)`—; lo que se añade hoy es que ese arreglo **vivía solo en `feat/permission-model`**, que no está en `main`, y hubo que **traerlo por separado** a `fix/person-identity-sync` (`308cbb9`), aplicándolo además a `ClientSchemaTest`. La parte de `unico()` se da por cerrada; la de RabbitMQ sigue anotada como riesgo **sin caso reproducido**.

**Y un dato de contexto que faltaba en todas partes**: hay **dos ramas de código fuera de `main` que no descienden una de otra**. `feat/permission-model` parte de un `main` anterior y `fix/person-identity-sync` del actual. 361 **no** incluye las 139 pruebas de seguridad y 472 **no** incluye la baja en Keycloak; el día que se mergeen habrá que remedir, no sumar. Queda escrito en [[checklist-reutilizacion]] y en el `CLAUDE.md` de la raíz, que hablaba de una sola rama pendiente.

Nota nueva: [[sincronizacion-con-proveedor-de-identidad]]. Actualizadas [[dominio-persona-identidad]], [[seguridad-keycloak-backend]], [[decisiones-tecnicas-malphasos]], [[deuda-tecnica-y-riesgos]], [[stack-spring-boot-4-particularidades]], [[checklist-reutilizacion]], `index.md` y el `CLAUDE.md` de la raíz.

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

**Y una corrección de higiene**: `migracion-client-hallazgos` tenía un enlace con sintaxis de alias, `[[nota|texto]]`, que esta wiki no usa y que por tanto no apuntaba a ninguna parte. Corregido. Tras el reenfoque **no queda ningún enlace roto** salvo los dos intencionados y las plantillas del propio `CLAUDE.md`.

Nota nueva: [[hoja-de-ruta-producto]]. Actualizadas `SecondBrain/CLAUDE.md` (propósito, los dos ejes de etiquetas, reglas duras), `index.md` (reescrito), [[sintesis-malphasos]], [[migracion-client-hallazgos]], el frontmatter de las 46 notas y el `CLAUDE.md` de la raíz.

## [2026-09-09] ingest | El traslado que no cruza de cliente, y una prueba que pasa en vacío

Registro de `feat/relocation-same-client` (`3c002b2`, **sin mergear**), leído del commit y contrastado contra el código, no del resumen de quien lo construyó.

**El agujero que cerró.** `ClientEquipmentService.relocate` solo comprobaba que el área de destino existiera y estuviera activa. Nada impedía mover una unidad al área de **otro cliente**, y este wiki ya tenía escrito que el traslado es *el hecho que más importa de una unidad, porque cambia quién responde por ella*. Un traslado que cruza de cliente deja el historial de mantenimiento colgando de quien nunca la tuvo, sin error y sin rastro.

**Lo que esta entrada NO puede afirmar, y es su punto.** **La regla está construida y sin verificar.** `EquipmentChainServiceTest.Unidad.trasladar` **pasa en vacío**: el doble de `ServiceAreaServicePort` no tiene estubado `findOwningClient`, Mockito devuelve `null` en las **dos** consultas de propiedad y `Objects.equals(null, null)` deja pasar la guarda. Comprobado leyendo la prueba y el servicio.

La batería da **496 ejecuciones y cero fallos** sobre esa rama — medido aquí con `./mvnw test` y `rm -rf target/surefire-reports` antes—, **exactamente lo mismo que `main` en `01c3277`**, medido igual el mismo día, **porque no se añadió ninguna prueba**. Ese verde confirma que no se rompió nada; **no confirma que la regla funcione**, y así queda escrito en la nota, en la deuda propia y en el `CLAUDE.md` de la raíz.

**Y una circunstancia de proceso que no debe leerse como olvido**: el ciclo es `desarrollador → tester → wikista` y esta tanda **se saltó el paso central por decisión explícita del usuario**.

**Las decisiones que se registraron con su porqué**, todas en [[decisiones-tecnicas-malphasos]] y desarrolladas en la nota nueva: `client` **publica la respuesta** con `ServiceAreaServicePort.findOwningClient(UUID)` en vez de que `equipment` camine área → sede → cliente dos veces y se ate a la estructura interna de otro contexto; es una **llamada síncrona porque un evento no contesta preguntas** —con eventos habría que permitir el traslado y deshacerlo después—; la regla **vive en el servicio** porque un `CHECK` tendría que cruzar tres tablas para comparar dos clientes que ninguna guarda junta; **en el alta no se comprueba nada**, porque es el área elegida la que define de qué cliente pasa a ser la unidad; y el rechazo sale **409 con `ERR_EQUIPMENT_010`** —los datos son válidos, choca el estado—, con precedente verificado en `PersonControllerAdvice`, que responde 409 a `KeycloakUserAlreadyExistsException`.

**Una decisión de diseño que empujó el reparto de agentes, y por eso queda escrita.** Se amplió el puerto existente en vez de publicar uno dedicado al estilo de `PersonCommunicationPort`. La razón de arquitectura es buena —`equipment` ya dependía de ese puerto **y** del agregado `ServiceArea`, así que un segundo contrato no estrechaba nada—, pero **hubo una segunda**: inyectar una dependencia nueva cambia el constructor del servicio y rompe la compilación de una prueba que el `desarrollador` **tiene prohibido tocar**. Aquí coincidió con la opción correcta; no hay garantía de que siempre coincida, y el día que no coincida hay que notarlo en vez de dejarse arrastrar.

**El método, que es lo más reutilizable de esta tanda.** **El propio `desarrollador` detectó la prueba degenerada y la reportó sin poder arreglarla**, porque el archivo es del `tester`. Este wiki ya registra que los defectos aparecen **al comparar** y no al leer; éste añade una vía nueva: **apareció porque una frontera impidió el arreglo cómodo**. La única salida fue escribirlo, y quedó en el cuerpo del commit con las palabras "la regla queda SIN VERIFICAR". Una separación de dominios que impide taparlo en silencio convierte un hallazgo privado en uno público — un beneficio del reparto que no se buscaba al diseñarlo.

**Cuatro defectos anotados y no arreglados** (deuda propia en [[deuda-tecnica-y-riesgos]]): `relocate` comprueba el área **antes** que la existencia de la unidad, de modo que una unidad inexistente hacia un área cerrada da **400 y no 404** —anterior a este cambio y **fijado por una prueba**, `trasladarAAreaCerrada`, que nunca estuba la lectura de la unidad—; un traslado al área que la unidad ya ocupa recorre **seis lecturas** y llega al `save` para no emitir nada, con el área de destino leída **dos veces** en la misma transacción; y `findByServiceArea` llama a `findById` descartando el resultado.

**El cuarto no lo reportó nadie: salió de leer el javadoc del puerto al lado del catálogo de errores del módulo.** `findOwningClient` declara que lanza `HeadquarterNotFoundException`, y `EquipmentControllerAdvice` **no la mapea** —ni `GlobalControllerAdvice`—, así que saldría como **500**; y la `ServiceAreaNotFoundException` que puede venir del **área actual** de la unidad se traduce a 404 `ERR_EQUIPMENT_009`, que el llamante leerá como "el área de destino no existe". Las dos son hoy poco alcanzables porque las columnas son `NOT NULL` con clave foránea, pero el contrato del puerto las declara por escrito. Es la misma familia que [[traduccion-de-fallos-de-adaptadores]], vista desde un puerto que cruza de módulo.

**Correcciones de afirmaciones que resultaron falsas al verificarlas.**

1. **Las dos ramas que el wiki daba por «sin mergear» están en `main`.** `feat/permission-model` entró por `e6dda32` y `fix/person-identity-sync` por `258cd81`, las dos el 2026-09-09, y se borraron tras el merge. Lo afirmaban `index.md` (dos entradas), el `CLAUDE.md` de la raíz (la tabla de ramas y la fila de `bootstrap`, que decía que `ApiAuthority` **no existe en `main`**), [[checklist-reutilizacion]], [[hoja-de-ruta-producto]] —donde eran el **bloque 0** del orden propuesto— y dos filas de [[deuda-tecnica-y-riesgos]]. Corregido en todas, **sin borrar lo anterior**: la tabla vieja del checklist queda tachada, porque su aviso era correcto.
2. **La remedición que estaba anotada como pendiente, hecha.** El wiki decía «el día que se mergeen habrá que remedir, no sumar». `main` en `01c3277` da **496** elementos `<testcase>` —494 por el atributo `tests=`, 380 por los `.txt`—, 41 clases, cero fallos. **No es 472 + 361**: sumar habría producido un número inventado. Anotado en [[stack-spring-boot-4-particularidades]].
3. **«Los equipos se mueven dentro de una sede» se quedaba corto**, y por eso el traslado tenía un agujero. Una unidad se mueve entre áreas de una sede **y entre sedes del mismo cliente**. Corregido en [[migracion-equipment-hallazgos]].
4. **«Cinco invariantes de este tipo en el proyecto» pasa a seis**, y la sexta rompe el molde: las cinco anteriores comprueban que algo esté **activo**; ésta compara dos clientes que ninguna tabla guarda juntos. La frase «todas viven en los servicios, **con sus pruebas**» deja de ser cierta para la nueva.

**Lo que se decidió omitir por no tener valor duradero**: el detalle línea a línea del diff —está en `git show 3c002b2`, cuyo cuerpo ya explica el porqué mejor que cualquier paráfrasis—, y el texto exacto del javadoc y de la descripción de OpenAPI, que envejecen con el código y se leen en el archivo.

Nota nueva: [[regla-traslado-mismo-cliente]]. Actualizadas [[decisiones-tecnicas-malphasos]], [[migracion-equipment-hallazgos]], [[dominio-cliente]], [[dominio-equipo-mantenimiento]], [[deuda-tecnica-y-riesgos]], [[checklist-reutilizacion]], [[hoja-de-ruta-producto]], [[stack-spring-boot-4-particularidades]], `index.md`, `SecondBrain/CLAUDE.md` y el `CLAUDE.md` de la raíz.

## [2026-09-12] lint | Lo que el wiki escribió el 09 dejó de ser cierto el 10: la regla del traslado ya está verificada

**Qué lo motivó.** La entrada anterior registró la regla del traslado como **construida y sin verificar**, y lo era. El **2026-09-10** el `tester` pasó por `feat/relocation-same-client` y añadió `43de295`, encima de `3c002b2`. Esta pasada solo corrige el wiki; no hay código nuevo aquí.

**Verificado antes de escribir**, no copiado del resumen del `tester`: se leyó `git show 43de295` entero y se contaron los `@Test` añadidos archivo a archivo —4 en `ServiceAreaServiceTest`, 7 en `EquipmentChainServiceTest`, 1 en `EquipmentRestAdapterTest`, 1 en el nuevo `ClientEquipmentRelocationPersistenceTest`: **13**—, y se corrió la batería aquí con `./mvnw test` y `rm -rf target/surefire-reports` antes: **509** elementos `<testcase>`, **507** por el atributo `tests=`, **386** por los `.txt`, **42** clases, cero fallos, cero errores, cero omitidas. 496 + 13 cuadra, y aquí sumar **sí** es legítimo porque es la misma rama con pruebas encima, no dos ramas que no descienden una de otra.

**Tres afirmaciones del wiki quedaron falsas y se corrigieron sin borrarlas.**

1. **«La regla está construida y sin verificar».** Lo estuvo un día. La prueba degenerada está reparada —`trasladar` estuba ahora `findOwningClient` para el área actual y para la de destino— y con ella entran los dos casos frágiles que dan valor a la tanda: trasladar a **otra sede del mismo cliente** se permite —quien compare sedes en vez de clientes pasa todo lo demás y solo falla ahí— y un área **inactiva que además es de otro cliente** responde **400 por cerrada y no 409 por cliente**, porque el área se comprueba antes. Corregido en [[regla-traslado-mismo-cliente]] —la sección vieja se conserva entera bajo un aviso, porque es el registro de lo que costó saltarse el paso central del ciclo—, [[dominio-equipo-mantenimiento]], [[migracion-equipment-hallazgos]], [[decisiones-tecnicas-malphasos]], `index.md` y el `CLAUDE.md` de la raíz.
2. **«La prueba que pasa en vacío» figuraba como deuda propia pendiente.** Marcada **resuelta el 2026-09-10** en [[deuda-tecnica-y-riesgos]], con la fila conservada y tachada.
3. **«`feat/relocation-same-client` (`3c002b2`), también 496, porque no añadió ninguna prueba».** La rama está en `43de295` y en **509**. Corregido en [[checklist-reutilizacion]], [[stack-spring-boot-4-particularidades]] —donde la tabla de conteos gana una columna— y el `CLAUDE.md` de la raíz.

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

**Una corrección más, encontrada al final de esta misma pasada y no por leer el wiki.** Al comprobar de qué desciende `feat/work-order-schema` —`git merge-base --is-ancestor 43de295 24e7640`— apareció que **`feat/relocation-same-client` ya está en `main`**, mergeada por `1ef55cf`, y su pasada de wiki por `55e0a5d`. Todo lo que el wiki escribió esta misma mañana la daba por **rama pendiente**. Quedaron falsas cuatro afirmaciones y se corrigieron sin borrarlas: `main` no está en `01c3277` sino en **`55e0a5d`**; su batería no es 496 sino **509**; la fila de `equipment` del `CLAUDE.md` de la raíz situaba el traslado «en rama sin mergear»; y la tabla de conteos de [[stack-spring-boot-4-particularidades]] etiquetaba como «`main`» una columna que ya no lo describe. **Las 509 de `main` no se remidieron**, y se dice así en vez de afirmar una medición que no se hizo: `git diff 43de295 main -- malphasos/` sale vacío, de modo que el código es el mismo que se midió horas antes. Tocadas [[regla-traslado-mismo-cliente]], [[checklist-reutilizacion]], [[stack-spring-boot-4-particularidades]], [[hoja-de-ruta-producto]] y el `CLAUDE.md` de la raíz.

**La lección de método, que es la parte reutilizable**: **el estado de las ramas caduca más rápido que ninguna otra cosa que este wiki escribe**, y no hay ninguna nota que avise cuando caduca. Lo encontró un `git merge-base` hecho para otra cosa. Conviene que toda pasada de wiki empiece comprobando `git branch --contains` de las ramas que el wiki declara pendientes, en vez de fiarse de lo que dijo la pasada anterior — aunque fuera del mismo día.

## [2026-09-13] ingest | Órdenes de trabajo, tandas 2 a 4: el módulo cerrado, y la regla que faltaba

**El wiki se había quedado atrás tres tandas.** La pasada anterior registró el esquema y dio el módulo por «existe el esquema y nada más». El dominio se mergeó a `main` (`97ef74f`) **sin pasada de wiki**, y la aplicación y el REST se escribieron después. Esta entrada cubre las tres.

**Lo construido**: el agregado `WorkOrder` de Generación 2 con siete eventos; `WorkOrderService` con las reglas que exigen preguntar a otros tres módulos; la persistencia con conciliación del alcance; y nueve operaciones REST con catálogo de errores propio y grupo de OpenAPI. Reescrita [[dominio-orden-trabajo]] de arriba abajo.

**El hallazgo de la pasada, y no salió de leer código.** La tanda 1 había dejado listadas **siete reglas** que el esquema no podía defender. Al construir la tabla de estado de esas siete —columna por columna, regla contra código— apareció que **una no está construida**: `requireEquipmentBelongsTo` comprueba que el equipo sea del **cliente** de la orden, y nunca que su área sea de la **sede** de la orden. Una orden del cliente A en la sede Norte admite un equipo del cliente A que está en la sede Sur. Es **la misma mentira histórica que el módulo se diseñó para impedir**, un nivel más abajo, y ahora sí es explotable porque ya hay API que crea órdenes. El arreglo es barato: el `ServiceArea` con su `idSede` **ya está cargado en esa misma línea**.

**La lección de método.** La lista de siete se escribió sin columna de estado, y **una lista de reglas sin columna de estado es una lista que nadie contrasta**: se lee como inventario, no como pendiente. La tabla que ahora tiene la nota existe para eso. Es exactamente la disciplina que `CLAUDE.md` ya enunciaba —«los defectos aparecen al comparar»— aplicada al propio wiki en vez de a dos módulos.

**Segundo hallazgo, por `grep`.** **Nada prueba la persistencia de este módulo**: ni una mención de `WorkOrderPersistenceAdapter`, `WorkOrderPersistenceMapper` ni `WorkOrderRepository` en `src/test`. Queda sin ejercer la pieza con más lógica fuera del dominio —la conciliación que desactiva filas en vez de borrarlas y reactiva con el área nueva— y el `@Query` que filtra por `estadoActivo`. `client`, `location` y `person` sí tienen su `…PersistenceAdapterTest`; `equipment` tampoco. **La ausencia se repite en los dos módulos más recientes**, que es lo que la convierte en patrón y no en olvido.

**La centinela hizo su trabajo de punta a punta.** `lasAutoridadesDeWorkOrderSiguenSinModulo` se puso roja con el primer controlador, tal como estaba anunciado en `CLAUDE.md`, en [[modelo-de-permisos]] y en [[dominio-orden-trabajo]]; se retiró en ese mismo commit junto con el `filter` que eximía a `work-order` en `ningunaAutoridadSobra`. La sustituyen dos pruebas, y la segunda —**`assign` es la única operación que exige `work-order.assign`**— es la que impide que la separación entre repartir trabajo y alterarlo desaparezca en silencio. Segundo caso del patrón de omisión consciente cerrándose limpiamente, tras el de `equipo_cliente`.

**Qué se verificó por mutación, porque el verde no basta.** Se rompió el patrón del grupo de OpenAPI (`/work-orders/**` → `/workorders/**`) y `recursoDocumentado` falló; se aflojó la guarda de filtros excluyentes (`> 1` → `> 99`) y `filtrosExcluyentes` falló. Producción restaurada en los dos casos. Importa sobre todo en el primero: **un grupo que no casa con ninguna ruta no da ninguna señal**, y una prueba mal escrita contra un fallo silencioso deja el proyecto donde estaba pero creyendo lo contrario. Ampliado [[openapi-swagger]] con el caso y con el detalle de que `/work-orders/**` casa también con `/work-orders` a secas.

**El marcador de requisitos sube de 8 a 11, no a 15.** El módulo está terminado y aun así **cuatro de sus siete RF siguen abiertos**, porque describen un formulario y no hay frontend: RF-03, RF-04, RF-06 y RF-07 son pasos de interfaz. El criterio aplicado queda escrito en [[hoja-de-ruta-producto]] para que se pueda discutir: cuenta como implementado lo que el backend satisface por completo; dar por hecho lo demás inflaría la cifra y haría desaparecer de la cuenta el trabajo de frontend sin haberlo hecho.

**Confirmado de paso**: el ciclo del grafo declarado de la ERS —RF-05 → RF-07 → RF-06 → RF-05— **no bloqueó nada**. El módulo se construyó ignorándolo, y las tres piezas salieron juntas como partes del mismo agregado, tal como [[hoja-de-ruta-producto]] predijo. Es la prueba de cuál de los dos grafos manda.

**Conteos, todos medidos borrando `target/surefire-reports` antes.** `main` (`97ef74f`) da **580** elementos `<testcase>` y 44 clases; `ceadba1` da **612** y 46. Los puntos intermedios se marcan como **derivados** en [[dominio-orden-trabajo]] y no como medidos: 580 + 17 + 14 + 1 = 612 cuadra con lo medido, y ese +1 es el centinela sustituido por dos pruebas. Cero fallos en ambos.

**Una cifra mal dicha, anotada en vez de reescrita.** El cuerpo del commit `ceadba1` dice «ocho operaciones»; **son nueve**, contadas sobre las anotaciones de mapeo. El error es del mensaje, no del código, y queda en [[dominio-orden-trabajo]] porque un cuerpo de commit no se corrige sin reescribir la historia.

**Deuda.** Una fila propia nueva —la persistencia sin pruebas—, de **24** a **25**. La fila de las siete reglas pendientes se reescribe: ya no son siete pendientes sino **una**, con severidad media y explotable. Y la fila de las autoridades de `work-order` sin módulo detrás **se cierra**.

**Dos hallazgos más, aparecidos en la revisión de esta misma pasada y no al escribirla.** Al remedir para poder citar las tres fuentes de conteo: (1) los `.txt` dan **418 tanto en `24e7640` como en `main`** —ni un punto de diferencia— pese a las **39** pruebas que añade `WorkOrderTest`, porque esa clase tiene 13 clases `@Nested` y **ninguna prueba suelta**; es el caso extremo de algo que esta nota ya decía, y el mejor argumento para no volver a citar esa fuente. (2) El desajuste `<testcase>` – atributo `tests=` **pasa de 2 a 3 por primera vez**: hay un segundo caso además de `CatalogAggregatesTest`, y es `WorkOrderServiceTest`, donde **`ordenInexistente` aparece en dos clases `@Nested`**. El desajuste no es una rareza de una clase: **crece sin avisar** según se escriben pruebas. Ampliado [[stack-spring-boot-4-particularidades]] con una tabla de dos columnas más.

**Tres notas más llevaban afirmaciones ya falsas y se corrigieron dejando constancia**, encontradas al revisar y no al escribir: [[regla-traslado-mismo-cliente]] seguía diciendo «seis construidas y siete previstas» y situando `main` en `55e0a5d`; [[checklist-reutilizacion]] daba `feat/work-order-schema` como la rama de código fuera —**tercera corrección a esa misma línea en dos días**—. Esa reincidencia es el dato: el estado de las ramas caduca más rápido que nada de lo que este wiki escribe.

**Y una predicción del wiki se cumplió literalmente.** [[regla-traslado-mismo-cliente]] había escrito que una de las siete reglas «es hermana de ésta y probablemente reutilice el mismo `findOwningClient`». Lo reutiliza exactamente, en `requireEquipmentBelongsTo`. Es la mejor justificación que ha dado el proyecto de haber publicado ese contrato como **puerto síncrono** y no como evento: la segunda pregunta llegó cuatro días después y no hubo que tocar `client` para contestarla.

**Tocadas**: [[dominio-orden-trabajo]] (reescrita), [[deuda-tecnica-y-riesgos]], [[modelo-de-permisos]], [[hoja-de-ruta-producto]], [[openapi-swagger]], [[decisiones-tecnicas-malphasos]], [[stack-spring-boot-4-particularidades]], [[regla-traslado-mismo-cliente]], [[checklist-reutilizacion]], `index.md` y el `CLAUDE.md` de la raíz.

**Comprobado al empezar, siguiendo la lección de la pasada anterior**: `git branch --contains` y `git log main..HEAD` antes de escribir nada sobre ramas. Sirvió — el `CLAUDE.md` de la raíz situaba `main` en `55e0a5d` con una sola rama fuera, y **estaba en `97ef74f`** con el dominio ya dentro.

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

**Tocadas**: [[dominio-orden-trabajo]], [[deuda-tecnica-y-riesgos]], [[regla-traslado-mismo-cliente]], [[decisiones-tecnicas-malphasos]], [[stack-spring-boot-4-particularidades]] y el `CLAUDE.md` de la raíz.


## [2026-09-13] lint | Los tres merges, y la vez que no deducir salió a cuenta con número

**El quinto módulo entra en `main`.** Tres merges `--no-ff` en orden —`e69437d` las tandas 3 y 4, `1adc2fc` la regla que faltaba, `b9563a9` las dos pasadas de wiki— y las cuatro ramas borradas, incluida `feat/work-order-application`, que llevaba colgada apuntando a un ancestro. **No queda ninguna rama de código fuera de `main`**, por primera vez desde el 2026-09-09.

`main` (`b9563a9`) mide **614** elementos `<testcase>`, 46 clases, cero fallos, cero errores, cero omitidas. `git fsck --strict` limpio, sin stashes y sin worktrees.

**Y una comprobación que esta vez tiene número.** La pasada anterior dejó dos casillas de la tabla de conteos **con guiones**, diciendo que afirmar «**611 y 435**» sin haberlos medido sería inventarlos. Medidos tras el merge: **611 y 433**. **Uno acertado y el otro no.**

El fallo habría sido en los `.txt`, y por la razón que [[stack-spring-boot-4-particularidades]] lleva repitiendo: las dos pruebas nuevas viven dentro de una clase `@Nested` ya existente, así que esa fuente **no las cuenta y se queda clavada en 433**. Quien dedujera «+2 en todo» se equivocaría justo en la fuente cuyo comportamiento raro está documentado en la misma nota, tres párrafos más arriba.

**Es la mejor defensa que ha tenido la regla de no deducir**: no falla por principio, falla **una de cada dos veces**, y la que falla es la que uno cree tener entendida. Registrado con la cifra en [[stack-spring-boot-4-particularidades]].

**Cuarta corrección en dos días a la línea de «qué rama queda fuera»**, en [[checklist-reutilizacion]] y en el `CLAUDE.md` de la raíz. A estas alturas la reincidencia ya no es un dato sino una regla: **toda pasada de wiki empieza por `git log main..HEAD`**, nunca por lo que dijo la anterior.

**Tocadas**: [[stack-spring-boot-4-particularidades]], [[dominio-orden-trabajo]], [[checklist-reutilizacion]] y el `CLAUDE.md` de la raíz.

## [2026-09-13] lint | El hash de una rama no se escribe; el de un commit sí

**Convención nueva, nacida de un error propio cometido horas antes.** El `CLAUDE.md` de la raíz fijó el hash de `main` cuatro veces en dos días y las cuatro caducó. La cuarta es la que enseña algo: **caducó por su propio merge** — la pasada de wiki que anotaba «`main` está en `b9563a9`» dejaba `main` en otro commit al mergearse. Es regresión infinita, y ninguna cantidad de `git log main..HEAD` la arregla, porque la regla anterior —comprobar antes de escribir— no cubre el caso en que **escribir es lo que invalida el dato**.

La distinción que queda, y que no es «no escribir hashes»:

| Un hash que nombra… | ¿Se escribe? |
|---|---|
| un commit concreto: una medición, un merge, un arreglo | **Sí.** Es inmutable y verificable |
| el valor de hoy de un puntero que se mueve (`main`) | **No.** Caduca sola, y a veces al escribirla |

Por eso «614 medidos sobre `b9563a9`» y «mergeada por `1ef55cf`» **se quedan**, y las tablas de conteo pasan a nombrar **commits en vez de ramas**: una medición pertenece al commit donde se contó y ahí se queda, mientras que `main` apunta cada día a otro sitio.

**El log no se toca.** Sus entradas están fechadas y eran ciertas al escribirse; reescribirlas para que sigan siendo ciertas hoy sería justo lo contrario de lo que un registro cronológico hace. La regla vale para las notas que describen el presente, no para las que fechan el pasado.

**Tocadas**: `CLAUDE.md` de la raíz (con la tabla de la distinción), `SecondBrain/CLAUDE.md` (regla dura nueva), [[dominio-orden-trabajo]] y [[stack-spring-boot-4-particularidades]].


## [2026-09-13] ingest | La persistencia probada, y el defecto que la prueba destapó antes de pasar

**Cierra el último pendiente del quinto módulo.** `WorkOrderPersistenceAdapterTest`, **10 pruebas** contra un PostgreSQL real vía Testcontainers. Hasta hoy `grep` sobre `src/test` no devolvía ni una mención del adaptador, el mapper ni el repositorio de `work-order`.

**La primera ejecución no falló por una aserción: falló con `LazyInitializationException`.** `WorkOrderPersistenceAdapter` **no llevaba `@Transactional`** y el mapper recorre el alcance, que es una colección perezosa con `open-in-view` desactivado.

**Por qué sobrevivió a cuatro tandas**: `WorkOrderService` siempre abre transacción, así que desde el API no se veía. El adaptador era inservible por su cuenta **y nadie lo llamaba por su cuenta, precisamente porque no había pruebas**. El defecto y su invisibilidad tenían la misma causa. De los **tres** adaptadores con colecciones propias —`client`, `person`, `work-order`— era el único sin la anotación, y el de `client` hasta lo explica en su javadoc.

**Se arregló en producción, no se rodeó en la prueba.** Envolver el test en una transacción lo habría puesto en verde describiendo el defecto en vez de detectarlo, que es exactamente lo que `CLAUDE.md` prohíbe. La regla entró como convención de persistencia: **un adaptador que mapea una colección perezosa lleva `@Transactional`**; sin él depende de que el llamante abra una, y eso es una dependencia que el tipo no declara.

**Qué comprueban las diez**, y por qué no es el ida y vuelta: la **conciliación del alcance**. Retirar deja la fila **inactiva y no la borra**; readmitir **reactiva esa misma con el área nueva** y sigue habiendo una sola; `toDomain` no vuelve a cargar lo retirado; guardar dos veces no duplica; un traslado del equipo no reescribe el área congelada —comprobado ahora desde el lado de la orden, no solo desde el del esquema—; y `findByEquipment` ignora lo que salió. **Las comprobaciones van contra la tabla con SQL directo**: preguntarle al agregado lo contestaría el mapper, que es la pieza bajo prueba.

**Cuatro mutaciones, cuatro señales.** Quitar la lectura previa del adaptador, quitar el filtro `estadoActivo` del `@Query`, dejar de desactivar la fila que sale, y no actualizar el área del readmitido. Cada una la detecta la prueba que le toca, y dos de ellas **solo** esa prueba.

**Un tercer dato sobre el conteo, y ya es tendencia.** Los `.txt` dan **433 antes y después** de añadir diez pruebas, porque la clase nueva es enteramente `@Nested`. Sumando la tanda: de **83** pruebas añadidas desde `24e7640`, esa fuente recoge **15**. Deja de ser una rareza y pasa a ser **una fuente que miente por defecto** con el estilo de pruebas de este proyecto. Registrado con la tabla en [[stack-spring-boot-4-particularidades]].

**Deuda: 27 registradas, 17 abiertas.** Se cierran dos —la persistencia sin probar y el `@Transactional` ausente— y se abre una: **`equipment` tampoco prueba su persistencia**, la misma ausencia que se señaló en dos módulos y se cerró en uno.

**Tocadas**: [[dominio-orden-trabajo]], [[deuda-tecnica-y-riesgos]], [[stack-spring-boot-4-particularidades]] y el `CLAUDE.md` de la raíz.

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

**Tocadas**: [[arquitectura-frontend]], [[integracion-keycloak-frontend]], [[hoja-de-ruta-producto]], `index.md` y el `CLAUDE.md` de la raíz, que decía que el código vivía **exclusivamente** en `malphasos/`. **Nuevas**: [[arquitectura-frontend-malphasos]], [[sistema-de-diseno-malphasos]].

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

**Tocadas**: [[modelo-de-permisos]], [[dominio-persona-identidad]], [[deuda-tecnica-y-riesgos]], `index.md` y el `CLAUDE.md` de la raíz.

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

**Tocadas**: [[seguridad-keycloak-backend]], [[integracion-keycloak-frontend]], [[deuda-tecnica-y-riesgos]], `index.md` y el `CLAUDE.md` de la raíz.
