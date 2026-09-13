---
name: stack-spring-boot-4-particularidades
description: Diferencias reales de Spring Boot 4 / Flyway 12 / Testcontainers 2 frente a lo que documenta el proyecto original — descubiertas al construir MalphasOS
tags: [malphasos, stack, backend, hallazgo, "describe:malphasos"]
source: malphasos/pom.xml (MalphasOS)
updated: 2026-09-13
---

# Particularidades de Spring Boot 4 y el stack moderno

Nota escrita **desde MalphasOS**, no desde `bolivarbioingenieria-app`. Documenta diferencias reales que costaron encontrar y que no se deducen de [[stack-tecnologico]], porque el proyecto original usa Spring Boot 4.0.6 y MalphasOS arrancó en 4.1.1 con librerías más nuevas.

## 1. Las autoconfiguraciones están modularizadas

En Spring Boot 4 cada tecnología tiene su propio módulo de autoconfiguración. Poner la librería suelta en el classpath **ya no basta**:

- `org.flywaydb:flyway-core` por sí solo **no activa nada**. Hay que usar `org.springframework.boot:spring-boot-starter-flyway`, que trae `spring-boot-flyway` (el módulo de autoconfiguración) además de `flyway-core`.
- Esto se nota también en los paquetes de las clases internas: `org.springframework.boot.jdbc.autoconfigure.DataSourceProperties`, `org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration`, `org.springframework.boot.hibernate.autoconfigure.HibernateJpaConfiguration`.

Síntoma cuando falta el starter: Flyway no imprime **ni un log**, no crea `flyway_schema_history`, y la aplicación arranca como si Flyway no existiera.

## 2. Los starters de test también son modulares

El proyecto generado por Spring Initializr no trae `spring-boot-starter-test` monolítico, sino uno por tecnología: `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-amqp-test`, `spring-boot-starter-flyway-test`, etc. Al agregar una tecnología nueva conviene agregar su starter de test correspondiente.

## 3. Flyway 12 separa el soporte de cada motor

`flyway-core` 12.x no sabe hablar con PostgreSQL por sí solo. Hace falta además:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

Síntoma cuando falta: la aplicación **falla al arrancar** con `Unsupported Database: PostgreSQL 17.10`. Es decir, hacen falta **las dos** dependencias: el starter (para que la autoconfiguración exista) y el módulo del dialecto (para que sepa hablar con el motor).

## 4. Testcontainers 2.x renombró sus artefactos

Spring Boot 4.1.1 fija `testcontainers.version = 2.0.5`. En la versión 2 los módulos llevan prefijo:

| Testcontainers 1.x | Testcontainers 2.x |
|---|---|
| `org.testcontainers:postgresql` | `org.testcontainers:testcontainers-postgresql` |
| `org.testcontainers:junit-jupiter` | `org.testcontainers:testcontainers-junit-jupiter` |

Síntoma con los nombres viejos: Maven ni siquiera lee el proyecto, falla con `'dependencies.dependency.version' ... is missing` (porque el BOM no gestiona esos artefactos, ya no existen con ese nombre).

Las clases Java **no** cambiaron de paquete: `org.testcontainers.containers.PostgreSQLContainer` y `org.testcontainers.utility.DockerImageName` siguen igual.

## 5. Jackson 3 ya viene por defecto

En los logs de arranque aparece `JacksonAutoConfiguration#jsonMapperBuilder` resolviendo `tools.jackson.databind.json.JsonMapper$Builder` — es decir, **Spring Boot 4 ya usa Jackson 3 de serie**. Esto explica retroactivamente la mezcla rara de Jackson 2 y 3 que [[stack-tecnologico]] marcaba como riesgo en el proyecto original: no era un experimento, era la transición del propio framework. En MalphasOS no se declaró ninguna dependencia de Jackson y funciona correctamente.

## Surefire da dos conteos distintos de la misma ejecución

