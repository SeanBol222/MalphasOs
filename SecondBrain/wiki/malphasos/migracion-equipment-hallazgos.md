---
name: migracion-equipment-hallazgos
description: Lo que apareció al migrar el núcleo de negocio en cinco pasos, incluido un booleano que el dominio no sabía derivar y una tabla cuyo nombre miente
tags: [malphasos, migracion, equipment, hallazgos, "reusable:media", "describe:ambos"]
source: Backend/sigma-bb/src/main/java/com/sigma/bb/equipment_hexagon
estado: estable
updated: 2026-09-02
---

# Migración de equipment: hallazgos

El último módulo y el más grande —173 archivos en el original—. Se cerró el 2026-09-02 en cinco pasos, y con él termina la migración del backend: `person`, `location`, `client` y `equipment` cubren todo el alcance de [[alcance-malphasos]].

Como `client`, se **reconstruyó en vez de portarse**: el original es Generación 1 y este wiki lo marca como patrón a no replicar ([[evolucion-arquitectonica-crud-a-cqrs]]). Se conservaron los conceptos y los nombres de tabla; todo lo demás se rehízo.

**Alcance de esta tanda:** el catálogo y el inventario. Los datos metrológicos y las verificaciones técnicas que describe [[dominio-equipo-mantenimiento]] **no entran todavía** y son la segunda tanda del módulo.

## Los cinco pasos

| # | Commit | Qué entró | Pruebas |
|---|---|---|---|
| 1 | `25fae09` | `V5__equipment_catalog.sql`: seis tablas, más `equipo_cliente` | 285 |
| 2 | `82bd2d9` | Seis agregados de Generación 2 | 310 |
| 3 | `a7e0051` | Aplicación y persistencia de fabricante, marca y tipo | 317 |
| 4 | `88a0eff` | Aplicación y persistencia de equipo, modelo y unidad | 326 |
| 5 | `b8fada6`…`8ae9f98` | Capa REST, más tres correcciones de la revisión | 338 |

## El hallazgo de modelado que más aporta

**`EquipmentType` ya no tiene un campo `verificable`: se deriva de si consta la modalidad de verificación.** Un tipo es verificable exactamente cuando se sabe cómo verificarlo.

El original tenía un booleano `b_verificable` y una columna `n_tipo_verificacion` en la tabla, **sin nada que los atara**, y el dominio ni siquiera modelaba la segunda. Cabía un tipo marcado como verificable del que nadie sabía cómo se verifica, y un tipo no verificable con modalidad declarada. Dos columnas describiendo el mismo hecho, libres de contradecirse.

La corrección va por tres sitios a la vez, y es el ejemplo más limpio del proyecto de una invariante defendida en capas:

- **El dominio** no expone el booleano como campo. `isVerificable()` es un método que devuelve `modalidadVerificacion != null`. El estado inconsistente deja de ser expresable.
- **El esquema** añade un `CHECK` que ata las dos columnas en ambos sentidos.
- **El mapper de persistencia** deriva el booleano al guardar y lo **ignora al leer**. La columna sobrevive porque la tabla se conserva, pero deja de ser una fuente de verdad.

Hay pruebas de los dos casos contra PostgreSQL real, y una que declara la modalidad, la persiste, la quita y vuelve a leer.

## La tabla cuyo nombre miente

**`equipo` no guarda un equipo.** No tiene un solo atributo propio más allá de sus dos claves foráneas: es la asociación entre una marca y un tipo, es decir *qué tipos fabrica cada marca*. Un equipo físico es `equipo_cliente`; lo que un catálogo llamaría "producto" es `modelo`.

Se conserva el nombre para no divergir del sistema del que se migra, y queda documentado en el esquema, en el agregado y en el controlador. Se le añade unicidad del par, porque repetirlo no significa nada.

**Sus dos referencias son inmutables.** El original ofrecía `updateEquipment` y `updateEquipmentPatch`; cambiar cualquiera de las dos habría convertido en mentira todos los modelos colgados de esa asociación. `EquipmentServicePort` no declara operación de cambio, el controlador no expone `PATCH`, y una prueba fija ese **405 como contrato, no como omisión**. Si la asociación está equivocada se retira y se crea la correcta.

## Qué cambia y qué no, en el resto de agregados

La pregunta se hizo agregado por agregado, y las respuestas están en los puertos:

| Agregado | Cambia | No cambia | Por qué |
|---|---|---|---|
| `Manufacturer` | nombre, país | — | |
| `Brand` | nombre | — | |
| `EquipmentType` | características, modalidad | — | la modalidad va aparte: cambia lo que el tipo *es* |
| `Equipment` | **nada** | tipo, marca | volvería mentira los modelos que cuelgan |
| `Model` | registro INVIMA | fabricante, equipo | el INVIMA se tramita después del alta |
| `ClientEquipment` | datos de compra, área | **modelo** | una unidad no se convierte en otra cosa |

El **traslado de una unidad tiene evento propio**, `ClientEquipmentRelocatedEvent`, separado del cambio de datos de compra. Los equipos se mueven dentro de una sede y eso cambia quién responde por ellos: es el hecho que más importa de una unidad.

## Seis defectos del esquema original

Todos corregidos en `V5__equipment_catalog.sql`:

