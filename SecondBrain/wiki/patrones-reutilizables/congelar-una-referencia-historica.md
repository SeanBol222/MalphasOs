---
name: congelar-una-referencia-historica
description: Cuando una columna guarda donde estaba algo y no donde esta, la clave foranea compuesta que parece faltar bloquearia el cambio legitimo; por que, y como fijarlo con una prueba
tags: [patron, esquema, integridad-referencial, postgresql, historico, "describe:malphasos"]
source: malphasos/src/main/resources/db/migration/V6__work_order.sql
estado: estable
updated: 2026-09-12
---

# Congelar una referencia histórica, y la clave foránea que no hay que añadir

## El caso concreto

`orden_trabajo_equipo` —la tabla puente entre una orden de trabajo y sus equipos— guarda tres columnas:

```sql
k_id_orden_trabajo  uuid NOT NULL,
k_id_equipo_cliente uuid NOT NULL,
k_id_area_servicio  uuid NOT NULL,   -- donde estaba el equipo al seleccionarlo
```

La tercera es **una copia congelada**: el área en la que estaba el equipo **cuando se añadió a la orden**, no la de hoy. `equipo_cliente` ya tiene su propia `k_id_area_servicio`, que es la actual y **puede cambiar**: trasladar una unidad a otra área es una operación normal del dominio, con evento propio, ver [[regla-traslado-mismo-cliente]].

Sin la copia, un traslado reescribiría dónde se prestó un servicio que ya ocurrió.

## La restricción que parece faltar, y por qué no debe estar

Quien lea ese esquema pensará —de buena fe, y es un buen reflejo— que falta esto:

```sql
-- NO añadir
FOREIGN KEY (k_id_equipo_cliente, k_id_area_servicio)
    REFERENCES equipo_cliente (k_id_equipo_cliente, k_id_area_servicio)
```

Parece la restricción correcta: garantizaría que el equipo **estaba de verdad en esa área** cuando se insertó la fila. Y en el momento del `INSERT` haría justo eso.

**El problema no es el `INSERT`, es el `UPDATE` del otro lado.** PostgreSQL comprueba una clave foránea en dos momentos: al insertar o actualizar la fila **referenciante**, y al actualizar o borrar la fila **referenciada**. Con la acción por omisión, `NO ACTION`:

```sql
UPDATE equipo_cliente SET k_id_area_servicio = <otra> WHERE k_id_equipo_cliente = <ese>;
-- ERROR: update or delete on table "equipo_cliente" violates foreign key constraint
```

Es decir: **una orden vieja bloquearía un traslado legítimo**, y lo haría con más fuerza cuanto más historial acumulara el sistema. La restricción que protege el pasado impediría el futuro.

Verificado contra un PostgreSQL real, no razonado sobre el papel: `WorkOrderSchemaTest.trasladarElEquipoNoReescribeElAreaCongelada` ejecuta ese `UPDATE` y exige que **no lance**, y después lee la fila del puente y exige que el área guardada **siga siendo la vieja**.

## La forma general

**Si una columna guarda *dónde estaba* algo en vez de *dónde está*, no es una referencia al estado actual y no se puede validar contra él de forma permanente.** Sirve para cualquier par de este tipo: el área de un equipo, el precio de un producto en una factura, la dirección de un cliente en un envío.

La consecuencia práctica:

| Qué se quiere garantizar | Dónde va | Por qué |
|---|---|---|
| Que el área **existe** | Clave foránea simple contra `area_servicio` | El área no deja de existir: [[patron-soft-delete]], aquí nada se borra |
| Que el área era **la del equipo en ese momento** | **En el servicio**, al añadir el equipo a la orden | Es una condición del instante de la inserción, no un invariante permanente. Es la regla 2 de las siete de [[dominio-orden-trabajo]] |
| Que el área **no se reescriba** después | Que nadie la actualice: no hay operación que lo haga | Congelada por ausencia de escritura, no por restricción |

## Lo que hace falta para que esto no se «arregle»

Un comentario en el SQL no basta: sobrevive hasta el primer refactor que lo lea por encima. **Lo que lo sostiene es la prueba**, y por eso está escrita como está:

> Si algún día alguien añade la clave foránea compuesta que «falta», `trasladarElEquipoNoReescribeElAreaCongelada` **se pone roja**, y su nombre y su comentario explican por qué no debe estar.

Es el mismo mecanismo que la prueba centinela de `equipo_cliente` ([[migracion-equipment-hallazgos]]) y que el `@PreAuthorize` vigilado por reflexión de [[modelo-de-permisos]]: **una decisión de omisión deliberada solo se defiende con una prueba que falle si alguien la deshace**. Un comentario documenta; una prueba avisa.

## El contraste que conviene tener al lado

En la misma migración, la **misma** familia de restricción sí se añade y sí es correcta:

```sql
FOREIGN KEY (k_id_sede, k_id_cliente) REFERENCES sede (k_id_sede, k_id_cliente)
```

La diferencia no es la forma sino **qué puede cambiar en la fila referenciada**: la sede de un cliente **no cambia de cliente nunca** —no existe operación para ello, y sería otra sede—, mientras que el área de un equipo **cambia por diseño**. Una clave foránea compuesta es segura exactamente cuando la pareja de columnas del otro lado es inmutable.

**Ésa es la pregunta que hay que hacerse antes de escribir una clave foránea compuesta**: ¿alguna de las columnas referenciadas cambia durante la vida de la fila? Si sí, la restricción no valida datos, congela el otro lado.

## Notas relacionadas

[[dominio-orden-trabajo]] · [[regla-traslado-mismo-cliente]] · [[reglas-de-negocio-en-el-esquema]] · [[patron-soft-delete]] · [[migracion-equipment-hallazgos]] · [[esquema-bd-v4]] · [[deuda-tecnica-y-riesgos]]
