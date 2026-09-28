---
name: dominio-orden-trabajo
description: El modulo de ordenes de trabajo de MalphasOS, completo en sus cuatro tandas -esquema, dominio, aplicacion y REST- y con sus siete reglas de servicio construidas, incluida la que falto hasta el 2026-09-13
tags: [dominio, work-order, esquema, mantenimiento, "describe:ambos"]
source: malphasos/src/main/java/com/malphasos/malphasos/workorder/ y malphasos/src/main/resources/db/migration/V6__work_order.sql
estado: estable
updated: 2026-09-27
---

# Órdenes de trabajo — el módulo completo

**Estado**: las **cuatro tandas** están construidas —esquema, dominio, aplicación y persistencia, REST— y las **siete reglas del servicio** con ellas. **Todo mergeado** el 2026-09-13, por `e69437d`, `1adc2fc` y `b9563a9`. El módulo se puede usar de extremo a extremo desde el API.

> **Precisión del 2026-09-13**: el mensaje del commit `ceadba1` dice «ocho operaciones». **Son nueve** —contadas sobre los `@GetMapping`/`@PostMapping`/`@PatchMapping`/`@DeleteMapping` del adaptador—. El error es del mensaje, no del código; queda anotado aquí porque un cuerpo de commit no se puede corregir sin reescribir la historia.

> **Corregido el 2026-09-13**: esta nota decía «existe el esquema y nada más» y describía solo la tanda 1. Era cierto el 2026-09-12 y dejó de serlo el mismo día con `647de0b`. La nota se quedó atrás **tres tandas**: el dominio se mergeó a `main` sin pasada de wiki, y la aplicación y el REST se escribieron después. Lo que sigue cubre las cuatro.

| Tanda | Commits | Qué trae |
|---|---|---|
| 1 · Esquema | `378cab2`, `24e7640` | `V6__work_order.sql`, 132 líneas, y `WorkOrderSchemaTest` |
| 2 · Dominio | `647de0b`, `6043091`, `c484522` | El agregado `WorkOrder`, tres enumeraciones, `SelectedEquipment` y siete eventos. **En `main` desde `97ef74f`** |
| 3 · Aplicación y persistencia | `5c0a5d5`, `dd625a1` | `WorkOrderService` con las reglas cruzadas, siete *commands*, el mapper a mano y el adaptador |
| 4 · REST | `ceadba1` | Nueve operaciones, catálogo de errores propio, grupo de OpenAPI y la retirada de la centinela |
| — · La regla que faltaba | `0cf56c5` | El alcance de una orden no sale de su sede. Ver «La regla 1 faltaba» |
| — · La persistencia probada | `307d410`, `af38d2a` | 10 pruebas contra PostgreSQL real, y el `@Transactional` que faltaba en el adaptador |

## Por qué este módulo es distinto de los cuatro anteriores

Los cuatro módulos que hay en `main` —`person`, `location`, `client`, `equipment`— se **migraron**: existía código y esquema del original y se reconstruyeron. Éste **no se migra, se construye**. El original tiene una tabla `orden_trabajo` de seis columnas que no sirve de plantilla (ver más abajo) y **ni una línea de código Java** que la use.

Es además el núcleo del negocio: de las órdenes de trabajo cuelgan los reportes de servicio, la firma digital y el historial de intervenciones de las hojas de vida. El grafo está en [[hoja-de-ruta-producto]].

Y es **el módulo con más vecinos del proyecto**: pregunta a `client` por el cliente, la sede y el área, a `equipment` por cada unidad, y a `person` por el ingeniero. **Nadie le pregunta a él**, así que sigue sin haber ciclos entre módulos.

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

El coste aceptado: dos columnas que pueden contradecir a los equipos de la orden si el servicio las deja. **Impedirlo es trabajo del servicio**, y ahí es exactamente donde quedó el hueco que documenta la sección «El hueco de la regla 1».

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
- Es la **única regla cruzada de este módulo que el esquema puede sostener sin desnormalizar más**, y por eso es la única que no aparece en la lista de siete.

### La tabla puente congela el área

`orden_trabajo_equipo` guarda `k_id_area_servicio`: **el área en la que estaba el equipo cuando se seleccionó**, no la de hoy. Misma razón que las dos columnas anteriores, aplicada al nivel de detalle.

Y **no lleva clave foránea compuesta contra `equipo_cliente`, a propósito**. El porqué es un caso fino de PostgreSQL y tiene nota propia: [[congelar-una-referencia-historica]].

