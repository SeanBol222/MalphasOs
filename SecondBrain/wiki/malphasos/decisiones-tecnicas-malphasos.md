---
name: decisiones-tecnicas-malphasos
description: Registro cronológico de decisiones técnicas tomadas al construir MalphasOS, con su justificación y en qué se apartan del proyecto original
tags: [malphasos, decisiones, adr, "describe:malphasos"]
updated: 2026-10-03
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
| Estado actual de la seguridad | ~~**Desactivada en `application.yaml`**~~ → **activa** | Se escribió cuando el realm `malphasos-realm` no existía y definir `issuer-uri` contra un realm inexistente impedía arrancar. **Corregido el 2026-09-08 al verificarlo**: `application.yaml` trae `enabled: ${APP_SECURITY_ENABLED:true}` desde que el realm se importa (2026-08-28). Solo el `application.yaml` de pruebas la apaga, para que cada prueba se centre en su capa |

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
| Roles granulares | ~~**Definidos pero sin usar**, como en el original~~ → **aplicados** | Se decidió heredar el defecto y quedó como decisión abierta. **Resuelto el 2026-09-08**: las 83 operaciones exigen la autoridad de su recurso y el realm reparte 19 roles entre tres grupos. Sí hubo que cambiar los `@PreAuthorize` de todos los controladores, que era el coste que retrasaba la decisión. Ver [[modelo-de-permisos]] |
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
| Autorización en `location` | ~~**`admin.full` en las diez operaciones**~~ → **`location.read` en cuatro y `location.write` en seis** | El hexágono original no tenía ninguna anotación. Se eligió la opción restrictiva apostando a que abrir es más fácil que cerrar, y **eso es exactamente lo que ocurrió el 2026-09-08**: países y ciudades son la tabla de referencia de la que cuelgan clientes, sedes y fabricantes, todo el mundo necesita leerlas y casi nadie tocarlas. Con `admin.full` ni leerlas era posible sin ser administrador |
| Manejo de excepciones | **Se repite por módulo, sin base común** | Resuelve la pendiente que este wiki arrastraba. Cada contexto acotado es dueño de su contrato de error y puede cambiarlo sin arrastrar a los demás; es duplicación de forma, no de comportamiento. Ver [[manejo-global-excepciones]] |
| Mappers de persistencia | **A mano, no con MapStruct** | MapStruct construye por setters o builder, y un agregado de Generación 2 no ofrece ninguno a propósito. Ver [[patron-mapper-mapstruct]] |
| Verbos de escritura | **Solo `PATCH`, sin `PUT`** | Eran caminos duplicados con su comando y su método de agregado propios, y uno etiquetaba su evento con un tipo distinto del que declaraba su clase |
| `DELETE` | **204, pero retira sin borrar** | Es el verbo que un cliente HTTP espera para retirar algo, y el cuerpo vacío no promete que la fila haya desaparecido. Queda dicho en la descripción de OpenAPI |

## Módulo de clientes, decisiones de construcción (2026-09-02)

| Decisión | Elegido | Por qué |
|---|---|---|
| Fronteras de agregados | **Cuatro agregados pequeños** que se referencian por identificador | Cargar un cliente no arrastra sus sedes, áreas y encargados, y dos personas editando sedes distintas no compiten por la misma fila. Coincide con lo que el esquema ya modelaba. El coste: ninguna transacción abarca cliente y sede a la vez |
| Representación legal | **Dentro del agregado `Client`**, como conjunto de identificadores de persona | "Quién representa a este cliente" es una pregunta sobre el cliente, y la lista es pequeña y acotada |
| Identidad del encargado | **Llave primaria de la persona**, sin `@OneToOne` ni `@MapsId` hacia la entidad de personas | La identidad compartida la garantizan la PK y la clave foránea del esquema. La anotación habría sido la única relación JPA entre módulos del proyecto, cuando las otras cuatro entidades referencian por identificador |
| Alta de encargado | **Dos caminos**: `register` crea la persona, `assign` parte de alguien que ya existe | El original siempre creaba una persona nueva, de modo que un ingeniero de la empresa no podía figurar además como encargado sin duplicarse |
| Orden dentro de `register` | **Validar el destino antes de crear la persona** | Al revés, un fallo en la validación dejaría una persona huérfana sin encargado que la justifique |
| Rutas REST | Lo que no existe sin otro **cuelga de su ruta** | Contactos y representantes van bajo `/clients/{id}`; una sede se abre bajo el cliente pero luego se direcciona sola, porque tiene identidad propia |
| Dirección de una sede | **Va entera o no va** | Sus tres partes forman un valor único; aceptar solo la calle dejaría una dirección incoherente |

## Módulo de equipos, decisiones de construcción (2026-09-02)

