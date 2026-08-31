---
name: decisiones-tecnicas-malphasos
description: Registro cronológico de decisiones técnicas tomadas al construir MalphasOS, con su justificación y en qué se apartan del proyecto original
tags: [malphasos, decisiones, adr]
updated: 2026-08-29
---

# Decisiones técnicas de MalphasOS

Registro de decisiones tomadas al construir MalphasOS, en el espíritu de un ADR ligero. Se añade una entrada cada vez que se decide algo que condiciona el resto del proyecto. Complementa [[checklist-reutilizacion]]: el checklist dice *qué falta*, esto dice *qué ya se decidió y por qué*.

## Metodología de trabajo

**Micro-commits con revisión previa.** Un cambio lógico por commit, cada preocupación en su propia rama partiendo de `main` actualizado, merge con `--no-ff` para que el historial muestre de dónde vino cada cosa. El usuario revisa el diff antes de cada commit. Nada se commitea sin haber sido verificado de verdad (tests corriendo, servicios levantados).

## Stack y build

| Decisión | Elegido | Por qué |
|---|---|---|
| Spring Boot | **4.1.1** (original: 4.0.6) | Es lo que generó Spring Initializr; no había razón para bajar de versión. Trae diferencias reales, ver [[stack-spring-boot-4-particularidades]] |
| Java | **21** (LTS) | Coincide con el proyecto original. El `JAVA_HOME` de la máquina apunta a Java 26 vía mise, así que el build se corre con `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` |
| groupId | `com.malphasos` | MalphasOS es producto propio, separado del namespace `com.bolivar.bioingenieria` del cliente |
| MapStruct | **1.6.3** (original: 1.5.5.Final) | Mejor soporte para Java 21+; el patrón de [[patron-mapper-mapstruct]] no depende de la versión |
| Jackson | **ninguna dependencia explícita** | Spring Boot 4 ya trae Jackson 3 de serie; declararlo a mano fue lo que generó la mezcla rara en el original |

## Base de datos

| Decisión | Elegido | Por qué |
|---|---|---|
| Esquema | **Flyway** (original: scripts `initdb`) | Los scripts de `/docker-entrypoint-initdb.d` solo corren al crear el volumen: cambiar el esquema obliga a borrar la base. Flyway versiona y es reproducible en cualquier entorno. Ver [[esquema-bd-v4]] para el modelo destino |
| Propiedad del esquema | **Flyway, exclusivamente** | `ddl-auto: validate` — Hibernate nunca modifica el esquema, solo verifica que las entidades coincidan con lo que crearon las migraciones |
| Primera migración | **baseline sin tablas** | `V1__baseline.sql` solo habilita `pgcrypto` y establece el punto de partida del versionado. Cada módulo de dominio traerá su propia migración, en vez de congelar de golpe decisiones de modelado que siguen abiertas (ver [[relacion-manager-persona]]) |
| PKs | **UUID desde el día uno** | El original tardó cuatro iteraciones en estandarizarlas, ver [[evolucion-esquema-v1-v4]] |
| `open-in-view` | **false** | Evita resolver lazy-loading durante el renderizado de la respuesta; obliga a decidir la carga de datos en la capa de aplicación |

## Testing

| Decisión | Elegido | Por qué |
|---|---|---|
| Base de datos en tests | **Testcontainers** | Levanta un PostgreSQL 17 real, el mismo motor que producción, así las migraciones de Flyway se validan de verdad. H2 habría obligado a escribir SQL al mínimo común denominador, renunciando a `jsonb`, extensiones y tipos propios de Postgres que el dominio sí necesita |
| Qué se testea | **comportamiento, no solo arranque** | Además de `contextLoads`, hay tests que verifican que Flyway efectivamente corrió y que el baseline habilitó la extensión |

## Infraestructura

| Decisión | Elegido | Por qué |
|---|---|---|
| Puerto del backend | **8081** | El 8080 lo ocupa Keycloak, que es su puerto convencional y el que referencian el frontend y la configuración del original |
| Keycloak hostname | **`localhost`** (original: `keycloak.test`) | No obliga a editar `/etc/hosts`. Exige separar `issuer-uri` de `jwk-set-uri`, porque el navegador y el backend alcanzan Keycloak por direcciones distintas: ver [[issuer-uri-vs-jwk-set-uri]] |
| Import de realms | **`--import-realm`** | La variable `KEYCLOAK_IMPORT` del original es de la era WildFly y Keycloak 26 la ignora, ver [[docker-compose]] |
| Servicio de la app en compose | **incluido** desde el commit de contenedorización | Construido desde el `Dockerfile` del módulo, con healthcheck vía Actuator. Ver [[dockerfile-y-contenedores]] |
| Secretos | **`.env` ignorado, `.env.example` versionado** | El repo nunca contiene credenciales reales |
| Theme de Keycloak | **el de por defecto** | `sigma-theme` lleva branding de Bolívar Bioingeniería; MalphasOS tendrá el suyo cuando tenga identidad visual |