### Las áreas del formulario no se guardan

RF-03 y RF-04 describen un formulario con un paso intermedio: se eligen áreas de la sede, y luego equipos de esas áreas. **Las áreas elegidas no se persisten.** Quedan implícitas en los equipos seleccionados.

El motivo es el de siempre con los datos duplicados: **dos listas que deben concordar se desincronizan**, y aquí no hay ninguna pregunta que la lista de áreas conteste y la de equipos no. Conviene saberlo antes de leer RF-04 y buscar la tabla que le corresponde: no existe, y es intencional.

### La fecha es `date`, no `timestamp`

El original la declaraba `timestamp without time zone` y la comentaba como *«la fecha en la que se creó la orden de trabajo»*. **Son dos cosas distintas**: la columna se llama `f_fecha_mantenimiento` y el formulario de la ERS pide un **día de servicio**, no el instante del registro. Aquí es `date`, sin hora, y es la del servicio.

**Y no se acota por ningún extremo.** A diferencia de la fecha de compra de un equipo, que no puede estar en el futuro, la de un mantenimiento suele estarlo —se programa— y a veces está en el pasado, cuando la orden se registra después de haber ido. Ninguno de los dos extremos es un error, y el agregado solo exige que exista.

### El ingeniero asignado es anulable

`k_identificador` admite nulo porque **una orden se crea antes de asignarse**. Eso es lo que justifica que el realm y `ApiAuthority` tengan `work-order.assign` separada de `work-order.write`: asignar es una operación distinta de crear, con su propio permiso. Ver [[modelo-de-permisos]].

## El formulario, construido el 2026-09-27

El módulo estaba terminado desde el 2026-09-13 y **no se podía usar**: cuatro de sus siete requisitos describen un formulario. Ahora existe, y hay tres cosas que conviene saber de él.

**El estado no se edita, se avanza.** La ficha ofrece «iniciar» y «marcar como ejecutada», y cada botón aparece **solo cuando el estado lo permite** — que es lo mismo que comprueba el backend antes de responder «el estado no lo permite». Un desplegable de estados invitaría a saltarse el orden y a que el servidor rechazara lo que la pantalla acababa de ofrecer.

**La regla de la sede se ejerce por fin desde un navegador.** La pantalla de alcance pregunta las áreas de la sede de la orden y solo ofrece equipos de ellas: es la séptima regla, la que se construyó el 2026-09-13 al descubrir que faltaba, y hasta hoy ninguna interfaz la había puesto a prueba.

**Y una desviación consciente respecto a RF-04**, anotada en [[hoja-de-ruta-producto]] y sin contar como implementada: el requisito describe elegir áreas y después ver sus equipos; la pantalla muestra **todas las áreas de la sede a la vez**, agrupadas, con «marcar toda el área» como equivalente de elegir una. Un paso menos para el mismo resultado, pero no es lo que el requisito dice.

### Varias altas sin transacción, y la pantalla lo dice

El API suma **un equipo por llamada**. Elegir diez son diez llamadas en serie y **no hay transacción que las envuelva**: si la séptima falla, las seis primeras quedan dentro. La pantalla informa de cuántas entraron en lugar de afirmar un resultado que no ocurrió. Es el mismo patrón que el panel que crea un modelo con sus piezas, y la misma conclusión: **lo que el backend no ofrece compuesto, el frontend solo puede contarlo con honestidad**.

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

**Lo más reutilizable de la tanda 1.** Los valores de las enumeraciones se le preguntaron al usuario **tres veces** antes de que apareciera que la respuesta estaba escrita en un `CHECK` del esquema heredado que nadie había abierto.

La regla que queda: **antes de pedir una decisión de vocabulario del dominio, agotar el original**. Aquí el sistema viejo no servía de plantilla para casi nada de este módulo —cuatro ausencias y una FK rota— y precisamente por eso nadie fue a mirarlo. Un esquema puede ser inútil como diseño y seguir siendo la única fuente escrita del vocabulario.

Es la misma familia de hallazgos que ya registra este wiki: [[reglas-de-negocio-en-el-esquema]] dice que un `CHECK` es uno de los seis sitios donde un esquema esconde reglas. Aquí escondía, además, un diccionario.

## Tanda 2 — el agregado