| Decisión | Elegido | Por qué |
|---|---|---|
| `verificable` de un tipo de equipo | **Derivado, no almacenado**: `isVerificable()` devuelve si consta la modalidad | Un tipo es verificable exactamente cuando se sabe cómo verificarlo. El original tenía un booleano y una columna de modalidad sin nada que los atara. Al derivarlo, el estado inconsistente deja de ser expresable. Ver [[migracion-equipment-hallazgos]] |
| La columna `b_verificable` de la tabla | **Se conserva, pero deja de ser fuente de verdad**: el mapper la deriva al guardar y la ignora al leer | La tabla se conserva para no divergir del sistema del que se migra. Un `CHECK` en `V5` ata las dos columnas desde el otro lado |
| Nombre de la tabla `equipo` | **Se conserva pese a que miente** | No guarda un equipo: es la asociación marca↔tipo. Renombrarla habría divergido del original sin ganar nada; se documenta en el esquema, en el agregado y en el controlador |
| Asociación marca↔tipo | **Inmutable, sin operación de cambio** | Cambiar cualquiera de sus dos referencias volvería mentira todos los modelos colgados de ella. El original ofrecía `updateEquipment` y `updateEquipmentPatch`. Si está mal se retira y se crea la correcta |
| Unicidad del par marca↔tipo | **Al esquema, no al servicio** | Comprobarla en el servicio solo abriría una ventana entre la consulta y la escritura. La regla que la base puede defender sin carreras, la defiende la base |
| Traslado de una unidad | **Evento propio**, separado del cambio de datos de compra | Los equipos se mueven dentro de una sede y eso cambia quién responde por ellos: es el hecho que más importa de una unidad |
| Alcance de la primera tanda | **Catálogo e inventario**; verificaciones técnicas y datos metrológicos, aparte | Cinco pasos ya grandes. La segunda tanda es lo único del backend que queda |
| Verbos de escritura, revisado | **Solo `PATCH`, también en rutas de sub-recurso** | Las tres rutas de sub-recurso llegaron como `PUT` y eran defendibles —reemplazan del todo y son idempotentes—, pero dos verbos con la misma semántica repartidos según quién escribiera cada controlador cuestan más que la precisión del matiz |
| Códigos de error de referencias externas | **Código propio por referencia**, no el genérico de datos inválidos | Un 404 con el código de "datos inválidos" no se distingue del 400 que usa el mismo código. `client` todavía no lo hace así: queda en [[deuda-tecnica-y-riesgos]] |

## Modelo de permisos (2026-09-08)

Detalle completo en [[modelo-de-permisos]]; aquí solo las decisiones y su coste.

| Decisión | Elegido | Por qué |
|---|---|---|
| Dónde se decide quién es administrador | **En un solo sitio**, `ApiAuthority.expand(...)`: quien trae `admin.full` recibe las 17 autoridades de recurso | La alternativa era que cada operación nombrara al administrador además de a su recurso, `hasAnyAuthority('admin.full','client.read')`. Eso deja el modelo repetido en las **83** operaciones, donde basta olvidar una para abrir un agujero que no rompe nada visible. Una prueba estructural impide que un controlador vuelva a nombrar al administrador |
| Cuántas capas aplican la regla | **Dos**: el converter del token y un bean `RoleHierarchy` | No es duplicación por descuido. El converter solo interviene cuando la autenticación nace de un JWT que pasa por esta cadena de filtros; cualquier otra llegaría a `@PreAuthorize` con las autoridades crudas y un administrador se vería rechazado. El coste —que las dos digan cosas distintas— se paga derivando ambas de la misma constante `RESOURCE_AUTHORITIES` y probándolas por separado, porque compartir la constante no garantiza que ambas la traduzcan bien |
| Qué hace `expand()` con un rol desconocido | **Lo conserva**: añade, nunca quita | Filtrar lo desconocido parece más limpio y es peor: un rol recién creado en el realm desaparecería en silencio dentro de un converter, sin log ni error, y se diagnosticaría mirando el sitio equivocado. La pertenencia se decide por igualdad exacta, no por prefijo, para que `admin.fullish` no conceda nada |
| Granularidad del vocabulario | **Por recurso, no por módulo**: `client`, `service-area` y `engineer` son tres vocabularios dentro del mismo módulo | El realm ya los distinguía y ninguna anotación los comprobaba. Permite que un ingeniero trabaje sobre áreas de servicio sin tocar la ficha del cliente. `client.delete` protege solo la baja del cliente entero: retirar un correo no es cerrar la cuenta |
| `equipment.assign` aparte de `equipment.write` | **Sí** | Asignar un equipo a un área o trasladarlo cambia **quién responde por él**, que es lo que un ingeniero de campo hace y un administrativo no. Corregir sus datos de compra se queda en `write` |
| Nombre del rol del encargado | **`engineer`, el del realm**, aunque el código diga `Manager` y la ERS «profesional responsable» | Tres nombres para lo mismo, y se conserva el del realm porque es el que la documentación recoge. El controlador lleva la correspondencia escrita para que nadie lo tome por un error. **No existe `engineer.write`**: al encargado se le asigna, no se le escribe, y una prueba lo fija para que quien la escriba no deje un endpoint sin nadie que pueda llamarlo |
| Autoridades de `work-order` en el catálogo | **Se incluyen**, sin módulo detrás | Ya estaban en el realm y asignadas al grupo `engineers`. Así el día que aparezcan sus endpoints el administrador no se queda fuera por olvido. Una prueba fija que hoy no protegen nada y fallará cuando lo hagan |
| Roles del realm que la expansión ya concede | **Se asignan igualmente al grupo `admins`** | La expansión de `admin.full` se los daría de todos modos, pero el realm debe poder leerse sin conocer el código: un `admins` sin `person` ni `location` afirmaría por escrito que un administrador no puede tocar personas ni ciudades |
| Filtrado por dueño | **Construido el 2026-10-04**, con el alcance como **argumento del caso de uso** | La alternativa era leer `SecurityContextHolder` en el adaptador de persistencia: filtraba igual, no cambiaba ninguna firma y dejaba el filtro **invisible desde fuera**, de modo que una consulta nueva lo habría olvidado sin que nada avisara. Con el alcance en la firma, el compilador obligó a trece llamantes a declarar el suyo. Esta fila decía «fuera de esta tanda» y que «exige decidir cómo se ata una cuenta de Keycloak a un cliente»: **esa decisión estaba tomada desde la migración de `person`** y no hizo falta migración ninguna. Ver [[filtrado-por-dueno]] |
| Cómo se sostiene el modelo | **Invariantes estructurales por reflexión**, no solo casos | Las 83 anotaciones se pusieron con un script por número de línea: un desfase de una línea deja un endpoint con la autoridad del vecino, y ninguna prueba de HTTP por casos lo ve. Los umbrales van como mínimos (`>= 83`, `>= 27`, `>= 56`) para servir de guarda de no vacuidad sin romperse al añadir un endpoint legítimo |

