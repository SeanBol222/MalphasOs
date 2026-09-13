---
name: dominio-orden-trabajo
description: El modulo de ordenes de trabajo de MalphasOS, primera de cuatro tandas -el esquema V6-, con las decisiones que se apartan del original y las siete reglas que el esquema deja al servicio
tags: [dominio, work-order, esquema, mantenimiento, "describe:ambos"]
source: malphasos/src/main/resources/db/migration/V6__work_order.sql
estado: incompleto
updated: 2026-09-12
---

# Órdenes de trabajo — el esquema (tanda 1 de 4)

**Estado**: existe el esquema y nada más. No hay agregados, ni servicios, ni controladores. Los **siete requisitos** de la categoría (RF-01 a RF-07) siguen contando como **no implementados**, y el marcador del producto sigue en **8 de 31** — ver [[hoja-de-ruta-producto]].

Construido el **2026-09-12** en la rama `feat/work-order-schema`, **sin mergear**: `378cab2` (migración `V6__work_order.sql`, 132 líneas) y `24e7640` (`WorkOrderSchemaTest`, 571 líneas).

## Por qué este módulo es distinto de los cuatro anteriores

Los cuatro módulos que hay en `main` —`person`, `location`, `client`, `equipment`— se **migraron**: existía código y esquema del original y se reconstruyeron. Éste **no se migra, se construye**. El original tiene una tabla `orden_trabajo` de seis columnas que no sirve de plantilla (ver más abajo) y **ni una línea de código Java** que la use.

Es además el núcleo del negocio: de las órdenes de trabajo cuelgan los reportes de servicio, la firma digital y el historial de intervenciones de las hojas de vida. El grafo está en [[hoja-de-ruta-producto]].

Las cuatro tandas, en el mismo orden que llevaron los otros módulos: **esquema** → dominio → aplicación y persistencia → REST.

## Lo que el original tenía, y por qué no sirve de plantilla

`DataBase/v4/initdb/A_Sigma_DB_V4.sql`, tabla `orden_trabajo`:

```sql
k_id_orden_trabajo    uuid      NOT NULL,
f_fecha_mantenimiento timestamp NOT NULL,
n_periodicidad        varchar(15) NOT NULL,
k_identificador       uuid      NULL,       -- ingeniero
t_estado_ejecucion    varchar(15) NOT NULL DEFAULT 'CREATED',
b_estado_activo       boolean   NOT NULL DEFAULT true
```

Cuatro ausencias y una referencia rota, todas verificadas sobre el archivo el 2026-09-12:

- **No hay cliente.** Una orden no sabe a quién se le presta el servicio.
- **No hay sede.** Tampoco dónde.
- **No hay tipo de servicio.** Preventivo, correctivo y calibración no existen como dato en ningún sitio del esquema.
- **No hay vínculo con los equipos.** Ninguna tabla puente, ninguna columna.
- **`grep "REFERENCES orden_trabajo"` no devuelve nada**: ninguna restricción del esquema original apunta a esta tabla. El único intento —`reporte_servicio.k_id_orden_trabajo`— **es `varchar(10)` contra una llave primaria `uuid`**, así que la clave foránea no podría declararse aunque alguien quisiera. Su columna hermana, `k_id_equipo_cliente`, tiene el mismo problema. `reporte_servicio` solo tiene declarada su `PRIMARY KEY`.

De modo que el ciclo completo de mantenimiento del original —orden → reporte → protocolo → verificaciones— **está desconectado en la base**. Ver la corrección de [[esquema-bd-v4]], que afirmaba que `reporte_servicio` tenía clave foránea a `orden_trabajo`.

## Las decisiones del esquema V6, con su porqué

### La orden guarda cliente y sede propios, aunque sean deducibles

Los dos se podrían deducir recorriendo los equipos de la orden. Se guardan igual, y es **redundancia deliberada**:

- Un equipo **puede trasladarse a otra sede del mismo cliente** —eso es exactamente lo que permite [[regla-traslado-mismo-cliente]]—. Sin estas columnas, una orden **ya ejecutada** cambiaría de sede retroactivamente el día del traslado. El histórico dejaría de ser histórico.
- Hace comprobable "una orden pertenece a un solo cliente", que sin columna propia no es afirmable.
- Evita que la consulta más frecuente sea un *join* de cuatro tablas.