`WorkOrder` es de **Generación 2**, con lo que eso implica en este proyecto: sin setters, entrada por `schedule(...)` —que registra evento— o `rehydrate(...)` —que no—, igualdad por identidad y colecciones devueltas como copia inmutable. Ver [[evolucion-arquitectonica-crud-a-cqrs]] y [[aggregate-root-pattern]].

### Los equipos son parte del agregado, no un agregado aparte

La decisión que más se podía haber ido por el otro lado. **Un equipo seleccionado no tiene sentido fuera de su orden** y su ciclo de vida es el de ella: se elige al planificar y deja de poder cambiarse cuando el trabajo termina. Por eso viven dentro y no se referencian por identificador como el cliente o la sede.

`SelectedEquipment` tiene **identidad por el equipo solo, no por el par**. La consecuencia se lee en `addEquipment`: añadir un equipo que ya está **no hace nada, ni siquiera si el área que se pasa es otra**. Dentro de una orden, el mismo equipo dos veces con dos áreas distintas no es dos cosas: es un intento de reescribir un área ya congelada. Para corregirla hay que retirarlo y volver a añadirlo — y eso es exactamente lo que significa hacerlo.

### El estado sabe avanzar; la columna no podría

`ExecutionState` lleva `siguiente()`, `avanzaA()` y `esFinal()`. El `CHECK` del esquema fija **qué valores existen**; no puede fijar **cómo se pasa de uno a otro**, porque una restricción de columna mira la fila que se escribe y no la que había antes. Por eso el avance vive en el dominio.

El recorrido es en un solo sentido y sin saltos: `CREADA → EN_EJECUCION → EJECUTADA`. No se salta de creada a ejecutada porque entonces nadie sabría cuándo empezó, y no se vuelve de ejecutada porque el trabajo ya se hizo; si hace falta intervenir otra vez, se crea otra orden.

`avanzarA` **distingue dos negativas que un solo mensaje confundiría**: la de una orden que ya terminó y la de un salto de estado. Quien recibe el error necesita saber cuál de las dos es.

### Tres decisiones del ciclo de vida que no son obvias

- **`start()` exige ingeniero y al menos un equipo**, y lo exige ahí y no al crear. Sin lo primero no hay quien lo haga; sin lo segundo no hay sobre qué. Son las dos condiciones que convierten una orden planificada en trabajo real. Crearla vacía sí se permite, porque el formulario de la ERS elige los equipos en un paso posterior al de la sede.
- **El alcance se puede tocar en ejecución, no solo al planificar.** En campo aparece un equipo que no estaba previsto, o uno de los elegidos resulta inaccesible. Lo que no se admite es cambiar una orden **ejecutada**, porque entonces su registro dejaría de describir lo que se hizo.
- **Cancelar se permite incluso sobre una orden ya ejecutada.** No es lo mismo que ejecutarla: una ejecutada se hizo, una cancelada no. Retirar del listado un registro histórico no reescribe lo que ocurrió. Y cancelar dos veces no emite dos eventos, siguiendo la regla del proyecto de que **un cambio que no cambia nada no emite evento**.

### Siete eventos

`WorkOrderCreated`, `Assigned`, `EquipmentAdded`, `EquipmentRemoved`, `Started`, `Executed` y `Deactivated` —de baja, no `Deleted`, porque aquí nada se borra—. Los de equipo llevan `WorkOrderEquipmentPayload`; el resto, `WorkOrderPayload`. Ver [[eventos-de-dominio]].

**Nadie los consume todavía.** El mecanismo de despacho existe y está en uso: ver [[patron-event-dispatcher-dual]].

> **Corregido el 2026-09-27.** Aquí decía que «el destinatario natural será el reporte de servicio». **El reporte se construyó ese día y no escucha ninguno de los siete**: consulta la orden por su identificador cuando necesita saber si empezó y qué equipos tiene, que es lo que hace cualquier módulo con sus vecinos. La predicción era razonable y salió falsa, y conviene que quede escrita: **los siete eventos siguen sin consumidor**, y el argumento de «ya emite lo que el siguiente módulo querrá escuchar» no justificó nada. Ver [[dominio-reporte-servicio]].

### Un defecto encontrado y corregido en esta tanda