**Un javadoc que quedó falso y nadie lo notó.** Los dos controladores de `location` afirmaban por escrito que todas sus operaciones exigen `admin.full`. La afirmación era cierta cuando se escribió y dejó de serlo con este cambio. Se corrigió en el mismo commit; queda como recordatorio de que **un comentario que enuncia una regla de seguridad envejece igual que el código y nada lo comprueba**.

## Sincronizacion con el proveedor de identidad (2026-09-09)

Detalle completo en [[sincronizacion-con-proveedor-de-identidad]]; aquí las decisiones y su coste.

| Decisión | Elegido | Por qué |
|---|---|---|
| Qué hace la baja con la cuenta de Keycloak | **Deshabilitar, no borrar** | El motivo fuerte **no** es la simetría con el borrado lógico: es que el identificador de la persona **es** el id del usuario de Keycloak —`register` hace `UUID.fromString` sobre lo que devuelve `createUser`—. Borrar rompería esa correspondencia para siempre y recrear la cuenta daría otro UUID, sin tabla de equivalencias que lo repare |
| Orden de las dos escrituras | **Keycloak primero, base después** | No hay transacción que abarque a los dos, así que hay que elegir en qué dirección puede quedar la inconsistencia. Con este orden, un fallo al persistir deja a alguien **activo que no puede entrar**: molesto, visible y reparable repitiendo el `DELETE`. Con el orden contrario dejaría a alguien **de baja que sí puede entrar**, que es el defecto que se estaba corrigiendo. **Coste aceptado**: la transacción sigue abierta durante una llamada de red |
| Cómo se sostiene ese orden | **Con una prueba de orden** (`InOrder`), no con un javadoc | Mover una línea basta para invertirlo, y una prueba que solo comprobara "se llamó a Keycloak" pasaría igual |
| Un usuario que no existe en Keycloak | **No impide la baja** | Quien se dio de alta con `save` nunca tuvo cuenta; el objetivo —que nadie entre con esa identidad— ya está cumplido. Se registra en el log porque, si la persona sí debía tener cuenta, es la única señal de que los dos sistemas estaban desincronizados. **Cualquier otro fallo sí aborta** |
| Comprobar si ya estaba inactiva antes de llamar | **No se comprueba** | Repetir la baja es barato, es idempotente en Keycloak, y **repara** la desincronización si alguien reactivó la cuenta a mano desde la consola |
| Qué propaga `update` | **Solo nombre y apellido** | El usuario no viaja en la petición; la contraseña es otra operación con otras garantías; y el **correo no se puede sincronizar**: una persona tiene varios sin ninguno marcado como principal, así que no hay forma de saber cuál es el de la cuenta. Es un límite del modelo de datos, no una omisión — por eso esa deuda queda **parcialmente abierta** |
| Cómo se sostiene ese conjunto | **Prueba por reflexión sobre los componentes del `record`** | Si alguien añade usuario, correo o contraseña a `PersonIdentityProfile`, la prueba se rompe a propósito |
| `enableUser` | **No se añade** | No existe hoy ningún camino de reactivación que lo llamaría, y un puerto con operaciones que nadie invoca es código muerto |
| Mover al usuario de grupo al cambiar `tipoPersona` | **Hecho el 2026-10-04**, con `syncGroup` | Esta fila decía «fuera de esta tanda» y que pertenecía «a la línea del [[modelo-de-permisos]]: cambiar de rol tiene consecuencias que merecen su propio caso de uso». Lo que la desbloqueó fue el [[filtrado-por-dueno]]: el alcance de lectura se decide por el tipo, de modo que la fila podía contradecir al grupo y **el sistema creía las dos a la vez**. `PersonType` tiene cinco valores contra tres grupos, así que un encargado queda en ninguno —no accede— y un `SUPER_ADMIN` en el de administradores, que es lo más que un grupo da |