## Organización del código

| Decisión | Elegido | Por qué |
|---|---|---|
| Nombres de paquetes | **camelCase, sin sufijo `_hexagon`** | El original usa snake_case (`equipment_hexagon`, `rest_controllers`), que va contra la convención de Java. Se descartó el todo-minúscula pegado (`technicalverification`) por ilegible: camelCase se aparta de la letra de la convención pero gana en claridad |
| Estructura por módulo | **La de `location_hexagon`/`equipment_hexagon`** | Es el patrón de Generación 2, ver [[evolucion-arquitectonica-crud-a-cqrs]]. `application/{ports,services/<entidad>/commands}`, `domain/<entidad>/events`, `infrastructure/{input,output}` |
| Excepciones de dominio | **`domain/exception` en todos los módulos** | El original es inconsistente: `location_hexagon` las pone en `infrastructure/output/errors`. Se unifica y se elimina esa carpeta; `infrastructure/input/errors` se conserva para el ControllerAdvice y el DTO de error |
| Rutas del API | **`/v1/api/<recurso>` en plural** | El original mezcla `/client/v1/api` con `/v1/api/equipment`. Se unifica; los grupos de OpenAPI ya fijan esta convención |
| `TechnicalVerificationEquipment` | **No es paquete de dominio** | El propio código original aclara que no es entidad ni agregado, sino DTO de transporte. Va en `infrastructure/input/model` |

## Configuración transversal (bootstrap)

Migrado desde el original con correcciones, no como copia literal. Ver [[manejo-global-excepciones]] y [[seguridad-keycloak-backend]] para el detalle de qué se corrigió.

| Decisión | Elegido | Por qué |
|---|---|---|
| `GlobalErrorResponse` | **`record`** | Una respuesta de error no debe mutar una vez construida; el original era clase con `@Setter` |
| Detalles de error de BD | **Solo al log, nunca al cliente** | El original devolvía `ex.getMessage()` de `DataAccessException`, exponiendo nombres de tablas y SQL |
| Client id de Keycloak | **Configurable** (`app.security.client-id`) | Estaba hardcodeado como `"sigma-api"` |
| Lectura de claims del token | **Pattern matching de Java 21** | El original hacía casts sin verificar: un token con forma inesperada lanzaba `ClassCastException` |
| Seguridad apagada | **Cadena `permitAll` explícita** (`SecurityDisabledConfig`) | Sin ella, apagar la seguridad no abre la API sino que activa la cadena por defecto de Spring (basic auth con contraseña generada). Ver [[seguridad-keycloak-backend]] |
| Estado actual de la seguridad | **Desactivada en `application.yaml`** | El realm `malphasos-realm` no existe todavía; definir `issuer-uri` contra un realm inexistente impide arrancar. El código es seguro por omisión (`matchIfMissing = true`) |

## Contenedor y despliegue local

| Decisión | Elegido | Por qué |
|---|---|---|
| Imagen de runtime | **JRE sobre Alpine** | El original usaba `eclipse-temurin:21` (JDK completo) para ejecutar. Ejecutar no necesita compilador: menos peso y menos superficie de ataque |
| Usuario del contenedor | **`malphasos`, sin privilegios** | El original corría como root |
| Contexto de build | **`.dockerignore`** | El original no tenía, así que el contexto arrastraba `target/`, `.git/` y el `.env` |
| Arranque | **`ENTRYPOINT`** en vez de `CMD` | El contenedor es la aplicación; los argumentos extra le llegan a ella |
| Proyecto de Compose | **`name: malphasos`** | Agrupa contenedores, red y volúmenes como una unidad, sin depender del nombre del directorio |
| Healthcheck de la app | **Spring Boot Actuator**, solo `health` | Una comprobación de puerto abierto no distingue una app sana de una con la base de datos caída. El resto de endpoints de Actuator queda fuera porque revelan beans, configuración y variables de entorno |
| Detalle del health | **`show-details: when-authorized`** | Un cliente anónimo ve solo `{"status":"UP"}`; el desglose por componente exige autenticación |

⚠️ **Trampa encontrada**: al incorporar Actuator, su health indicator de RabbitMQ intenta conectarse al broker. Por defecto apunta a `localhost:5672`, que dentro del contenedor no existe, así que el healthcheck reportaba DOWN con la aplicación perfectamente sana. Hay que configurar `spring.rabbitmq.host` apuntando al nombre del servicio.