`SelectedEquipment` rechazaba un equipo nulo con `Objects.requireNonNull`, que lanza `NullPointerException`. Ningún advice mapea esa excepción, así que **habría salido como 500 en vez de 400**: un dato inválido del llamante presentado como un fallo del servidor. Corregido en `c484522` cambiándolo a `IllegalArgumentException`, la forma que el resto del proyecto usa y que el advice sí traduce. Comprobado después que no queda ningún otro `requireNonNull` en producción. Ver [[traduccion-de-fallos-de-adaptadores]].

## Tanda 3 — las reglas que exigen preguntar fuera

Ésta es la razón de ser del servicio de este módulo. Las reglas viven en `WorkOrderService` porque **todas exigen consultar otro módulo**, y el agregado solo decide con lo que la propia orden tiene delante.

**Las referencias se comprueban activas, no solo existentes.** Una clave foránea confirma que la fila está; con borrado lógico eso deja de significar que siga en uso. Es la misma distinción que ya obligó a subir reglas al servicio en `client` y en `equipment`, y el caso de siempre de [[patron-soft-delete]].

### Estado real de las siete reglas

La tanda 1 dejó listadas siete reglas que el esquema no podía defender. Éste es su estado **verificado el 2026-09-13 leyendo el servicio**, no dado por hecho:

| # | Regla | Estado | Dónde |
|---|---|---|---|
| 1 | El área del equipo pertenece **a la sede de la orden** | ✅ | `requireEquipmentInScopeOf` |
| 2 | El equipo **estaba en esa área** al añadirlo | ✅ Hecha **imposible de violar** | El área no viaja en el comando |
| 3 | El equipo es **del cliente de la orden** | ✅ | `requireEquipmentBelongsTo` |
| 4 | La persona asignada es **de tipo `ENGINEER`** | ✅ | `requireActiveEngineer` |
| 5 | Las referencias están **activas**, no solo existen | ✅ | Las cuatro guardas privadas |
| 6 | Las **transiciones de estado** válidas | ✅ | `ExecutionState` + `avanzarA`, en el dominio |
| 7 | No se modifica una orden ya **`EJECUTADA`** | ✅ | `exigirModificable`, en el dominio |

**Siete de siete desde el 2026-09-13** (`0cf56c5`). La regla 1 **faltaba** y se cerró ese mismo día; conserva sección propia más abajo porque el modo en que faltó es más instructivo que la regla.

### La regla 2 no se comprueba: se hace imposible

**La decisión de diseño más importante del módulo.** `AddEquipmentToWorkOrderCommand` es:

```java
public record AddEquipmentToWorkOrderCommand(UUID id, UUID idEquipoCliente) { }
```

**No tiene campo para el área.** La averigua el servicio consultando dónde está el equipo ahora, y la congela. Si el llamante pudiera declararla, podría declarar una donde el equipo no está, y el registro histórico **nacería mintiendo** sobre dónde se prestó el servicio.

Una regla comprobada puede comprobarse mal. Una regla que no se puede expresar no puede violarse. Cuando exista la opción, **quitar el campo gana a validarlo** — y el sitio donde esto se ve es el `record` del comando y el `record` de la petición REST, que tampoco lo lleva.

### La regla 1 faltaba, y cómo se encontró

**Cerrada el 2026-09-13 con `0cf56c5`.** Lo que sigue se conserva porque el hallazgo vale más que el arreglo.

`requireEquipmentBelongsTo` —así se llamaba— recibía la unidad, pedía su área, comprobaba que **el área estuviera abierta**, y después preguntaba por el **cliente dueño** con `findOwningClient`. Lo que **nunca hacía** era comparar el `idSede` de esa área —que el agregado `ServiceArea` ya trae— contra el `idSede` de la orden.

La consecuencia concreta: **una orden del cliente A en la sede Norte admitía un equipo del cliente A que estaba en la sede Sur.** Pasaba las tres comprobaciones —activo, área abierta, mismo cliente— y entraba. La orden decía que el servicio era en Norte y el equipo estaba en otro sitio. **El mismo tipo de mentira histórica** que el módulo entero se diseñó para impedir, un nivel más abajo.

El arreglo salió barato como estaba previsto: el `ServiceArea` ya se cargaba en esa línea para mirar si estaba abierto, así que la sede venía en la mano y **la comprobación no cuesta ninguna consulta más**. El método pasa a llamarse `requireEquipmentInScopeOf` y recibe la orden entera, porque ahora mira dos de sus campos.

#### El orden de las guardas es una decisión, no un detalle