**Un límite que se escribe en vez de disimularse.** Verificado contra un Keycloak 26.6.1 real: un token emitido **antes** de la baja sigue abriendo el API hasta que caduca —300 s en este realm—, porque el resource server lo valida sin preguntarle al emisor. La brecha queda cerrada **para las autenticaciones nuevas, no para las ya emitidas**; cerrarla del todo exige introspección por petición.

**Y un cambio de conducta que nadie había señalado.** Añadir `case 404` a `translateClientFailure` modificó `deleteUser`, que ya existía: de sus **dos** caminos para un 404, el que pasa por la traducción devuelve ahora `KeycloakUserNotFoundException` en vez de `KeycloakConnectionException`. Es inocuo —su único llamante captura `RuntimeException` y solo cambia el log—, pero deja una regla: **un `switch` de traducción compartido tiene tantos llamantes como métodos lo usen, y añadirle un caso los modifica a todos**.

## Traslado de equipos dentro del mismo cliente (2026-09-09)

Detalle completo en [[regla-traslado-mismo-cliente]]. Aquí solo las decisiones y su coste. (Esta línea remitía a «por qué la regla figura como construida y no como verificada»: **verificada el 2026-09-10**, corregido el 2026-09-12.)

| Decisión | Elegido | Por qué |
|---|---|---|
| Quién resuelve de qué cliente es un área | **`client`, en una llamada**: `ServiceAreaServicePort.findOwningClient(UUID)` | La alternativa era que `equipment` caminara área → sede → cliente dos veces, atándose a la estructura interna de otro contexto. Lo que cruza la frontera sigue siendo un identificador |
| Llamada o evento | **Llamada síncrona** | El traslado necesita la respuesta antes de decidir y **un evento no contesta preguntas**. Con eventos habría que permitir el traslado y deshacerlo después |
| Puerto dedicado o ampliar el existente | **Ampliar `ServiceAreaServicePort`** | `equipment` ya dependía de ese puerto y del agregado `ServiceArea`: un segundo contrato al estilo de `PersonCommunicationPort` no estrechaba nada y solo añadía un bean. **Segunda razón, y es sobre el proceso**: inyectar una dependencia nueva cambia el constructor del servicio y rompe la compilación de `EquipmentChainServiceTest`, archivo que el `desarrollador` tiene prohibido tocar. **El reparto de dominios entre agentes empujó una decisión de diseño** |
| Dónde vive la regla | **En el servicio** | Un `CHECK` tendría que cruzar unidad, área y sede para comparar dos clientes que ninguna tabla guarda junta |
| Qué se comprueba en el alta | **Nada** | No hay cliente previo que violar: el área elegida define de qué cliente pasa a ser la unidad. Buscar simetría habría sido inventar una regla |
| Estado del rechazo | **409 con `ERR_EQUIPMENT_010`**, no 400 | Los datos son válidos; choca el estado. Precedente verificado en `PersonControllerAdvice`, que responde 409 a `KeycloakUserAlreadyExistsException` |
| Tipo de la excepción | **`RuntimeException` propia**, no `IllegalArgumentException` | El advice del módulo traduce esa familia entera a 400, así que heredar de ella habría dado 400 en silencio |

**Y una decisión de proceso, tomada por el usuario y no por el código**: esta tanda **no pasó por el `tester`**. Es la razón por la que la regla se registra como construida y sin verificar, con la batería en verde diciendo únicamente que nada se rompió — **496 ejecuciones y cero fallos, idénticas a `main`, porque no se añadió ninguna prueba**.

> **Corrección del 2026-09-12.** El párrafo de arriba fue cierto durante un día: el `tester` pasó por la rama el 2026-09-10 (`43de295`) y la regla quedó verificada con 13 pruebas, dejando la rama en **509** ejecuciones. Se conserva porque la decisión de proceso se tomó de verdad y **su coste quedó medido**: la regla estuvo un día en el repositorio sin que nada la ejerciera, y el wiki tuvo que publicarlo como pendiente. Lo que **no** cambió al repararla es igual de informativo: **no había ningún defecto detrás, la guarda funcionaba**.

## Módulo de órdenes de trabajo, esquema (2026-09-12)

Primera de cuatro tandas. Detalle completo en [[dominio-orden-trabajo]]; aquí solo las decisiones y su coste.

