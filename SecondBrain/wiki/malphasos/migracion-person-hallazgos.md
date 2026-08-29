---
name: migracion-person-hallazgos
description: Los 22 defectos encontrados al migrar person_hexagon capa por capa, y qué los destapó
tags: [malphasos, person, hallazgos, migracion]
source: person_hexagon (original) → malphasos/src/.../person (MalphasOS)
updated: 2026-08-29
---

# Migración de `person_hexagon`: qué apareció al mirarlo de cerca

Primer módulo de dominio migrado completo, en seis commits de adentro hacia afuera: dominio, esquema, aplicación, persistencia, identidad y REST. Se decidió conservar el patrón de **Generación 1** del original (ver [[evolucion-arquitectonica-crud-a-cqrs]]), es decir, sin agregados ni eventos de dominio.

El dato relevante no es el volumen —53 clases del original frente a 45 en MalphasOS— sino que la migración **destapó 22 defectos**, varios de ellos capaces de romper el sistema en producción. Esta nota los recoge para que sirvan de aviso al migrar los módulos restantes: los mismos patrones se repiten en `client_hexagon`, que comparte generación y estilo.

## Defectos que impedían que algo funcionara

| Defecto | Dónde | Consecuencia |
|---|---|---|
| El DTO de creación no tenía `tipoPersona`, pese a alimentar una columna `NOT NULL` | `PersonCreateRequest` | **El endpoint de creación fallaba siempre** contra la restricción de la base |
| `addEmail`/`addPhone` lanzaban `NullPointerException` al agregar el primer contacto | `Person` | Las listas quedaban en `null` porque el builder no las inicializaba |
| El adaptador mapeaba colecciones perezosas fuera de transacción | `PersonPersistenceAdapter` | `LazyInitializationException`, oculta solo porque `open-in-view` viene activo por omisión. Ver [[antipatron-open-in-view]] |
| `n_segundo_apellido` era obligatorio en la entidad, contra el esquema | `PersonEntity` | Rechazaba a cualquier persona con un solo apellido |
| La función `validar_roles_persona()` no tenía trigger asociado | esquema SQL | Dos reglas de negocio que **nunca se ejecutaron** |
| `createSuperAdminUser` devolvía `null` | `PersonIdentityAdapter` | Contrato que promete algo que no ocurre |

## Defectos con impacto de seguridad

| Defecto | Consecuencia |
|---|---|
| El `ControllerAdvice` del módulo capturaba `Exception` y devolvía `ex.getMessage()` al cliente | Divulgación de información interna, además de interceptar errores que el advice transversal trataba mejor |
| `KeycloakUnauthorizedException` respondía **401** al llamante | Quien carece de permisos es el cliente administrativo del servicio frente a Keycloak, no quien llama. Un 401 le pide autenticarse por una mala configuración del servidor. Corregido a 502 |
| El `Response` de JAX-RS nunca se cerraba | Fuga de conexiones en cada alta de usuario |
| El `switch` de grupos de Keycloak era una sentencia sin caso por defecto | Un `RoleType` nuevo habría creado usuarios **sin ningún grupo**, es decir sin permisos, y en silencio |
| `KeycloakRoleConverter` hacía casts sin verificar sobre los claims del token | `ClassCastException` dentro del filtro de seguridad ante un token con forma inesperada |

## Defectos de arquitectura

- **`PersonService` dependía de `PersonIdentityAdapter`**, la clase concreta, en lugar del puerto. `PersonIdentityPort` existía y nadie lo usaba: el puerto no cumplía ninguna función y la capa de aplicación quedaba atada a Keycloak.
- **`PersonCommunicationPort`**, un puerto de la capa de aplicación, **importa tipos de `infrastructure`**. Las dependencias deben apuntar hacia adentro. Este adaptador se aplazó, no se migró.
- **`PersonErrorResponse`** vivía en el paquete de dominio siendo parte del contrato del API.
- **`delete(Person)` del puerto de persistencia no borraba**: llamaba a `save` y descartaba el resultado.

## Datos que se perdían en silencio

- `segundoTipoPersona` viajaba en la petición y el servicio nunca lo trasladaba a la persona construida.
- `validarRoles()` no se invocaba desde ningún sitio una vez trasladadas las reglas al dominio.
- `tipoPersona` era `String`: nada impedía escribir `"ingeniero"` o `"Engineer"`, y el error solo aparecía al insertar. Ahora es el enum `PersonType`.
- El enum `RoleType` conocía 3 valores mientras el catálogo de la base aceptaba 5.

## Inconsistencias del contrato REST

Las tres operaciones de registro colgaban de **tres versiones distintas del API**: `/vi/` (error tipográfico por `/v1/`), `/v1/` y `/v2/`. Los métodos devolvían `Object` y `List<?>`, con lo que OpenAPI no podía documentar ninguna respuesta. Los correos se creaban con `PUT` y colgaban de rutas propias, como si existieran sin la persona. Y había typos en `registerAdimn` y `tuUpdatePerson`.

## Errores cometidos durante la migración, no heredados