Al añadir la comprobación de sede apareció que **una de las dos podía quedar muerta**: un equipo de otro cliente está *necesariamente* en otra sede —si el área fuera de la sede de la orden, su cliente sería el de la sede, y la clave foránea compuesta garantiza que ése es el de la orden—, así que las dos negativas serían ciertas a la vez.

Se comprueba **el dueño antes que la sede**, y con ese orden las dos siguen siendo alcanzables:

| Caso | Qué responde |
|---|---|
| Equipo de otro cliente | «es del cliente X y la orden es del cliente Y» |
| Equipo del mismo cliente, otra sede | «está en la sede X y el mantenimiento se presta en la sede Y» |

Al revés, el primer caso daría el mensaje de sede —cierto pero menos informativo— y la comprobación de cliente no se alcanzaría nunca. Lo fija la prueba `elClienteSeCompruebaAntesQueLaSede`, con un `withMessageNotContaining`.

**La regla general que deja esto**: cuando una guarda nueva subsume a una vieja, la pregunta no es cuál borrar sino **en qué orden dejarlas para que cada una siga contestando su caso**. Borrar la subsumida pierde el mensaje bueno; dejarla detrás la mata en silencio.

#### Cómo apareció, que es lo reutilizable

**Contrastando esta nota contra el código**, no leyendo el servicio. Es literalmente lo que dice la disciplina del proyecto en `CONVENCIONES.md` —«los defectos aparecen al comparar»— funcionando sobre el propio wiki.

Lo que lo permitió: la lista de siete reglas se escribió en la tanda 1 **sin columna de estado**. Una lista así **se lee como inventario y no como pendiente**, y nadie la volvió a mirar entre que se escribió y que se dio el módulo por cerrado. La tabla de arriba, con su columna «Estado» y su columna «Dónde», existe para eso.

#### Y una prueba que fija un estado imposible

De paso apareció que `equipoDeOtroCliente` —anterior a este arreglo— monta un mundo que no puede existir: un área **en la sede de la orden** pero **de otro cliente**. Sigue en verde porque la guarda de cliente salta primero, pero lo que pincha no es alcanzable por el API. No se tocó; queda anotado.

### El ingeniero puede tener dos tipos

`requireActiveEngineer` acepta que **cualquiera de los dos** tipos de la persona sea `ENGINEER`. El modelo permite `tipoPersona` y `segundoTipoPersona`, de modo que alguien que es a la vez ingeniero y encargado sigue pudiendo ejecutar un mantenimiento. Ver [[relacion-manager-persona]].

### La persistencia concilia, no reemplaza

`WorkOrderPersistenceMapper.sincronizarEquipos` es la pieza con más lógica del módulo fuera del dominio, y no es un mapeo:

- El adaptador **lee la fila existente antes de guardar** y se la pasa al mapper. Construir una entidad nueva en cada guardado haría que Hibernate insertara duplicados del alcance.
- El equipo que ya no está en el agregado **queda inactivo, no desaparece**: la llave compuesta impide volver a insertarlo, y el historial de un equipo —en qué órdenes se le intervino— dejaría de ser cierto si se borrara.
- El que vuelve **reactiva su fila con el área que traiga el agregado**, que para un equipo readmitido es la de ese momento. Es la única forma de corregir un área congelada, y es coherente con que retirarlo y volver a añadirlo sea precisamente eso.
- `toDomain` **solo carga los equipos activos**: el agregado responde «sobre qué se trabaja en esta orden», y los retirados son historial.

## Tanda 4 — el API

`/v1/api/work-orders`, **nueve** operaciones — 2 de lectura, 6 de escritura y 1 de asignación, contadas sobre las anotaciones el 2026-09-13. **No hay operación de cambio general**: una orden no se «edita». Se le añaden o quitan equipos, se le asigna un ingeniero y avanza de estado; cada uno es un hecho distinto, con su ruta, su autoridad y su evento. Un `PATCH` sobre la orden entera los confundiría a todos.

| Operación | Ruta | Autoridad |
|---|---|---|
| Listar (filtros excluyentes) | `GET /` | `work-order.read` |
| Obtener | `GET /{id}` | `work-order.read` |
| Programar | `POST /` → 201 | `work-order.write` |
| Añadir equipo | `POST /{id}/equipments` → 201 | `work-order.write` |
| Retirar equipo | `DELETE /{id}/equipments/{idEquipoCliente}` | `work-order.write` |
| **Asignar ingeniero** | `PATCH /{id}/engineer/{idIngeniero}` | **`work-order.assign`** |
| Empezar · Ejecutar | `PATCH /{id}/start` · `/execute` | `work-order.write` |
| Cancelar | `DELETE /{id}` → 204 | `work-order.write` |