## Módulo de personas

| Decisión | Elegido | Por qué |
|---|---|---|
| Patrón | **Generación 1**, como el original | Decisión explícita del usuario. Con el enum y las reglas de validación en el dominio, la distancia hasta Generación 2 quedó corta: falta `extends AggregateRoot`, una factoría y los eventos |
| `tipoPersona` | **enum `PersonType`** (era `String`) | Como texto libre nada impedía escribir `"ingeniero"`; el error solo aparecía al insertar en la base |
| `RoleType` | **Separado de `PersonType`**, no fusionado | En el original ambos viajan por separado y esa separación parece deliberada: no toda persona necesita usuario |
| Reglas de combinación de tipos | **En el dominio** | En el esquema eran una función sin trigger: código muerto. En el dominio dan mensajes útiles y se prueban sin base de datos |
| Adaptador de comunicación interna | **Aplazado** | Solo existe para que el módulo de clientes hable con personas, y ese módulo aún no existe |
| `createSuperAdminUser` | **Fuera del puerto** | Devolvía `null` sin implementar |
| Idioma del código | **Identificadores en inglés**, campos del dominio en español | Los campos son vocabulario del negocio y coinciden con las columnas de la base |
| Rutas del API | `/v1/api/persons`, subrecursos anidados | El original repartía las de registro entre `/vi/`, `/v1/` y `/v2/` |

## Identidad y seguridad

| Decisión | Elegido | Por qué |
|---|---|---|
| Realm | **Transformado del export original**, no reescrito | Conserva los 21 flujos de autenticación y 14 client scopes internos que un realm escrito a mano perdería |
| Validación de tokens | **`issuer-uri` público + `jwk-set-uri` interno** | Las dos URL no coinciden en Docker; declarar solo la primera produce 401 con tokens válidos. Ver [[issuer-uri-vs-jwk-set-uri]] |
| `frontendUrl` del realm | **Vacío** | Fijado anula `KC_HOSTNAME` y ata el realm a una URL concreta |
| Fuerza bruta | **Activa**, bloqueo a los 5 intentos | El original la tenía apagada y permitía 30 |
| Política de contraseñas | `length(12) and notUsername and notEmail` | ⚠️ Cambia comportamiento: las contraseñas débiles se rechazan al crear usuarios |
| Flujo del client público | **Sin `directAccessGrants`** | La SPA usa PKCE; el flujo de contraseña directa expone credenciales sin aportar nada |
| Roles granulares | **Definidos pero sin usar**, como en el original | Aplicarlos exigiría cambiar los `@PreAuthorize` de todos los controladores; queda como decisión abierta |
| Seguridad en pruebas | **Apagada salvo en `SecurityIntegrationTest`** | Cada prueba se centra en su capa; la protección se verifica en un sitio, con el decodificador de JWT sustituido por un doble para no necesitar Keycloak |
| Secretos de los clients confidenciales | **Valores de desarrollo explícitos en el realm** | La alternativa —quitar el campo para que Keycloak genere uno aleatorio en cada importación— es más segura, pero obliga a copiar el secreto a mano desde la consola cada vez que se recrea el contenedor. Este realm ya es de desarrollo y contiene la contraseña de su usuario de pruebas, así que se prefirió la reproducibilidad al clonar. Los secretos se llaman `dev-only-...-change-in-any-real-environment` para que su propio nombre advierta |
| Fallo del servicio contra Keycloak | **502, no 401** | Quien no consiguió autenticarse es el servicio contra su dependencia, no quien llama. Un 401 le pide al cliente arreglar algo que no está en su mano. Ver [[traduccion-de-fallos-de-adaptadores]] |

## Pruebas manuales y documentación viva

| Decisión | Elegido | Por qué |
|---|---|---|
| Autenticación en Swagger | **OAuth2 Authorization Code + PKCE** | El esquema bearer obliga a pegar un token que caduca a los cinco minutos. Con el flujo completo, el botón Authorize inicia sesión en Keycloak y el token se inyecta solo |
| Usuario de desarrollo | **`dev.admin` en el realm versionado**, dentro del grupo `admins` | El realm no traía ningún usuario con el que iniciar sesión. Queda reproducible para quien clone el repositorio. ⚠️ Su contraseña está en el archivo: ese realm es solo para desarrollo local |
| Violaciones de integridad | **409, no 500** | El origen es el dato que envió el cliente, no un fallo del servidor |

## Módulo de clientes (decidido el 2026-08-29, antes de escribir código)