Al citar un número de pruebas conviene saber que **depende de dónde se mire**, y que el camino más obvio da otro.

Medido el **2026-09-08** sobre `feat/permission-model`, con `rm -rf target/surefire-reports` antes de cada ejecución:

| Fuente | Suma | Qué cuenta |
|---|---|---|
| atributo `tests=` de `TEST-*.xml` | **472** | todas las pruebas, incluidas las de clases `@Nested` |
| `target/surefire-reports/*.txt` | **358** | **omite por completo las pruebas que viven en clases `@Nested`** |

**Los dos números son correctos según lo que miden.** Este wiki y el `CLAUDE.md` de la raíz publican el conteo de los XML. Quien reverifique con `grep "Tests run" target/surefire-reports/*.txt` obtendrá el otro y creerá que la cifra está inflada: no lo está, está contando otra cosa.

### Corrección: la causa del desfase no era la que decía esta nota (2026-09-08)

Hasta hoy esta nota afirmaba que el `.txt` **cuenta un `@ParameterizedTest` como una sola prueba** y que los 32 de diferencia del 2026-09-02 salían de «11 métodos parametrizados expandidos en 43 invocaciones». **Es falso**, comprobado por clase:

- `ApiAuthorityTest` tiene un método `@ParameterizedTest` y **no** tiene `@Nested`: `.txt` y XML dicen **19 los dos**. Igual `CountryTest` (16/16), `ClientTest` (20/20), `EventMetadataTest` (13/13). El `.txt` sí cuenta cada invocación.
- La diferencia de 114 sale **entera** de tres clases, y las tres usan `@Nested`. Su `.txt` dice literalmente `Tests run: 0`:

| Clase | `.txt` | XML |
|---|---|---|
| `SecurityIntegrationTest` (6 clases anidadas) | 0 | 82 |
| `CatalogAggregatesTest` (5 anidadas) | 0 | 23 |
| `EquipmentChainServiceTest` (3 anidadas) | 0 | 9 |

Las dos últimas no se han tocado desde antes del 2026-09-02 y suman **exactamente 32**, que es el desfase que aquella medición atribuyó a los parametrizados. La cifra era correcta; la explicación, no. Es un caso de manual de conclusión que encaja con el dato y aun así apunta al mecanismo equivocado.

### Corrección: tampoco el atributo `tests=` cuenta todo (2026-09-09)

Esta nota daba el atributo `tests=` de los XML como el conteo bueno, "todas las pruebas". **No lo es: también se queda corto**, y hay un tercer número que nadie había mirado.

Medido dos veces el 2026-09-09 sobre `fix/person-identity-sync`, borrando `target/surefire-reports` antes de cada ejecución. Las dos corridas dieron exactamente lo mismo:

| Fuente | Suma | Qué cuenta |
|---|---|---|
| Resumen de Maven (`[INFO] Tests run:` final) y número de elementos `<testcase>` de los XML | **363** | **Las ejecuciones reales.** Es el techo |
| atributo `tests=` de los `<testsuite>` | **361** | Dos de menos |
| `target/surefire-reports/*.txt` | **329** | Omite por completo las clases `@Nested` |

**Los dos que faltan salen de una sola clase**, y la causa es concreta y comprobable: `CatalogAggregatesTest` declara `tests="23"` y contiene **25** elementos `<testcase>`. Tres de sus clases `@Nested` tienen un método con **el mismo nombre**, `referenciasObligatorias`, y el atributo agregado del `<testsuite>` los cuenta **una sola vez**. Comprobado recorriendo los 36 XML: es la única clase del proyecto con nombres de método repetidos entre `@Nested`, y es la única en la que el atributo y el recuento de elementos difieren.

Corolario práctico: **el número honesto es el del resumen de Maven**, que coincide con contar `<testcase>`. Los conteos publicados hasta ahora (339, 472, 361) no están inflados — están ligeramente **por debajo** de las pruebas que se ejecutaron, en la medida en que haya nombres de método repetidos entre clases anidadas.