**Que asignar tenga autoridad propia es lo que permite que un coordinador reparta trabajo sin poder alterar lo que se va a hacer.** Sin esa separación, `work-order.write` lo cubriría todo y el permiso no significaría nada distinto. Ver [[modelo-de-permisos]].

Los **cuatro filtros del listado son mutuamente excluyentes** —cliente, sede, ingeniero, equipo— y pasar dos responde 400. Combinarlos exigiría un puerto por combinación, y la respuesta correcta a «¿y si quiero dos?» es una consulta nueva y explícita, no un producto cartesiano implícito.

### El catálogo distingue tres cosas que un solo código confundiría

Ocho códigos, `ERR_WORK_ORDER_001` a `008`. Lo que merece quedar es el reparto, no la lista:

- **404 con código propio por cada referencia externa** —cliente, sede, área, equipo, persona—. El advice de este módulo maneja las excepciones de los cuatro módulos que consulta: llegan por su controlador y de otro modo escaparían como un 500. Es el hueco que se descubrió en la tanda del traslado por no declarar una de ellas. Ver [[patron-catalogo-errores-por-contexto]].
- **409 para el conflicto de estado**, separado del 400 de datos inválidos. Los datos recibidos son válidos y no falta ninguno; lo que choca es el momento — arrancar una orden ya ejecutada, tocar el alcance de una cancelada. Compartir el código de «datos inválidos» impediría al llamante distinguir **«lo has escrito mal»** de **«ahora no se puede»**.
- **400 para las reglas** del agregado y del servicio, que llegan como `IllegalArgumentException`.

Ese 409 es nuevo en el proyecto: los cuatro módulos anteriores no tenían un estado que pudiera chocar.

### La centinela se rompió como estaba previsto

`RestAuthorizationCoverageTest.lasAutoridadesDeWorkOrderSiguenSinModulo` afirmaba que ninguna autoridad `work-order.*` protegía nada. **Falló con el primer controlador del módulo, que es exactamente lo que se le pedía**, y se retiró en el mismo commit junto con el `filter(a -> !a.startsWith("work-order."))` de `ningunaAutoridadSobra`.

La sustituyen dos pruebas: que las tres autoridades protegen ya sus endpoints, y que **`assign` es la única operación que exige `work-order.assign`** — porque si mañana otra la exige, esa separación de permisos deja de existir en silencio.

El patrón de omisión consciente funcionó de punta a punta: se anotó cuándo se rompería, se rompió entonces, y el aviso estaba escrito en `CONVENCIONES.md` y en esta nota. Es el segundo caso del proyecto, tras la centinela de `equipo_cliente` de [[migracion-equipment-hallazgos]].

## La verificación

Todas las cifras son el **número de elementos `<testcase>` de los XML de Surefire**, medidas con `rm -rf target/surefire-reports` **antes** de cada corrida — sin eso se suman informes de ramas anteriores, ver [[stack-spring-boot-4-particularidades]].

| Punto | Pruebas | Clases | Lo que añade |
|---|---|---|---|
| `43de295` (antes del módulo) | 509 | 42 | — |
| `24e7640` (tanda 1) | 541 | 43 | `WorkOrderSchemaTest`: 23 métodos, **32** ejecuciones |
| `main` `97ef74f` (tanda 2) | **580** | 44 | `WorkOrderTest`: **39** |
| `dd625a1` (tanda 3) | 597 ᵈ | 45 | `WorkOrderServiceTest`: **17** ᵈ |
| `ceadba1` (tanda 4) | **612** | 46 | `WorkOrderRestAdapterTest`: **14**, más el neto +1 del centinela sustituido por dos |
| `0cf56c5` (la regla 1) | **614** | 46 | Las dos pruebas de la regla que faltaba |
| `b9563a9` (todo mergeado) | **614** | 46 | Remedido tras los tres merges: el mismo código, la misma cifra |
| `af38d2a` (la persistencia) | **624** | 47 | `WorkOrderPersistenceAdapterTest`: **10**, y un defecto encontrado |