El coste aceptado: dos columnas que pueden contradecir a los equipos de la orden si el servicio las deja. **Impedirlo es trabajo del servicio**, y está en la lista de siete de más abajo.

### Y esa pertenencia la comprueba el motor, no la convención

```sql
ALTER TABLE sede ADD CONSTRAINT "UQ_sede_identidad_con_cliente" UNIQUE (k_id_sede, k_id_cliente);
...
CONSTRAINT "FK_orden_trabajo_sede_del_cliente"
    FOREIGN KEY (k_id_sede, k_id_cliente) REFERENCES sede (k_id_sede, k_id_cliente)
```

`k_id_sede` ya es única por ser llave primaria: el `UNIQUE` **no añade ninguna regla nueva sobre `sede`**, solo expone el par `(sede, cliente)` como destino referenciable, que es lo que Postgres exige para admitir una clave foránea compuesta. Con eso, **una sede que existe pero es de otro cliente falla en la base**, no en el servicio.

Dos detalles que merecen quedar:

- **Se añade en `V6`, no editando `V4`.** Una migración ya aplicada no se toca; Flyway la tiene sellada por *checksum*.
- **No estaba en el encargo.** La propuso el `desarrollador` al escribir la migración. Es la **única regla cruzada de este módulo que el esquema puede sostener sin desnormalizar más**, y por eso es la única que no aparece en la lista de siete.

### La tabla puente congela el área

`orden_trabajo_equipo` guarda `k_id_area_servicio`: **el área en la que estaba el equipo cuando se seleccionó**, no la de hoy. Misma razón que las dos columnas anteriores, aplicada al nivel de detalle.

Y **no lleva clave foránea compuesta contra `equipo_cliente`, a propósito**. El porqué es un caso fino de PostgreSQL y tiene nota propia: [[congelar-una-referencia-historica]].

### Las áreas del formulario no se guardan

RF-03 y RF-04 describen un formulario con un paso intermedio: se eligen áreas de la sede, y luego equipos de esas áreas. **Las áreas elegidas no se persisten.** Quedan implícitas en los equipos seleccionados.

El motivo es el de siempre con los datos duplicados: **dos listas que deben concordar se desincronizan**, y aquí no hay ninguna pregunta que la lista de áreas conteste y la de equipos no. Conviene saberlo antes de leer RF-04 y buscar la tabla que le corresponde: no existe, y es intencional.

### La fecha es `date`, no `timestamp`

El original la declaraba `timestamp without time zone` y la comentaba como *«la fecha en la que se creó la orden de trabajo»*. **Son dos cosas distintas**: la columna se llama `f_fecha_mantenimiento` y el formulario de la ERS pide un **día de servicio**, no el instante del registro. Aquí es `date`, sin hora, y es la del servicio.

### El ingeniero asignado es anulable

`k_identificador` admite nulo porque **una orden se crea antes de asignarse**. Eso es lo que justifica que el realm y `ApiAuthority` tengan `work-order.assign` separada de `work-order.write`: asignar es una operación distinta de crear, con su propio permiso. Ver [[modelo-de-permisos]].

## Las tres enumeraciones, y de dónde salieron

```
n_periodicidad     varchar(10)  MENSUAL · TRIMESTRAL · SEMESTRAL · ANUAL
t_tipo_servicio    varchar(11)  PREVENTIVO · CORRECTIVO · CALIBRACION
t_estado_ejecucion varchar(12)  CREADA · EN_EJECUCION · EJECUTADA   (default CREADA)
```

**Ninguna se inventó**, y el registro de dónde salió cada una importa más que la lista:

| Enumeración | Origen verificado |
|---|---|
| Periodicidades | El `CHECK` de `orden_trabajo` del esquema heredado. En `v2` y `v3` está en español y en minúscula —`'mensual', 'trimestral', 'semestral', 'anual'`—; en `v4` traducido a inglés |
| Estados | El mismo sitio. En `v3` el `CHECK` dice `CREADO, EJECUCION, EJECUTADO`; en `v4` `CREATED, IN_PROGRESS, EXECUTED`. En MalphasOS van en femenino —`CREADA`, `EJECUTADA`— y en el original en masculino; la llave primaria de aquella tabla se llama `PK_mantenimiento`, que es probablemente con qué concordaban |
| Tipos de servicio | **No existen en el original.** Salen del vocabulario que la ERS ya usa: mantenimiento preventivo, mantenimiento correctivo, calibración |