### La remedición que quedaba pendiente: `main` con las dos ramas dentro (2026-09-09)

Esta nota y el `CLAUDE.md` de la raíz avisaban de que **el día que se mergearan las dos ramas habría que remedir, no sumar**. Se mergearon ese mismo día (`e6dda32` y `258cd81`), y el resultado es el dato que faltaba:

| Fuente | `main` (`01c3277`) | `feat/relocation-same-client` (`3c002b2`) | `feat/relocation-same-client` (`43de295`) | `feat/work-order-schema` (`24e7640`) |
|---|---|---|---|---|
| elementos `<testcase>` — **el conteo honesto** | **496** | **496** | **509** | **541** |
| atributo `tests=` de los `<testsuite>` | 494 | 494 | 507 | 539 |
| `target/surefire-reports/*.txt` | 380 | 380 | 386 | 418 |
| clases · fallos · errores · omitidas | 41 · 0 · 0 · 0 | 41 · 0 · 0 · 0 | 42 · 0 · 0 · 0 | 43 · 0 · 0 · 0 |

Medido con `./mvnw test` y `rm -rf target/surefire-reports` antes de cada corrida. **496 no es 472 + 361 menos nada**: sumar habría dado un número inventado, que es exactamente contra lo que avisaba la nota.

Los dos que separan 496 de 494 siguen saliendo de `CatalogAggregatesTest`, la única clase con nombres de método repetidos entre `@Nested`. Las dos primeras columnas dan lo mismo porque **`3c002b2` no añadió ninguna prueba**: ver [[regla-traslado-mismo-cliente]] para qué significa —y qué no significa— ese verde.

**Ampliación del 2026-09-12, segunda pasada**: la cuarta columna es `feat/work-order-schema`, que desciende de `43de295`, así que **541 = 509 + 32** también es una suma legítima. La clase 43 es `WorkOrderSchemaTest` —23 métodos, 32 ejecuciones, cuatro de ellos `@ParameterizedTest`—. La diferencia `<testcase>` – atributo **sigue siendo 2**, de modo que después de cinco tandas el desajuste sigue viniendo entero de `CatalogAggregatesTest`. Los `.txt` saltan de 386 a 418, exactamente +32: `WorkOrderSchemaTest` **no tiene clases `@Nested`**, y ahí se ve el contraste con las columnas anteriores. Medido con `./mvnw test` y `rm -rf target/surefire-reports` antes.

**Ampliación del 2026-09-13, el módulo de órdenes de trabajo completo.** Dos columnas más, medidas igual:

| Fuente | `main` (`97ef74f`) | `ceadba1` (REST) | `main` (`b9563a9`, todo dentro) |
|---|---|---|---|
| elementos `<testcase>` — **el conteo honesto** | **580** | **612** | **614** |
| atributo `tests=` de los `<testsuite>` | 578 | **609** | **611** |
| `target/surefire-reports/*.txt` | **418** | 433 | **433** |
| clases · fallos · errores · omitidas | 44 · 0 · 0 · 0 | 46 · 0 · 0 · 0 | 46 · 0 · 0 · 0 |

### La vez que no deducir salió a cuenta, con número

La tercera columna estuvo **con guiones durante un commit**. Al añadir las dos pruebas de la regla de la sede solo se midió `<testcase>` (614), y esta nota dijo que «afirmar **611 y 435** sin haberlos contado sería inventarlos».

Al medirse de verdad tras el merge: **611 y 433**. Uno acertado y **el otro no**.

El fallo habría sido en los `.txt`, y por la razón que esta nota lleva repitiendo: las dos pruebas nuevas están dentro de una clase `@Nested` ya existente, `AlAnadirUnEquipo`, de modo que **esa fuente no las cuenta y se queda clavada en 433**. Quien dedujera «+2 en todo» se equivocaría exactamente en la fuente cuyo comportamiento raro está documentado tres párrafos más arriba.