Para `main` y `ceadba1`, las otras dos fuentes de conteo: el atributo `tests=` da **578** y **609**, y los `.txt` **418** y **433**. Los `.txt` **no se movieron entre `24e7640` y `main`** —418 y 418— pese a las 39 pruebas de `WorkOrderTest`, porque esa clase tiene 13 clases `@Nested` y ninguna prueba suelta: esa fuente las ignora por completo. Y el desajuste con el atributo pasa de 2 a 3 porque **`ordenInexistente` aparece en dos `@Nested` de `WorkOrderServiceTest`**, el segundo caso del proyecto tras `CatalogAggregatesTest`. Ver [[stack-spring-boot-4-particularidades]].

Cero fallos, cero errores, cero omitidas en todas.

**Las marcadas con ᵈ son derivadas, no medidas**, y conviene decirlo: se corrieron `24e7640`, `main` y `ceadba1`; el punto intermedio sale de restar. 580 + 17 + 14 + 1 = 612 cuadra exactamente con lo medido, y ese +1 es el centinela que se fue sustituido por dos pruebas. Una suma que no cuadre con una medición es una cifra inventada — este wiki ya tuvo una.

### Las pruebas que más valen

- `ordenConSedeDeOtroClienteFalla` (esquema) — arma dos clientes con sede propia e inserta una orden con el cliente A y la sede de B. Convierte «una orden pertenece a un solo cliente» en algo comprobado por el motor.
- `trasladarElEquipoNoReescribeElAreaCongelada` (esquema) — inserta una fila del puente, **traslada el equipo después**, y comprueba dos cosas a la vez: que el traslado **no falla**, y que el área guardada **sigue siendo la vieja**. Si algún día alguien añade la clave foránea compuesta que «falta», **esta prueba se pone roja y explica por qué no debe estar**. Ver [[congelar-una-referencia-historica]].
- `elAreaNoViajaEnLaPeticion` (REST) — no ejerce una petición: comprueba por reflexión que `WorkOrderEquipmentRequest` tiene **un solo componente**. Es la forma de fijar una regla que consiste en una **ausencia**; una prueba de comportamiento no puede ejercer un campo que no existe.

### Dos verificaciones por mutación

El verde no prueba que una regla se ejerza. Antes de cerrar la tanda 4 se rompió la producción a propósito para ver si algo se quejaba:

| Mutación | Señal |
|---|---|
| `pathsToMatch` → `/workorders/**` | `recursoDocumentado` falla |
| `if (filtros > 1)` → `> 99` | `filtrosExcluyentes` falla |
| Anular la comprobación de sede | `equipoDeOtraSedeDelMismoCliente` falla, **y solo ésa** |
| Intercambiar las guardas de cliente y sede | `elClienteSeCompruebaAntesQueLaSede` falla, y Mockito estricto marca además `findOwningClient` como estubado que sobra |

Producción restaurada en ambos casos. La primera importa especialmente porque el fallo que imita —un grupo de OpenAPI que no casa con ninguna ruta— **no da ningún error**: deja el recurso fuera de Swagger en silencio, y pasó cuatro veces en este proyecto antes de que hubiera pruebas. Ver [[openapi-swagger]].

### Un detalle de método que se repite y conviene copiar

Los valores inválidos de los `CHECK` en `WorkOrderSchemaTest` son **cortos a propósito**. Las tres columnas están dimensionadas justas —`varchar(10)` para `TRIMESTRAL`, `varchar(11)` para `CALIBRACION`, `varchar(12)` para `EN_EJECUCION`—, así que un valor inválido más largo fallaría **por longitud y no por el `CHECK`**, dejando la prueba en verde afirmando algo que no ocurrió. Es una prueba que pasa por el motivo equivocado, primo hermano de la que pasa en vacío de [[regla-traslado-mismo-cliente]].

### La persistencia, y el defecto que apareció al probarla

**Cubierta el 2026-09-13** con `WorkOrderPersistenceAdapterTest`: **10 pruebas** contra un PostgreSQL real. Hasta ese día `grep` sobre `src/test` no devolvía **ni una** mención de `WorkOrderPersistenceAdapter`, `WorkOrderPersistenceMapper` ni `WorkOrderRepository`.

#### El defecto lo destapó la primera llamada directa

La primera ejecución no falló por una aserción sino con **`LazyInitializationException`**: `WorkOrderPersistenceAdapter` **no llevaba `@Transactional`**, y el mapper recorre el alcance, que es una colección perezosa con `open-in-view` desactivado.