Van en español siguiendo la convención del proyecto para el vocabulario propio del dominio, que ya trae `NIT_juridico` y `patron_constante`. Eso además **evita repetir un defecto del original**: `v4` tradujo *semestral* a **`BIANNUAL`**, palabra que en inglés significa a la vez «dos veces al año» y «cada dos años». Está en [[deuda-tecnica-y-riesgos]] con las otras cuatro inconsistencias de esta tabla.

### El método: buscar antes de preguntar

**Lo más reutilizable de esta tanda.** Los valores de las enumeraciones se le preguntaron al usuario **tres veces** antes de que apareciera que la respuesta estaba escrita en un `CHECK` del esquema heredado que nadie había abierto.

La regla que queda: **antes de pedir una decisión de vocabulario del dominio, agotar el original**. Aquí el sistema viejo no servía de plantilla para casi nada de este módulo —cuatro ausencias y una FK rota— y precisamente por eso nadie fue a mirarlo. Un esquema puede ser inútil como diseño y seguir siendo la única fuente escrita del vocabulario.

Es la misma familia de hallazgos que ya registra este wiki: [[reglas-de-negocio-en-el-esquema]] dice que un `CHECK` es uno de los seis sitios donde un esquema esconde reglas. Aquí escondía, además, un diccionario.

## Las siete reglas que el esquema permite a propósito

El esquema **no las defiende y no puede**: cada una cruza tablas que no guardan juntos los datos que hay que comparar, o depende del estado y no de la existencia. Van al servicio en la tanda 3. **Se listan aquí para que nadie las dé por cubiertas al leer la migración en verde.**

1. Que el área de una fila del puente **pertenezca a la sede de la orden**.
2. Que el equipo **estuviera en esa área** en el momento de añadirlo.
3. Que el equipo **sea del cliente de la orden**.
4. Que la persona asignada sea **de tipo `ENGINEER`**.
5. Que las referencias —cliente, sede, área, equipo, ingeniero— **estén activas**, no solo que existan.
6. Las **transiciones de estado** válidas: `CREADA → EN_EJECUCION → EJECUTADA`, y no al revés.
7. Que **no se modifique una orden ya `EJECUTADA`**.

La 5 es el caso de siempre con [[patron-soft-delete]]: una clave foránea comprueba que una fila exista, no que esté activa, y con borrado lógico esas dos cosas dejaron de ser la misma.

Con éstas, el proyecto pasa de **seis** reglas de este tipo ya construidas —dos en `client`, cuatro en `equipment`, ver [[regla-traslado-mismo-cliente]]— a **trece previstas**. Es el módulo que más carga sobre el servicio, y no por descuido: es el que más veces cruza de agregado.

## La verificación

`WorkOrderSchemaTest`, contra un PostgreSQL real vía Testcontainers: **23 métodos, 32 ejecuciones** —cuatro son `@ParameterizedTest`—. **Cero defectos encontrados**: nada de lo que la prueba pedía al esquema faltaba.

Batería completa sobre `24e7640`, medida el 2026-09-12 con `./mvnw test` y `rm -rf target/surefire-reports` **antes** —sin eso se suman informes de corridas anteriores, ver [[stack-spring-boot-4-particularidades]]—: **541** elementos `<testcase>`, **43 clases**, cero fallos, cero errores, cero omitidas. Antes eran 509 sobre `43de295`; 509 + 32 = 541 y aquí la suma cuadra porque es la misma rama con pruebas encima. El atributo `tests=` da **539** y los `.txt` **418**, por el desajuste ya conocido.

**Las dos pruebas que más valen** no son de columnas:

- `ordenConSedeDeOtroClienteFalla` — arma dos clientes con sede propia e inserta una orden con el cliente A y la sede de B. Es lo que convierte «una orden pertenece a un solo cliente» en algo comprobado por el motor.
- `trasladarElEquipoNoReescribeElAreaCongelada` — inserta una fila del puente con el área actual del equipo, **traslada el equipo después**, y comprueba dos cosas a la vez: que el traslado **no falla**, y que el área guardada **sigue siendo la vieja**. Si algún día alguien añade la clave foránea compuesta que «falta», **esta prueba se pone roja y explica por qué no debe estar**. Ver [[congelar-una-referencia-historica]].

**Un detalle de método que se repite y conviene copiar**: los valores inválidos de los `CHECK` son **cortos a propósito**. Las tres columnas están dimensionadas justas —`varchar(10)` para `TRIMESTRAL`, `varchar(11)` para `CALIBRACION`, `varchar(12)` para `EN_EJECUCION`—, así que un valor inválido más largo fallaría **por longitud y no por el `CHECK`**, dejando la prueba en verde afirmando algo que no ocurrió. Es una prueba que pasa por el motivo equivocado, primo hermano de la que pasa en vacío de [[regla-traslado-mismo-cliente]].

Las siete reglas de arriba **no se prueban aquí** a propósito: viven en el servicio de la tanda 3 y una prueba hoy daría rojo por el motivo equivocado.

## ⚠️ Una trampa esperando a la tanda REST

`RestAuthorizationCoverageTest` tiene una prueba centinela, `lasAutoridadesDeWorkOrderSiguenSinModulo`, con este cuerpo:

```java
assertThat(citadas).noneMatch(a -> a.startsWith("work-order."));
```

**Se pondrá roja el día que aparezca el primer `@PreAuthorize("hasAuthority('work-order.…')")` del módulo**, que es la tanda 4. Es el patrón de omisión consciente funcionando exactamente como se diseñó —igual que la centinela de `equipo_cliente` que se rompió al aparecer la tabla, ver [[migracion-equipment-hallazgos]]—, pero **si quien la ve no lo sabe, parecerá una regresión**. Hay que **retirarla en el mismo commit** que introduce el primer controlador, y sustituirla por las pruebas de cobertura de las tres autoridades.

La acompaña `ningunaAutoridadSobra`, que hoy **excluye explícitamente** las autoridades de `work-order` con un `filter(a -> !a.startsWith("work-order."))`. Ese filtro también sobra a partir de la tanda 4.

> **Precisión sobre un informe previo**: el `tester` afirmó el 2026-09-12 que esa prueba «no vive en esta rama». **Es falso** y quedó comprobado el mismo día: `RestAuthorizationCoverageTest` entró en `main` con `feat/permission-model` (`e6dda32`, 2026-09-09) y `git show main:…/RestAuthorizationCoverageTest.java` la devuelve. No afectó a su trabajo —no escribió nada partiendo de eso— pero se deja anotado para que no se repita la afirmación.

## Lo que falta

- **Tanda 2, dominio**: el agregado `WorkOrder` de Generación 2, con `create`/`rehydrate`, los métodos que dicen qué ocurrió (`assignEngineer`, `start`, `execute`, `addEquipment`, `removeEquipment`) y sus eventos. Decidir si la colección de equipos es parte del agregado o un agregado aparte.
- **Tanda 3, aplicación y persistencia**: los *commands*, el mapper a mano, el adaptador, y **las siete reglas**.
- **Tanda 4, REST**: los controladores, el catálogo de errores propio, el grupo de OpenAPI **con su prueba de cobertura** —[[openapi-swagger]] registra que un `pathsToMatch` que no casa no avisa— y la retirada de la centinela.

## Notas relacionadas

[[congelar-una-referencia-historica]] · [[hoja-de-ruta-producto]] · [[decisiones-tecnicas-malphasos]] · [[regla-traslado-mismo-cliente]] · [[dominio-equipo-mantenimiento]] · [[dominio-cliente]] · [[dominio-reportes]] · [[esquema-bd-v4]] · [[reglas-de-negocio-en-el-esquema]] · [[patron-soft-delete]] · [[modelo-de-permisos]] · [[deuda-tecnica-y-riesgos]] · [[stack-spring-boot-4-particularidades]] · [[openapi-swagger]] · [[evolucion-arquitectonica-crud-a-cqrs]]
