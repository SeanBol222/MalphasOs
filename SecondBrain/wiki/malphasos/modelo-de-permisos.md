---
name: modelo-de-permisos
description: Las 22 autoridades del API, la expansion en dos escalones que separa al super usuario del administrador, la escalera de quien puede crear a quien, y la unica excepcion a la autoridad literal
tags: [malphasos, seguridad, keycloak, autorizacion, "reusable:media", "describe:malphasos"]
estado: estable
updated: 2026-09-27
---

# Modelo de permisos de MalphasOS

Escrito el 2026-09-08, al cerrar la tanda que lo construyó (nueve commits, `e68f83e`…`2de115f`, rama `feat/permission-model`). Antes de esa tanda **el modelo de permisos no existía**: las 83 operaciones REST exigían `admin.full` y el realm solo se lo daba al grupo `admins`. Ver [[seguridad-keycloak-backend]] para las piezas de seguridad y [[keycloak-configuracion]] para el realm.

## El defecto que corrige

Las 83 operaciones publicadas declaraban `@PreAuthorize("hasAuthority('admin.full')")` —83 mappings, 83 anotaciones idénticas—. Los grupos `engineers` y `clients` recibían cuatro roles de lectura (`client.read`, `equipment.read`, `service-area.read`, `work-order.read`) que **ninguna operación comprobaba**: autenticaban correctamente y recibían 403 en toda llamada. Y los dos grupos tenían **exactamente los mismos cuatro roles**, de modo que un técnico de campo y un cliente externo eran indistinguibles para el sistema.

Es el mismo defecto que [[keycloak-configuracion]] ya registraba del original —«los 15 roles granulares no se usan»— heredado intacto al migrar.

**Por qué nadie lo vio durante meses**: todas las pruebas de seguridad construían el token con `admin.full`, el único rol que sí funcionaba. Una batería que solo recorre el camino del administrador no dice absolutamente nada sobre los demás perfiles. El hallazgo salió de contrastar la ERS contra el código (2026-09-02), no de leer los controladores.

## El vocabulario: 22 autoridades

`bootstrap/config/security/ApiAuthority.java` reúne el vocabulario completo. Los nombres **no los inventa la clase: reflejan los roles que el realm define** sobre el client `malphasos-api`.

Dos autoridades de mando, que no protegen ningún endpoint:

| Autoridad | Qué es |
|---|---|
| `admin.full` | Concede las **19** de recurso al expandirse. Es el permiso del grupo `admins`. (Decía 17: cierto hasta el **2026-09-27**, cuando entraron las dos de `report`) |
| `super.admin.full` | Implica `admin.full` **y algo más desde el 2026-09-13**. Hasta esa fecha concedían lo mismo y esta tabla decía que se conservaban separados por si «un realm futuro» daba al segundo capacidades propias: ese futuro llegó. **Ningún grupo lo recibe**: un super usuario se crea a mano en Keycloak |

Y una que **sí protege endpoints pero que `admin.full` no concede** — el escalón de arriba, desde el 2026-09-13:

| Autoridad | Operaciones | Notas |
|---|---|---|
| `super.person.write` | 2 | Alta de ingenieros y administradores. Las de editar y retirar no la nombran: dependen del tipo de la persona y delegan en un bean. Ver «La escalera de usuarios» |

Las 17 de recurso, con las operaciones que protegen a fecha de hoy (contado sobre los controladores, no sobre el catálogo):

| Autoridad | Operaciones | Notas |
|---|---|---|
| `person.read` | 2 | |
| `person.write` | 2 | **Era 12 hasta el 2026-09-13.** Ahora cubre el alta de representantes de cliente y la de encargados; las altas de la gente de la casa subieron de escalón, y editar y retirar —incluidos correos y teléfonos— delegan en un bean porque dependen del tipo de la persona |
| `location.read` | 4 | |
| `location.write` | 6 | |
| `client.read` | 4 | |
| `client.write` | 11 | Incluye sedes y sub-recursos del cliente: una sede no existe sin su cliente |
| `client.delete` | 1 | Solo la baja del cliente entero. Retirar un correo no es cerrar la cuenta |
| `service-area.read` | 2 | Vocabulario propio, para que un ingeniero trabaje sobre áreas sin tocar la ficha del cliente |
| `service-area.write` | 3 | |
| `engineer.read` | 2 | El realm llama `engineer` a lo que el código llama `Manager` y la ERS «profesional responsable». Se conserva el vocabulario del realm porque es el que la documentación recoge |
| `engineer.assign` | 4 | **No existe `engineer.write`**: al encargado se le asigna, no se le escribe. Una prueba lo fija, porque quien la escriba dejaría el endpoint sin nadie que pudiera llamarlo |
| `equipment.read` | 13 | |
| `equipment.write` | 17 | |
| `equipment.assign` | 2 | Vincular una unidad a un área y trasladarla a otra. Asignar cambia quién responde por un equipo, y es lo que un ingeniero de campo hace y un administrativo no |
| `work-order.read` | 2 | |
| `work-order.write` | 6 | Programar, cambiar el alcance, avanzar de estado y cancelar |
| `work-order.assign` | 1 | **Solo poner la orden en manos de un ingeniero.** Es lo que permite que un coordinador reparta trabajo sin poder alterar lo que se va a hacer |
| `report.read` | 2 | Los reportes de una orden, o el historial de un equipo. **No hay «todos los reportes»**: esa operación no existe |
| `report.write` | 4 | Abrir, llenar, registrar la verificación, cerrar y retirar. **No hay una tercera para cerrar**, al contrario que en las órdenes: quien llena el reporte es quien lo firma en campo, y separarlas describiría un reparto que no existe. La firma digital (RF-21) traerá la suya, y hay prueba que avisará |