1. **`d_amperaje` era `numeric(2)`** — escala cero y máximo 99. Un amperaje de 2.5 A se redondeaba a 3 y uno de 120 A no cabía. Pasa a `numeric(8,2)`. Hay una prueba que fija que un 2.50 sobrevive el viaje de ida y vuelta.
2. **`n_nombre_marca` era anulable**, y el nombre es lo único que una marca tiene.
3. **El fabricante y el equipo de un modelo eran anulables**: cabía un modelo que no pertenecía a nada.
4. **El modelo y el área de servicio de una unidad eran anulables**: cabía un equipo del que no se sabía qué era ni dónde estaba.
5. **`b_verificable` y `n_tipo_verificacion` sueltas** — el hallazgo de arriba, atado ahora por `CHECK`.
6. **No había unicidad en ninguna tabla del catálogo.** Se añaden las de nombre de fabricante, marca y tipo, la del registro INVIMA de un modelo y la del número de serie dentro de un modelo. El INVIMA admite varios nulos bajo la restricción, de modo que los modelos sin registro conviven.

Y en el dominio: `Equipment` guardaba cada referencia **dos veces**, como identificador y como objeto completo, sin nada que mantuviera ambos al día. Aquí solo hay identificadores, como manda la convención del proyecto.

Validaciones que el original no hacía: voltaje y amperaje positivos, valor de mantenimiento no negativo, y que un equipo no se compró en el futuro.

## La prueba centinela que se rompió a propósito

`V4__client.sql` dejó `equipo_cliente` fuera porque su clave foránea apunta a `modelo`, que no existía todavía. Para que no se olvidara, `ClientSchemaTest` llevaba una prueba que **fijaba que la tabla no existía**.

Al aparecer la tabla, esa prueba se rompió — que es exactamente lo que se le pedía. Se retira y la reemplaza una que comprueba que sus dos referencias son obligatorias.

Es un patrón que vale la pena repetir: una omisión consciente se marca con una prueba que falla cuando deja de ser una omisión, en vez de con un comentario que nadie relee.

## La primera vez que la dirección se invierte

`ClientEquipmentService` consulta al módulo de clientes por el área de servicio. Hasta ahora `client` consultaba a `person` y a `location`; **ahora es consultado**. No hay ciclo, porque `client` no conoce a `equipment`.

El mapa de dependencias entre módulos queda así:

```
person  ←  client  ←  equipment
             ↓           ↓
          location ← ────┘
```

`ManufacturerService` consulta `CountryServicePort` porque el país de un fabricante es opcional pero, si viene, tiene que existir.

## Tres invariantes más que ninguna clave foránea puede defender

Siguen la línea que abrió `client` ([[migracion-client-hallazgos]]): comprueban que algo esté **activo**, no solo que exista. Con borrado lógico, esas dos cosas dejan de ser la misma, y una clave foránea solo sabe de la segunda.

- No se registra un modelo sobre una asociación marca-tipo retirada.
- No se incorpora una unidad de un modelo retirado.
- No se instala ni se traslada un equipo a un área de servicio cerrada.

Con las dos de `client` van **cinco invariantes de este tipo** en el proyecto. Todas viven en los servicios, con sus pruebas.

**La unicidad del par marca-tipo se deja al esquema**, deliberadamente: comprobarla en el servicio solo abriría una ventana entre la consulta y la escritura. La regla que la base puede defender sin carreras, la defiende la base.

## Lo que apareció al revisar la capa REST

Tres cosas que el código compilaba y las pruebas no cubrían. Las dos primeras las encontró comparar este módulo con `location` y `client`; la tercera, leer el catálogo de errores en voz alta.

**Los grupos de OpenAPI no casaban con las rutas.** `OpenApiConfig` se escribió cuando se portó la configuración transversal, mucho antes que estos controladores, y sus patrones pedían `/equipment`, `/equipment-models` y `/client-equipment` mientras las rutas publicadas son `/equipments`, `/models` y `/client-equipments`. **Tres de los seis recursos no aparecían en ningún grupo de Swagger.**

El fallo no avisa: un patrón que no casa con ninguna ruta no es un error, simplemente deja el recurso fuera de la documentación. Corregido en `e2e3c8e`, con una prueba que exige los seis recursos en `/v3/api-docs/equipment` y una advertencia en el javadoc de la clase para quien añada el próximo grupo. Ver [[openapi-swagger]].

**Tres `PUT` donde la convención dice `PATCH`.** Las rutas de sub-recurso —modalidad, INVIMA, traslado— llegaron como `PUT`. El argumento a favor era bueno: reemplazan por completo el sub-recurso y son idempotentes. No compensa: dos verbos de escritura con la misma semántica repartidos por el API según quién escribiera cada controlador cuestan más que la precisión del matiz. Un solo verbo hace que la regla se enuncie sin excepciones.

**Un código de error que servía para dos cosas incompatibles.** Un área de servicio inexistente respondía 404 con `ERR_EQUIPMENT_007`, *"Invalid equipment data"* — el mismo código que sale con **400** cuando una regla del servicio rechaza la petición. El estado decía "no existe" y el código decía "datos inválidos"; un cliente que solo mirase el código no podía distinguirlos. Ahora el país y el área llevan código propio, 008 y 009.

**`client` tenía el mismo defecto**, agrupando `CityNotFoundException` y `PersonNotFoundException` bajo `INVALID_CLIENT_DATA`. Se creyó al principio que `client` ya lo hacía bien y que `equipment` se desviaba del patrón; era al revés. **Corregido el mismo día** con `ERR_CLIENT_006` y `007`, de modo que los dos módulos que hablan con otros tratan ahora sus referencias externas igual.

## Notas relacionadas

[[dominio-equipo-mantenimiento]] · [[migracion-client-hallazgos]] · [[decisiones-tecnicas-malphasos]] · [[deuda-tecnica-y-riesgos]] · [[checklist-reutilizacion]] · [[reglas-de-negocio-en-el-esquema]] · [[patron-soft-delete]]