| Decisión | Elegido | Por qué |
|---|---|---|
| Cliente y sede en la orden, aunque sean deducibles por sus equipos | **Guardarlos** | Un equipo puede trasladarse a otra sede del mismo cliente ([[regla-traslado-mismo-cliente]]) y sin esas columnas una orden **ya ejecutada cambiaría de sede retroactivamente**. Coste aceptado: dos columnas que el servicio tiene que mantener coherentes con los equipos |
| Cómo se garantiza que la sede es de ese cliente | **Clave foránea compuesta**, con un `UNIQUE (k_id_sede, k_id_cliente)` sobre `sede` añadido en `V6` | Deja de ser convención y pasa a comprobarlo el motor. El `UNIQUE` no añade regla alguna sobre `sede` —la PK ya la hace única— solo expone el par como destino referenciable. **Es la única regla cruzada de este módulo que el esquema puede sostener sin desnormalizar más**, y **la propuso el `desarrollador`**: no estaba en el encargo |
| Dónde se añade ese `UNIQUE` | **En `V6`, no editando `V4`** | Una migración ya aplicada no se toca: Flyway la tiene sellada por *checksum* y editarla rompe cualquier base existente |
| El área de la tabla puente | **Congelada**: la que tenía el equipo al seleccionarlo | Un traslado posterior no debe reescribir dónde se prestó el servicio |
| Clave foránea compuesta del puente contra `equipo_cliente` | **No ponerla**, a propósito | Parece la restricción correcta, pero PostgreSQL la comprobaría también al **actualizar** la fila referenciada, y **una orden vieja bloquearía un traslado legítimo**. Verificado contra Postgres real y **fijado con una prueba que se pondrá roja si alguien la añade**. Nota propia: [[congelar-una-referencia-historica]] |
| Las áreas del paso intermedio del formulario (RF-04) | **No se persisten** | Quedan implícitas en los equipos elegidos. Dos listas que deben concordar se desincronizan, y no hay ninguna pregunta que la de áreas conteste y la de equipos no |
| Tipo de `f_fecha_mantenimiento` | **`date`**, no `timestamp` | El original la declaraba `timestamp` y la comentaba como la fecha de **creación** de la orden: dos cosas distintas. El formulario de la ERS pide un **día de servicio** |
| Ingeniero asignado | **Anulable** | Una orden se crea antes de asignarse. Es lo que justifica que `work-order.assign` sea una autoridad aparte de `work-order.write` en [[modelo-de-permisos]] |
| Idioma de las tres enumeraciones | **Español** | Convención del proyecto para el vocabulario propio del dominio (`NIT_juridico`, `patron_constante`). Evita además repetir el `BIANNUAL` del original, ambiguo en inglés entre «dos veces al año» y «cada dos años» |
| De dónde salen los valores | **Del `CHECK` del esquema heredado** (periodicidades y estados) y **del vocabulario de la ERS** (tipos de servicio) | No se inventaron. Ver abajo, porque el **cómo** vale más que el qué |
| Las siete reglas que el esquema no puede expresar | **Al servicio, en la tanda 3** | Cada una cruza tablas que no guardan juntos los datos a comparar, o depende del estado y no de la existencia. Están listadas en [[dominio-orden-trabajo]] y anotadas en [[deuda-tecnica-y-riesgos]] para que la migración en verde no se lea como un esquema que las defiende |

**Y una decisión de método, que es lo más reutilizable de la tanda.** Los valores de las tres enumeraciones se le **preguntaron al usuario tres veces** antes de que apareciera que dos de las tres estaban escritas en un `CHECK` del esquema heredado que nadie había abierto. El original no servía de plantilla para casi nada de este módulo —no tiene cliente, ni sede, ni tipo de servicio, ni vínculo con los equipos, y ninguna restricción lo referencia— y **precisamente por eso nadie fue a mirarlo**. La regla: antes de pedir una decisión de vocabulario del dominio, agotar el original; un esquema puede ser inútil como diseño y seguir siendo la única fuente escrita del diccionario.

## Módulo de órdenes de trabajo, dominio, aplicación y REST (2026-09-12 / 13)

Las tres tandas restantes. Detalle en [[dominio-orden-trabajo]].

| Decisión | Elegido | Por qué |
|---|---|---|
| Los equipos de la orden | **Parte del agregado**, no un agregado aparte | Un equipo seleccionado no tiene sentido fuera de su orden y su ciclo de vida es el de ella. Por eso viven dentro y no se referencian por identificador como el cliente o la sede |
| Identidad de `SelectedEquipment` | **El equipo solo, no el par equipo+área** | Añadir un equipo que ya está no hace nada **ni siquiera con otra área**: dentro de una orden, el mismo equipo dos veces con dos áreas no es dos cosas, es un intento de reescribir un área congelada. Corregirla exige retirarlo y volver a añadirlo, que es exactamente lo que significa |
| Dónde viven las transiciones de estado | **En `ExecutionState`** (`siguiente`, `avanzaA`, `esFinal`), no en el `CHECK` | Una restricción de columna mira la fila que se escribe, no la que había antes: puede fijar **qué valores existen** y no **cómo se pasa de uno a otro** |
| Qué exige `start()` | **Ingeniero asignado y al menos un equipo** | Sin lo primero no hay quien lo haga, sin lo segundo no hay sobre qué. Se comprueba al empezar y no al crear, porque el formulario de la ERS elige los equipos en un paso posterior |
| Modificar el alcance con la orden **en ejecución** | **Permitido** | En campo aparece un equipo no previsto, o uno de los elegidos resulta inaccesible. Lo que no se admite es cambiar una orden **ejecutada**: su registro dejaría de describir lo que se hizo |
| Cancelar una orden ya `EJECUTADA` | **Permitido** | Cancelar no es lo contrario de ejecutar: retirar del listado un registro histórico no reescribe lo que ocurrió |
| Cómo se garantiza que el área congelada es la real | **Quitando el campo del comando y de la petición REST** | `AddEquipmentToWorkOrderCommand` no tiene dónde poner el área: la averigua el servicio. **Una regla que no se puede expresar no puede violarse**, y eso gana a comprobarla. Es la decisión más importante del módulo |
| Persistencia del alcance | **Conciliar contra la fila existente**, no reconstruirla | El adaptador lee la entidad antes de guardar. El equipo retirado **queda inactivo, no se borra** —la llave compuesta impide reinsertarlo y el historial dejaría de ser cierto—; el que vuelve reactiva su fila con el área **nueva** |
| Estado que devuelve un choque de momento | **409**, separado del 400 de datos inválidos | Los datos son válidos y no falta ninguno: lo que choca es el momento. Compartir código impediría distinguir «lo has escrito mal» de «ahora no se puede». Es el primer módulo del proyecto con un estado que puede chocar |
| Filtros del listado | **Mutuamente excluyentes**, 400 si llegan dos | Combinarlos exigiría un puerto por combinación. La respuesta a «¿y si quiero dos?» es una consulta nueva y explícita |
| Operación de cambio general sobre la orden | **No existe** | Una orden no se edita: se le añaden o quitan equipos, se le asigna un ingeniero y avanza de estado. Un `PATCH` sobre la orden entera confundiría cuatro hechos distintos en uno |