**Total: 98 operaciones** — 83 verificadas el 2026-09-08 sobre `feat/permission-model`, más las **9** de órdenes de trabajo (2026-09-13, `ceadba1`) y las **6** de reportes de servicio (2026-09-27, `ac0233b9`). Los 83 y su reparto 27/56 quedan como la cifra de aquella tanda; los umbrales de las pruebas van como mínimos, así que no se rompen al crecer.

> **Actualizado el 2026-09-27: ya no queda ninguna autoridad esperando su módulo.** Las dos de `report` entraron en el catálogo **el mismo día que sus rutas**, y no antes, porque `ningunaAutoridadSobra` habría fallado — una autoridad que no protege nada es un permiso que el realm concede en vano. Es lo contrario de lo que se hizo con `work-order`, y las dos formas funcionaron: aquélla necesitaba una centinela que avisara, ésta no necesita nada.

> **Actualizado el 2026-09-13.** Aquí decía que las tres de `work-order` estaban en el catálogo **sin módulo detrás**, y que una prueba fijaba que no protegían nada y fallaría el día que lo hicieran — «que es cuando toca revisar esta nota». **Eso ocurrió**: el módulo llegó, la centinela `lasAutoridadesDeWorkOrderSiguenSinModulo` se puso roja, se retiró en el mismo commit, y ésta es la revisión que pedía. Haber incluido las tres por adelantado hizo lo que se esperaba: el administrador no se quedó fuera por olvido, porque `ApiAuthority.expand(...)` ya las conocía.

**Por qué `assign` merece autoridad propia.** Sin ella, `work-order.write` cubriría también asignar y el permiso no significaría nada distinto de «puede tocar la orden». Con ella, repartir trabajo y decidir qué trabajo es son dos capacidades separables. Una prueba lo fija: **`assign` es la única operación que la exige**, de modo que si mañana otra la reclama la separación no desaparece en silencio. Es el mismo razonamiento que ya justificaba `equipment.assign` y `engineer.assign`, y el tercer caso del proyecto.

## La decisión central: expandir en un sitio, no repetir en 83

Cada operación declara **únicamente la autoridad de su recurso**: `hasAuthority('client.read')`.

La alternativa era que cada una nombrara además al administrador, `hasAnyAuthority('admin.full','client.read')`. Se descartó porque deja el modelo de permisos **repetido 83 veces**, y basta olvidarse de una para abrir un agujero que no rompe nada visible. Quién es administrador se decide en un solo sitio: `ApiAuthority.expand(...)`.

Una prueba estructural impide deshacer la decisión sin enterarse: **ningún controlador puede nombrar al administrador**, y cada operación debe exigir exactamente una autoridad nombrada literalmente — **con una única excepción acotada**, la de la escalera de usuarios, que se explica más abajo.

## Por qué la regla se aplica en dos capas

Existen dos implementaciones de la misma jerarquía, y no es duplicación por descuido:

- `KeycloakRoleConverter` llama a `ApiAuthority.expand(...)` al convertir el token en autoridades.
- `SecurityConfig` declara un bean `RoleHierarchy` con la misma jerarquía (`super.admin.full > admin.full`, y `admin.full > cada autoridad de recurso`).

El converter **solo interviene cuando la autenticación nace de un JWT que pasa por esta cadena de filtros**. Cualquier autenticación construida por otra vía llegaría a `@PreAuthorize` con las autoridades crudas, y un administrador se vería rechazado. La jerarquía cierra ese hueco.

El coste de tener dos caminos —que digan cosas distintas— se paga de dos formas: **ambas se derivan de la misma constante `RESOURCE_AUTHORITIES`**, y cada una tiene su prueba propia, porque derivar de la misma constante no garantiza que ambas la traduzcan bien.