**No se veía desde el API** porque `WorkOrderService` siempre abre transacción, así que la falta era invisible mientras nadie llamara al adaptador por su cuenta — y nadie lo hacía, porque no había pruebas. Lo llevan ya los adaptadores de `client` y de `person`, **los otros dos con colecciones propias**; de los tres, éste era el único sin él. Corregido en producción, no rodeado en la prueba: envolver la prueba en una transacción habría descrito el defecto en vez de detectarlo.

**La forma general**: un adaptador que mapea una colección perezosa **necesita su propia transacción**, y el día que no la tiene el fallo no aparece hasta que alguien lo llama fuera de un servicio. Es una dependencia sobre el llamante que el tipo no declara.

#### Qué comprueban las diez

Lo que aportan no es el ida y vuelta —eso lo garantizaría casi cualquier mapeo— sino la **conciliación del alcance**, que es donde vive la lógica:

- **Retirar un equipo deja su fila inactiva, no la borra**, comprobado con `SELECT` sobre `orden_trabajo_equipo`: la fila sigue ahí con `b_estado_activo = false`.
- **Readmitirlo reactiva esa misma fila con el área nueva**, y sigue habiendo **una sola**. Es la única forma de corregir un área congelada.
- **`toDomain` no vuelve a cargar lo retirado**: la fila está, el agregado no la trae.
- **Guardar dos veces no duplica el alcance**, que es exactamente para lo que el adaptador lee la fila existente antes de guardar.
- **Un traslado del equipo no reescribe el área que la orden congeló**, comprobado ahora **desde el lado de la orden** — la prueba de esquema ya lo miraba desde el otro.
- **`findByEquipment` no devuelve la orden de la que el equipo salió**, que es lo que justifica su `@Query` escrito a mano.

**Las comprobaciones van contra la tabla con SQL directo a propósito.** Preguntarle al agregado si el equipo está lo contestaría el mapper, que es la pieza bajo prueba.

#### Verificadas por mutación

| Mutación | Qué se puso en rojo |
|---|---|
| El adaptador deja de leer la fila existente | Las tres de retirada y la de consulta por equipo |
| El `@Query` deja de filtrar por `estadoActivo` | `buscarPorEquipoIgnoraLoRetirado`, **solo ésa** |
| `sincronizarEquipos` no desactiva la fila que salió | Las tres de retirada |
| El equipo readmitido no toma el área nueva | `readmitirloReactivaConElAreaNueva`, **solo ésa** |

#### Lo que sigue sin cubrir en el proyecto

`equipment` **tampoco tiene** pruebas de su persistencia salvo `ClientEquipmentRelocationPersistenceTest`. La ausencia que este módulo acaba de cerrar sigue viva en el vecino. Anotado en [[deuda-tecnica-y-riesgos]].

## Lo que falta

**El módulo no tiene ya nada sin cubrir.** Lo que queda es de fuera:

1. **El consumidor de los eventos**, que sigue sin aparecer. Se esperaba que fuera el reporte de servicio; se construyó el 2026-09-27 y **no los escucha**. Los siete se emiten y nadie los recoge.
2. ~~**El formulario**, que es lo que cierra los cuatro RF que siguen abiertos.~~ **Construido el 2026-09-27**: cierra tres, y RF-04 queda como desviación consciente.

~~La regla 1~~ y ~~las pruebas de persistencia~~ — **cerradas las dos el 2026-09-13**, ver arriba.

## Notas relacionadas

[[congelar-una-referencia-historica]] · [[hoja-de-ruta-producto]] · [[decisiones-tecnicas-malphasos]] · [[regla-traslado-mismo-cliente]] · [[dominio-equipo-mantenimiento]] · [[dominio-cliente]] · [[dominio-reporte-servicio]] · [[dominio-reportes]] · [[esquema-bd-v4]] · [[reglas-de-negocio-en-el-esquema]] · [[patron-soft-delete]] · [[modelo-de-permisos]] · [[deuda-tecnica-y-riesgos]] · [[stack-spring-boot-4-particularidades]] · [[openapi-swagger]] · [[evolucion-arquitectonica-crud-a-cqrs]] · [[aggregate-root-pattern]] · [[eventos-de-dominio]] · [[patron-event-dispatcher-dual]] · [[patron-catalogo-errores-por-contexto]] · [[traduccion-de-fallos-de-adaptadores]] · [[relacion-manager-persona]] · [[migracion-equipment-hallazgos]]