Conviene separarlos de lo anterior: estos no son defectos del original sino fallos propios al portarlo. Aparecieron **probando la aplicación a mano desde Swagger**, con la batería automática en verde.

**Se perdió una regla al trasladarla al dominio.** Las reglas de combinación de tipos estaban repartidas entre la función `validar_roles_persona()` (dos reglas) y la restricción `CHK_segundo_tipo_persona` de la tabla (una tercera: el segundo tipo solo puede ser encargado). Se portaron las dos primeras y se pasó por alto la de la restricción. Consecuencia: `MANAGER` + `ADMIN` atravesaba el dominio sin freno, la base lo rechazaba, y el cliente recibía un 500 que no explicaba nada.

La lección es general y aplica a los módulos que faltan: **al migrar reglas de un esquema hay que revisar las restricciones `CHECK`, no solo las funciones y triggers**. Ver el inventario de sitios donde esconderse en [[reglas-de-negocio-en-el-esquema]].

**Las pruebas no lo detectaron porque cubrían las reglas conocidas.** Una regla que nadie identificó no aparece en ninguna prueba. Es el límite de las pruebas escritas por quien migra: verifican lo que entendió, no lo que existe. De ahí el valor de probar la aplicación a mano contra datos reales.

## Defectos heredados que sobrevivieron a la revisión

Una tercera categoría, distinta de las dos anteriores y quizá la más instructiva: defectos que **sí estaban en el original**, que se portaron fielmente, y que ninguna de las dos lecturas del código detectó. Fueron los últimos en caer, y los destapó la misma prueba manual —intentar registrar un ingeniero desde Swagger—, no la batería automática.

**El adaptador de identidad estaba traducido a medias.** `createUser` interpretaba los códigos del `Response` de Keycloak, pero no capturaba nada: cuando el cliente falla antes de entregar respuesta —no logra autenticarse, no alcanza el servidor— lanza en lugar de devolver un código, y esa excepción escapaba hasta el servlet como un 500 fuera del contrato del API. `deleteUser`, en el mismo archivo, sí lo contemplaba. La inconsistencia vivía dentro de una sola clase, en el original y en la copia. Ver [[traduccion-de-fallos-de-adaptadores]], que generaliza el caso a los adaptadores que faltan por migrar.

**Los secretos de los clients eran la máscara del export.** Los dos clients confidenciales quedaron con el secreto literal `**********`, que es lo que Keycloak escribe al exportar y lo que toma como valor real al reimportar. Verificado: los dos `realm-export.json` del original lo traen así. Al transformar el export para MalphasOS se arreglaron `admin.full`, `frontendUrl`, la fuerza bruta y la política de contraseñas, y aun así este campo pasó desapercibido, porque la consola de administración enmascara los secretos exactamente igual y nada en la interfaz lo delata. Ver [[keycloak-configuracion]].

De los dos, el primero hacía fallar el registro; el segundo era la razón por la que fallaba.

Un detalle menor del mismo commit, este sí introducido al migrar: `@Valid` quedó sobre las listas de contactos en vez de sobre su argumento de tipo (`List<@Valid Email>`). Hibernate Validator lo acepta pero lo marca como obsoleto, y llenaba el log en cada petición.

**Lo que enseña la categoría**: revisar un archivo buscando defectos conocidos no equivale a revisarlo entero. En el adaptador se corrigieron cuatro cosas y se pasó por alto una quinta que estaba a diez líneas; en el realm, cinco de seis. La atención se agota en lo que se fue a buscar.

## Lo que enseñó el proceso

**Seis defectos los encontró la ejecución, no la lectura del código**: el `LazyInitializationException`, la desincronización entre el enum y el catálogo de la base, un error propio al escribir `"ingeniero"` donde correspondía `ENGINEER`, la regla de tipos que faltaba, la mitad sin traducir del adaptador de identidad y los secretos enmascarados del realm. Los tres primeros los destaparon las pruebas automáticas; los tres últimos, pruebas manuales desde Swagger. Y de los seis, **la mitad estaba en el original y se leyó sin verla**. Leer el código no basta, y escribir pruebas tampoco alcanza cuando se desconoce una regla.

**Desactivar `open-in-view` sacó a la luz un problema latente.** El original funciona con esa opción activa, que es su valor por defecto y un antipatrón conocido. Al apagarla, el fallo apareció de inmediato. Conviene mantenerla apagada precisamente por eso.

**El patrón de Generación 1 resultó menos distante de la Generación 2 de lo previsto.** Con el enum y las reglas de validación, `Person` ya tiene comportamiento propio: lo que falta para convertirlo es `extends AggregateRoot`, una factoría estática y los eventos. La conversión posterior sería barata.

## Notas relacionadas

[[dominio-persona-identidad]] · [[traduccion-de-fallos-de-adaptadores]] · [[antipatron-open-in-view]] · [[decisiones-tecnicas-malphasos]] · [[deuda-tecnica-y-riesgos]] · [[checklist-reutilizacion]] · [[dominio-cliente]]