**Es la mejor defensa que hay de la regla**: no es que deducir esté mal por principio, es que aquí falla **una de cada dos veces**, y la que falla es la que uno creería tener entendida.

**Dos cosas de esta tabla valen más que las cifras.**

**Los `.txt` no se movieron ni un punto entre `24e7640` y `97ef74f`**: 418 y 418, mientras los `<testcase>` subían de 541 a 580. Las 39 pruebas de `WorkOrderTest` son **invisibles** para esa fuente, porque la clase tiene **13 clases `@Nested`** y ninguna prueba suelta. Es el caso extremo de lo que esta nota ya decía —los `.txt` no cuentan las `@Nested`— y el mejor argumento disponible para no volver a citarlos: aquí habrían dicho que una tanda entera no añadió nada.

**Y el desajuste `<testcase>` – atributo pasa de 2 a 3, por primera vez desde que se mide.** Durante cinco tandas vino entero de `CatalogAggregatesTest`; ahora hay un **segundo** caso: `WorkOrderServiceTest` declara `tests="16"` y trae 17 `<testcase>`, porque **`ordenInexistente` aparece en dos de sus clases `@Nested`**. Confirma que el desajuste no es una rareza de una clase concreta sino el comportamiento normal de Surefire ante nombres de método repetidos entre anidadas, y que **crece sin avisar** a medida que se escriben pruebas — que es justo por lo que el número que se publica tiene que ser el de contar `<testcase>`.

> **Y una corrección sobre las etiquetas de la tabla, del 2026-09-12**: la primera columna, «`main` (`01c3277`)», **ya no describe `main`**. `feat/relocation-same-client` se mergeó ese mismo día (`1ef55cf`) y `main` está en `55e0a5d`, con el contenido de la tercera columna: **509**, no 496. Las cifras de cada columna siguen siendo correctas para el commit que nombran; lo que caducó es la equivalencia «primera columna = línea principal».

**Ampliación del 2026-09-12**: la tercera columna es la misma rama tras la pasada del `tester` (`43de295`, 2026-09-10) y **sí es una suma legítima**, 496 + 13, porque es la misma base con pruebas añadidas encima; sumar solo está prohibido entre ramas que no descienden una de otra. La clase 42 es `ClientEquipmentRelocationPersistenceTest`, nueva. La diferencia `<testcase>` – atributo se mantiene en 2, así que el desajuste sigue viniendo entero de `CatalogAggregatesTest` y las pruebas nuevas no añaden nombres repetidos entre `@Nested`. Medido aquí con `./mvnw test` y `rm -rf target/surefire-reports` antes.

### Y una trampa de método: Surefire no limpia sus informes

Esta nota registraba que el conteo **no era estable entre corridas** (337, 338 y 339 en tres ejecuciones seguidas del 2026-09-02). El 2026-09-08 dos ejecuciones completas dieron **472 y 472**, idénticas hasta el número de clases, así que la inestabilidad no se reprodujo.

Lo que sí se comprobó ese día es un mecanismo suficiente para producirla: **`mvn test` no borra `target/surefire-reports`**. Se plantó a mano un `TEST-com.fake.StaleTest.xml` con `tests="7"`, se ejecutó otra prueba, y el archivo falso seguía ahí y seguía sumando. Los informes de una corrida anterior —o de **otra rama**, que es lo que pasa al cambiar de rama sin reconstruir— se quedan y se suman a los de la nueva.

**Al citar un conteo, borrar el directorio primero.** Sin eso, el número no mide una ejecución sino la unión de todas las que hayan pasado por ahí.

## Notas relacionadas

[[stack-tecnologico]] · [[decisiones-tecnicas-malphasos]] · [[checklist-reutilizacion]] · [[modelo-de-permisos]]