**El coste que quedó sin pagar, y se pagó el mismo día.** De las siete reglas que la tanda 1 dejó al servicio, **seis se construyeron y una no**: el área del equipo no se comprobaba contra la sede de la orden. Apareció el 2026-09-13 **contrastando [[dominio-orden-trabajo]] contra el servicio**, no leyendo el servicio — la lista de siete se había escrito sin columna de estado, y una lista así es una lista que nadie contrasta. **Cerrada con `0cf56c5`**, y con una decisión que merece quedar:

| Decisión | Elegido | Por qué |
|---|---|---|
| Qué hacer cuando una guarda nueva **subsume** a una vieja | **Ordenarlas, no borrar la subsumida** | Un equipo de otro cliente está por fuerza en otra sede, así que la comprobación de sede taparía a la de cliente. Borrar la vieja pierde el mensaje más informativo; dejarla detrás la mata en silencio. Se deja el dueño **delante** y una prueba fija ese orden con `withMessageNotContaining`, de modo que **las dos siguen siendo alcanzables** |

## Módulo de reportes de servicio (2026-09-27)

Cuatro tandas en un día, del esquema al REST. Las decisiones que condicionan lo que venga detrás:

- **Un reporte por equipo de la orden, no uno por orden.** Es lo que dice RF-09 literalmente y lo que ocurre en campo. La consecuencia práctica: la llave del reporte es el par (orden, equipo), y la pantalla que falta es una por equipo, no una por visita.
- **La llave foránea es compuesta contra `orden_trabajo_equipo`.** Con dos foráneas sueltas cabría un reporte de un equipo que la orden nunca incluyó. Segunda vez que este proyecto usa la técnica —la primera fue `UQ_sede_identidad_con_cliente` en `V6`— y aquí salió gratis porque el par ya era la llave primaria del puente.
- **El reporte no copia nada de la orden**, y eso *es* RF-11: consultar por el identificador es autocompletar. El original sí copiaba el cliente, con el vínculo hacia la orden roto por tipos.
- **El resultado de verificar vive con el reporte, no con el equipo.** `V8` configuró *dónde* y *cuántas veces* se mide; `V9` guarda *lo que salió*. El criterio: se mide durante el servicio y se imprime en el reporte de ese servicio. Esto reparte la «segunda tanda de `equipment`» en dos mitades y deja solo una pendiente — el vencimiento de calibración.
- **`ReportDataProviderPort` del original no se usó.** Aquel patrón resuelve un agregador de consulta; esto es una entidad con ciclo de vida. Queda disponible para el PDF de RF-17, que sí agregará. Ver [[dominio-reportes]].
- **El vocabulario del resultado se inventó, y está marcado como tal.** La ERS no enumera valores para «resultado». Va como catálogo cerrado porque de él cuelgan la hoja de vida y las alertas; queda en [[deuda-tecnica-y-riesgos]] esperando la palabra del usuario.
- **Dos autoridades y no tres.** Cerrar no tiene la suya: quien llena el reporte es quien lo firma. Y entraron en `ApiAuthority` **el mismo día que sus rutas**, al contrario que las de `work-order`: las dos formas funcionan, pero adelantarlas exige una prueba centinela que avise, y no adelantarlas no exige nada.
- **Sin `findAll` y sin `reopen`.** Una lista de todos los reportes del sistema no responde a ninguna pregunta del dominio, y un reporte cerrado es lo que se entregó al cliente. Las dos ausencias tienen prueba, para que añadirlas sea una decisión y no un descuido.
- **El orden de escritura de una ORM no es el del código.** Corregir una lectura choca con el índice único parcial porque Hibernate vacía los `INSERT` antes que los `UPDATE`. Se impone el orden en el adaptador con un `saveAndFlush` intermedio, porque un índice **parcial** no se puede declarar diferido en PostgreSQL. Es el segundo caso de «la persistencia tiene reglas propias que el dominio no ve», tras el `@Transactional` de los adaptadores con colecciones perezosas.