| Decisión | Elegido | Por qué |
|---|---|---|
| Patrón arquitectónico | **Generación 2**: `AggregateRoot` + commands + eventos de dominio | `client` es Generación 1 en el original y el wiki lo marca `reusable:no` como patrón. Con 96 archivos, reconvertirlo después sale mucho más caro que construirlo bien. Obliga a migrar antes `shared/domain/events`, coste que `equipment` ya no vuelve a pagar. Ver [[evolucion-arquitectonica-crud-a-cqrs]] |
| Identidad encargado↔persona | **Entidad propia con `@MapsId`** sobre `Person` | Conserva la forma del esquema —`encargado.k_identificador` como PK y FK a la vez— sin migrar datos, y hace visible para JPA una relación que hoy solo existe en la base y en el orden de dos llamadas de un método privado. La alternativa, absorber el rol dentro de `Person`, da un modelo más simple pero invierte la dependencia entre módulos: `person` pasaría a conocer sedes y áreas. Ver [[relacion-manager-persona]] |
| `tipoEncargado` | **Enum `ManagerType`** (`HEADQUARTER`, `SERVICE_AREA`) | Es un `String` en el original, con un javadoc que documenta dos valores que la restricción rechaza. Mismo tratamiento que recibió `tipoPersona` |
| Orden de migración | ~~`PersonCommunicationPort` → `shared` → `V3__client.sql`~~ → **`location` completo antes que `client`** | Corregido el 2026-08-29, ya empezado: `sede.k_id_ciudad` es `NOT NULL` y apunta a `ciudad`, que a su vez necesita `pais`. Sin las tablas de ubicación la migración de clientes no se puede ni escribir. `location` es además el módulo más pequeño de Generación 2, así que estrena el contrato de eventos con poco en juego. Ver [[migracion-location-hallazgos]] |
| `representante_legal` | **Se porta y se implementa** | La tabla existe en el esquema y no tiene una sola línea de código en el original. Vincula persona y cliente por identidad compartida, igual que `encargado`, y es lo que le falta al tipo `CEO_CLIENT` para asociarse a su cliente: sin ella el enum y el grupo del realm existen sin poder usarse |

## Módulo de ubicaciones (migrado el 2026-08-29)

| Decisión | Elegido | Por qué |
|---|---|---|
| Identidad de `pais` y `ciudad` | **UUID**, con el código ISO como llave natural | El original usaba el código del país, un `varchar(3)`, como llave primaria, y ese `varchar(3)` era el destino de las claves foráneas de `ciudad`, `cliente` y `fabricante`. El código se conserva único, con formato validado, pero deja de ser aquello a lo que apunta medio esquema |
| Unicidad del nombre de ciudad | **Dentro de su país**, no global | Hay un Córdoba en España y otro en Argentina. El original no declaraba unicidad de ninguna clase |
| Igualdad de los agregados | **Por identidad**, nunca por datos | Dos objetos que representan el mismo país lo son aunque difieran sus datos: uno puede ser una versión más vieja del otro. Con `callSuper = true` la comparación acababa en la identidad de `Object` y era siempre falsa |
| Reconstrucción desde persistencia | **`rehydrate(...)`, que no emite eventos** | Recuperar algo de la base no es un hecho del dominio. Si emitiera, cada lectura publicaría un evento de creación |
| Nombre de los eventos de baja | **`Deactivated`, no `Deleted`** | Aquí no se borra nada: el registro permanece con `b_estado_activo` en falso. Quien lea "deleted" concluye razonablemente que la fila ya no existe |
| Renombrar vs. trasladar una ciudad | **Dos eventos distintos** | Mover una ciudad de país cambia la cobertura de todas las sedes que hay en ella; renombrarla no afecta a nadie. Con un único `CityUpdatedEvent` había que comparar el payload contra el estado anterior para saber qué cambió |
| Cambios que no cambian nada | **No emiten evento** | Renombrar con el mismo nombre no registra nada. Anunciar un cambio que no ocurrió obliga a cada consumidor a defenderse de duplicados |

## Pendientes de decidir

- Si el manejo de excepciones usa una base común entre módulos o se repite por bounded context: ver [[manejo-global-excepciones]].
- Organización del frontend por feature vs por tipo técnico: ver [[arquitectura-frontend]].

## Notas relacionadas

[[stack-spring-boot-4-particularidades]] · [[migracion-location-hallazgos]] · [[traduccion-de-fallos-de-adaptadores]] · [[relacion-manager-persona]] · [[dominio-cliente]] · [[checklist-reutilizacion]] · [[alcance-malphasos]] · [[sintesis-malphasos]] · [[docker-compose]]