Detalle que ahorra un rato de desconcierto: los nombres de la jerarquía van **sin el prefijo `ROLE_`**. Esa convención de Spring Security no se usa aquí; los permisos llegan de Keycloak con su propio nombre y se comprueban con `hasAuthority`, nunca con `hasRole`.

## `expand()` añade, nunca quita

Un rol que no pertenece al vocabulario **se conserva tal cual**. La alternativa —filtrar lo desconocido— parece más limpia y es peor: alguien crea un rol en el realm, lo asigna, y el permiso desaparece en silencio dentro de un converter, sin log ni error. Un fallo así se diagnostica mirando el sitio equivocado durante horas.

La pertenencia se decide por **igualdad exacta** sobre el conjunto, no por prefijo: `admin.fullish` y `super.admin` no conceden nada. Hay una prueba para eso, porque es justo lo que un refactor a `startsWith` rompe sin avisar.

## Qué recibe cada grupo del realm

Verificado el 2026-09-08 sobre `docker/keycloak/import/malphasos-realm-realm.json`:

| Grupo | Roles | Perfil |
|---|---|---|
| `admins` | **20** de 22 | Todo menos las dos de `super.*`, que no tiene ningún grupo. (Eran 18 de 20 hasta el **2026-09-27**) |
| `engineers` | **13** (antes 11, y 4 al principio) | Lectura completa (`person`, `location`, `client`, `service-area`, `equipment`, `engineer`) + `equipment.write` + `equipment.assign` + los tres de `work-order` + **los dos de `report`** |
| `clients` | **5** (antes 4) | Solo lectura: `client.read`, `equipment.read`, `service-area.read`, `work-order.read` y **`report.read`** |

**El ingeniero recibe `report.write` aunque no escriba clientes ni sedes**, y no es una excepción al perfil: llenar el reporte **es** su oficio, y es el único que está delante del equipo. El cliente lee los reportes de sus equipos y no los llena. La prueba que comprueba que nadie escribe fuera de su oficio **no incluye `report.write` en la lista de vedadas** a propósito, y lo dice por escrito; que el cliente no la tenga se comprueba aparte.

`admins` recibe también los cuatro roles nuevos, **pese a que la expansión de `admin.full` se los concedería igualmente**. El realm debe poder leerse sin conocer el código: un `admins` sin `person` ni `location` afirmaría por escrito que un administrador no puede tocar personas ni ciudades.

Los cuatro roles nuevos —`person.read`, `person.write`, `location.read`, `location.write`— **llevan descripción, al contrario que los quince anteriores**, y sin tildes porque el archivo es ASCII puro y viaja entre entornos.

## Cómo se verifica: dos invariantes estructurales

139 pruebas de seguridad (conteo XML de Surefire del 2026-09-08: 82 en `SecurityIntegrationTest`, 19 en `ApiAuthorityTest`, 14 en `RestAuthorizationCoverageTest`, 11 en `KeycloakRoleConverterTest`, 7 en `RealmAuthorityContractTest`, 6 en `SecurityConfigRoleHierarchyTest`).

Lo que más pesa no son los casos, sino dos invariantes, y la razón importa: **las 83 anotaciones se pusieron con un script por número de línea**. Un desfase de una línea deja un endpoint con la autoridad del vecino, y ninguna prueba de HTTP por casos lo ve.

**`RestAuthorizationCoverageTest`** recorre los controladores por reflexión —escanea el classpath, no arranca Spring— y exige:

- toda operación publicada declara `@PreAuthorize`, y ninguno a nivel de clase;
- la autoridad citada existe en `ApiAuthority` (`hasAuthority('equipmentt.read')` compila, arranca y responde 403 a todo el mundo para siempre);
- ningún controlador nombra al administrador;
- toda consulta `GET` se protege con una autoridad `.read`, y ninguna escritura se conforma con una;
- toda autoridad del catálogo protege algún endpoint — **sin excepciones desde el 2026-09-13**, cuando `work-order` dejó de ser una y se retiró el filtro que la eximía;
- `work-order.assign` protege **una sola** operación, y es `assign`.

Los umbrales van como **mínimos** (`>= 83`, `>= 27`, `>= 56`), no como igualdades: sirven de guarda de no vacuidad —que la comprobación siguiente no pase por lista vacía— sin romperse cada vez que se añade un endpoint legítimo.

**`RealmAuthorityContractTest`** confronta el vocabulario del código con el JSON del realm: ni un rol de más ni de menos, y qué recibe cada grupo. Si divergen, hoy el administrador se quedaría sin algo **sin que nada fallara**.

## La escalera de usuarios (2026-09-13)

**Quién puede crear, editar y retirar a quién.** Los tres actos siguen la misma escalera: quien puede crear un tipo puede corregirlo y retirarlo.