Ver [[dominio-reporte-servicio]] para el detalle.

## El tema de Keycloak (2026-09-28 y 2026-10-02)

- **Dos mecanismos, no uno**: Keycloakify para el login —es una interfaz de verdad— y un **tema clásico** para la consola de administración, donde cambiar logo y colores son cuatro archivos y la vía de Keycloakify son **692**.
- **El proyecto del tema es React**, y la razón no es de gusto: *«only React supports custom Admin UIs»*. **La primera recomendación de esta sesión fue Angular y estaba mal argumentada** —se invocó el error del `auth/` React del original, que no aplica porque existe librería de Angular—; lo que decide es qué cubre cada opción. Queda escrito en [[tema-de-keycloak]] como patrón de error: el argumento por analogía con una herida vieja sonaba bien y no venía al caso.
- **Un nombre de tema para los dos**, porque Keycloak resuelve por (nombre, tipo).
- **El JAR se construye dentro de Docker**, no en la máquina de quien desarrolla: Keycloakify necesita Maven, y montar un JAR ya hecho como volumen falla creando un directorio y arrancando sin tema **en silencio**.

## El módulo de personas en el frontend (2026-10-02)

- **Cuatro altas y no una pantalla con un selector de tipo.** El API tiene cuatro puertas y las dos reglas que las sostienen son del backend: en las tres con cuenta **el tipo lo dice la ruta**, y la cuarta **solo admite `MANAGER`** —`PersonService.save` lo impone para que nadie escriba una fila que dice ser administrador sin serlo—. Un selector sería el campo que invita a lo que el servidor rechaza.
- **La ficha se edita en sí misma, no en una pantalla aparte** —rompiendo con el precedente de cliente y sede— porque **el permiso depende de la fila**: una ruta aparte tendría que declarar una autoridad fija antes de saber a quién carga. Es la misma razón por la que el backend lo resuelve en un bean y no en la anotación.
- **El tipo de persona se ve y no se cambia**, y **el motivo caducó el 2026-10-04**. Decía: «cambiarlo no mueve al usuario de grupo en Keycloak, así que ofrecerlo sería ofrecer una acción cuya consecuencia el servidor no completa». El servidor **ya la completa**. La pantalla sigue sin ofrecerlo porque nadie la ha tocado, no porque el argumento valga: **queda como decisión de producto pendiente**, y con un filo nuevo —promover exige `super.person.write`, que ningún grupo del realm concede, de modo que un administrador vería un campo que no puede usar para la mitad de los valores—.
- **El menú oculta por autoridad**, y la autoridad la declara la **entrada de navegación**, de donde salen a la vez el menú y el guard de la ruta. Lo hizo necesario «Personas»: el primer destino que un grupo legítimo del realm no puede usar.

## Un tipo de equipo se verifica en varias magnitudes (2026-10-03)

Lo decidió el usuario sobre cuatro preguntas, y en las cuatro eligió la opción más expresiva. El
contexto y el modelo están en [[dominio-equipo-mantenimiento]]; aquí van las decisiones y su coste.

- **Magnitud y unidad salen de un catálogo cerrado**, no de texto libre. El motivo que pesó no es la
  coherencia: el índice de unicidad compara la unidad **como texto**, de modo que `°C` escrito con el
  signo de grado (U+00B0) y con el indicador ordinal masculino (U+00BA) eran dos unidades distintas para
  la base y **la misma a la vista**. Un catálogo lo hace imposible de teclear. Coste: dos tablas
  sembradas que nadie administra.
- **La modalidad y la cantidad de lecturas bajan a cada verificación.** La alternativa —una por
  aparato— era menos trabajo hoy y obligaba a remodelar el día que apareciera un equipo mixto; y esta
  era ya la segunda pasada sobre esta pieza.
- **`b_verificable` se va y no se sustituye.** Era exactamente `n_tipo_verificacion IS NOT NULL`,
  atado por un `CHECK`: redundante por construcción. «Se verifica» se cuenta.
- **Los contadores de la pantalla no se guardan.** Ni «cuántos tipos de verificación» ni «cuántos
  puntos»: son controles que despliegan campos, y lo que se envía es la lista. Un número que tiene que
  coincidir con el número de elementos se desincroniza el día que alguien añade uno por otro camino, y
  la cuenta siempre se puede derivar contando. **Es la misma decisión que `verificable`**, tomada dos
  veces el mismo día por dos caminos distintos.

### Magnitud y unidad **no** son agregados, al contrario que `Country`

Son datos de referencia inmutables: entran sembrados, nadie los edita y no hay operación que los cree.
`Country` tiene sus tres eventos y su servicio de escritura porque **el sistema original ya administraba
países**; aquí construir `create/rename/deactivate` sería maquinaria sin un solo llamante, y este
proyecto tiene escrito que no se reserva nada para lo que no existe — un patrón reservado es
indistinguible de uno roto.

Convertirlos en agregados el día que haya que administrarlos es un cambio acotado. Lo que no se puede
deshacer es haber construido eventos que nadie emite.

### La verificación guarda las piezas enteras, no sus identificadores

**Excepción razonada a la convención de referenciar por identificador**, y se corrigió a mitad de
camino: la primera versión guardaba `magnitudId` y `unidadId`. Esa convención guarda fronteras entre
**agregados**, y por la decisión de arriba estos no lo son. Con solo identificadores, ni el reporte ni
la pantalla pueden decir «Temperatura en °C» sin volver a consultar el catálogo — y **el servicio ya lo
consulta para validarlos**, así que embeberlas no cuesta una consulta más: la guarda que valida es la
que trae la pieza.

Consecuencia visible: `TypeVerificationResponse` **sí trae nombres**, rompiendo con el resto del módulo
—ninguna otra respuesta los trae, y está anotado como fricción—. La cabecera de una tabla de
verificación dice «Temperatura (°C)», y obligar a la pantalla a cruzar dos catálogos para pintar un
encabezado es exactamente el problema que esa fricción describe.

### El catálogo metrológico se sirve con `equipment.read`, sin autoridades nuevas

Sus dos listas solo sirven para declarar cómo se verifica un tipo de equipo, de modo que quien puede
leer el catálogo de equipos puede leer esto. Inventar `magnitude.read` obligaría a tocar el realm, los
tres grupos y la expansión del administrador **para separar algo que nadie va a separar**. Las
autoridades siguen en 22. Ver [[modelo-de-permisos]].

### La migración se niega a correr antes que adivinar

`V10` reestructura `punto_verificacion` y no hay forma automática de repartir puntos ya registrados
entre magnitudes que nadie declaró. Las tres opciones eran adivinar, borrar en silencio o **fallar con
un mensaje**. Falla, con los conteos dentro, y tumba el arranque — que es lo que este proyecto ya sabía
que hace un `CHECK` nuevo sobre filas viejas, usado aquí a propósito.

## Pendientes de decidir

- Organización del frontend por feature vs por tipo técnico: ver [[arquitectura-frontend]]. **Resuelto de hecho el 2026-09-13**: por módulo de negocio con los nombres del backend, ver [[arquitectura-frontend-malphasos]].
- **Las tres palabras de `t_resultado`** en un reporte de servicio: son una propuesta, no vocabulario de la ERS.
- **Si las respuestas de los módulos deben traer nombres además de identificadores.** Van ya cuatro módulos que devuelven solo identificadores —encargados, equipos, órdenes y reportes— y el frontend resuelve cada nombre con una consulta aparte. O se acepta como convención y se escribe, o se rompe una vez y se hace en todos. **Y el 2026-10-03 se rompió una vez**: `TypeVerificationResponse` trae el nombre de la magnitud y el símbolo de la unidad, por la razón de arriba. Es un precedente, no una resolución: sigue sin decidirse si los otros cuatro lo hacen.

## Notas relacionadas

[[modelo-de-permisos]] · [[tema-de-keycloak]] · [[sincronizacion-con-proveedor-de-identidad]] · [[regla-traslado-mismo-cliente]] · [[dominio-orden-trabajo]] · [[dominio-reporte-servicio]] · [[congelar-una-referencia-historica]] · [[stack-spring-boot-4-particularidades]] · [[migracion-equipment-hallazgos]] · [[migracion-client-hallazgos]] · [[migracion-location-hallazgos]] · [[traduccion-de-fallos-de-adaptadores]] · [[relacion-manager-persona]] · [[dominio-cliente]] · [[checklist-reutilizacion]] · [[alcance-malphasos]] · [[sintesis-malphasos]] · [[docker-compose]]

## La hoja de vida es un compilado, y solo su historial tiene tabla (2026-10-04)

- **La hoja de vida no es una entidad.** Aclaración del usuario: se arma con los datos del cliente, del tipo, la marca, el modelo, la serie y el fabricante, y existe desde que el equipo se registra, con su historial en cero. Es un modelo de lectura en `application/model`, sin tabla. Ver [[hoja-de-vida]].
- **Es de solo lectura**, y eso responde a RF-24: cada dato se corrige donde vive. Decisión del usuario.
- **Su historial sí tiene tabla**, porque **la intervención sobrevive al reporte retirado**: el mantenimiento ocurrió. Decisión del usuario, y la razón de que no sea una consulta sobre los reportes.
- **Las tres columnas del historial son una copia congelada**, sin foránea compuesta contra el reporte. Ver [[congelar-una-referencia-historica]].
- **El oyente vive en `report` y no donde la ERS lo reservó**, porque en `equipment` habría creado un ciclo. Entra por `InterventionRecordingPort`.
- **Síncrono y en la misma transacción**: cerrar e historiar son atómicos. `AFTER_COMMIT` dejaba un reporte cerrado con su historial vacío sin que nada lo notara.
- **`Intervention` es un `record`, no un agregado**: no cambia, describe algo que pasó.
- **Once consultas por documento antes que una unión de nueve tablas**, seis de ellas ajenas.
- **`V13` rellena el pasado** en lugar de corregir `V12`, que ya estaba aplicada en desarrollo.