| Quién | Sobre quién |
|---|---|
| **SuperUsuario** (`super.admin.full`) | Administrador · Representante de cliente · Ingeniero |
| **Administrador** (`admin.full`) | Representante de cliente · Encargado |
| **Representante · Ingeniero** | nadie |
| *SuperUsuario* | **se crea solo a mano en Keycloak** |

La línea que separa los dos escalones **tiene significado**: `MANAGER` y `CEO_CLIENT` son personas **del cliente**; `ENGINEER` y `ADMIN` son personal **de BolívarBioingeniería**. No es una división arbitraria, y es la misma que sostendrá el cupo de representantes por cliente cuando llegue.

### Por qué basta una autoridad nueva y no cuatro

La escalera tiene **dos peldaños**, no cuatro, de modo que no hace falta una autoridad por tipo. `person.write` cubre la gente del cliente; `super.person.write`, la de la casa.

**El prefijo `super.` no es decorativo**: marca exactamente lo que `admin.full` **no** concede al expandirse. La regla se lee en los nombres sin abrir el código, y tres invariantes la sostienen —que `admin.full` no la alcance, que los dos conjuntos no se toquen, y que ningún grupo del realm la conceda—.

**No se llama `engineer.write`** a propósito: `engineer.read` y `engineer.assign` ya existen y hablan del *encargado*, que es lo que el código llama `Manager`. Ese nombre ya estaba tomado por otro concepto, y meter un cuarto sinónimo en esa confusión habría sido peor que el nombre largo.

### La única excepción a la autoridad literal

Al **crear**, el tipo está en la ruta —`/persons/engineers`, `/persons/ceo-clients`— y la anotación basta. Al **editar o retirar**, no: `PUT /persons/{id}` no dice de qué tipo es esa persona, lo dice la fila.

Esas operaciones delegan en un bean:

```java
@PreAuthorize("@personWriteGuard.canWrite(#id, authentication)")
```

Tres decisiones que conviene no perder:

- **El bean vive en `person/infrastructure/input/security/`, no en `bootstrap`.** Necesita `PersonType` y el puerto de personas, y **`bootstrap` no importa nada de ningún módulo de negocio** —comprobado, y lleva cinco módulos siéndolo—. Ponerlo allí habría invertido esa dependencia.
- **El servicio sigue sin saber quién llama.** Ninguna clase de `application` ni de `domain` toca `Authentication`. La decisión se toma antes de entrar, en la capa que ya autorizaba.
- **La excepción está acotada por una prueba.** `laExcepcionEstaAcotada` exige que la forma de bean solo aparezca bajo `/v1/api/persons` y en operaciones que reciben un identificador. Sin ese límite sería la vía para esquivar cualquier autoridad literal, y el modelo volvería a estar repartido por los controladores.

Los **correos y teléfonos delegan igual**. Cuelgan de `/persons/{personId}/…`, así que la persona está en la ruta; sin eso quedaba una puerta trasera para editar los contactos de alguien a quien no se puede editar.

### `POST /persons` quedó acotado a encargados

Es la única alta que **no crea usuario**. Aceptaba cualquier `PersonType`, incluido `SUPER_ADMIN`: no daba acceso —sin cuenta no se entra— pero permitía escribir una fila que dice ser algo que no es, y saltarse la escalera que las otras puertas imponen. Ahora solo admite `MANAGER`, que es justo para lo que sirve.

## Lo que este modelo todavía no hace

- **No hay filtrado por dueño.** Un usuario del grupo `clients` con `client.read` ve **todos** los clientes y el catálogo entero, no solo el suyo. Fue una decisión explícita de dejarlo fuera de esta tanda, no un olvido. Verificado el 2026-09-08: **ninguna clase fuera de `bootstrap/config` toca `Authentication`, `SecurityContextHolder` ni `@AuthenticationPrincipal`**, de modo que ningún servicio sabe quién llama. Implementarlo no es añadir un `WHERE`: exige decidir cómo se ata una cuenta de Keycloak a un cliente del dominio.
- **No es verificable que el realm que Keycloak importa coincida con el JSON del repositorio.** Las pruebas leen el archivo versionado subiendo directorios desde el módulo; una edición hecha a mano en la consola de administración no la ve nadie. El contrato es con el archivo, no con el servidor.

## Reutilizable

`reusable:media` — el vocabulario concreto es de este dominio, pero **el patrón sí se traslada**: expandir el rol de mando en un solo punto en vez de repetirlo en cada anotación, aplicarlo en dos capas derivadas de la misma constante, y sostenerlo con invariantes estructurales por reflexión en vez de con casos uno a uno.

## Notas relacionadas

[[seguridad-keycloak-backend]] · [[keycloak-configuracion]] · [[decisiones-tecnicas-malphasos]] · [[deuda-tecnica-y-riesgos]] · [[stack-spring-boot-4-particularidades]] · [[dominio-cliente]] · [[relacion-manager-persona]]
