---
name: dominio-reporte-servicio
description: El modulo de reportes de servicio de MalphasOS, completo en sus cuatro tandas el 2026-09-27; uno por equipo de la orden, con sus seis reglas cruzadas y el choque de orden entre Hibernate y un indice unico parcial
tags: [dominio, reportes, esquema, mantenimiento, "describe:malphasos"]
source: malphasos/src/main/java/com/malphasos/malphasos/report/ y malphasos/src/main/resources/db/migration/V9__service_report.sql
estado: estable
updated: 2026-09-27
---

# Reportes de servicio — el módulo completo

**Estado**: las **cuatro tandas** construidas y mergeadas el **2026-09-27** —`12964cba`, `d5f12304`, `e3889b67`, `ac0233b9`—. El módulo se usa de extremo a extremo desde el API.

**No confundir con [[dominio-reportes]]**, que describe el `reports_hexagon` del **original**: aquello era un agregador que juntaba datos de varios hexágonos para pintar un árbol, y no tiene nada que ver con esto. Aquí un reporte es **una entidad de negocio con su propio ciclo de vida**: lo que se hizo sobre un equipo. El patrón `ReportDataProviderPort` del original **no se usó**, y sigue disponible el día que haga falta un reporte que agregue módulos —el PDF de RF-17, probablemente—.

| Tanda | Commit | Qué trae |
|---|---|---|
| 1 · Esquema | `12964cba` | `V9__service_report.sql` y `ServiceReportSchemaTest`, 33 pruebas |
| 2 · Dominio | `d5f12304` | `ServiceReport` con cinco eventos, `VerificationReading`, 34 pruebas |
| 3 · Aplicación y persistencia | `e3889b67` | Las seis reglas cruzadas, el adaptador y 37 pruebas —24 de servicio, 13 contra PostgreSQL real— |
| 4 · REST | `ac0233b9` | Seis operaciones, dos autoridades nuevas y 20 pruebas de contrato HTTP |

## Qué es un reporte, y por qué hay uno por equipo

La orden dice **qué se va a hacer**; el reporte dice, equipo por equipo, **qué se encontró, qué se hizo y cómo quedó**. RF-09 lo pide con esas palabras —«un reporte de mantenimiento individual por cada equipo seleccionado dentro de una orden de trabajo»— y coincide con lo que ocurre en campo: cada equipo se interviene y se entrega por separado.

```
orden_trabajo_equipo ──> reporte_servicio ──> dato_verificacion ──> punto_verificacion
  (que se iba a hacer)     (que se hizo)        (que se midio)      (donde se mide, V8)
```

**La llave foránea es compuesta contra el puente**, no dos sueltas hacia `orden_trabajo` y `equipo_cliente`. Con dos sueltas cabría un reporte del equipo A en una orden que nunca lo incluyó: dos filas que por separado existen y juntas no significan nada. Contra el puente lo impide el esquema solo, y **sale gratis** porque el par ya es su llave primaria. Es la misma técnica de `UQ_sede_identidad_con_cliente` en `V6`.

**El reporte no copia cliente, sede, tipo de servicio ni ingeniero**, y eso *es* RF-11. El requisito pide autocompletar esos datos «a partir de la información registrada en la orden de trabajo»; consultarlos por el identificador de la orden es autocompletarlos, y la orden ya los congeló al crearse —por eso `V6` los guarda de forma redundante—. Una tercera copia habría que mantenerla de acuerdo con las otras dos. **El original sí copiaba `k_id_cliente` aquí, y tenía el vínculo con la orden roto**: `k_id_orden_trabajo varchar(10)` contra un `uuid`.

## Los cinco campos de RF-15 y los dos que hacen falta para cerrar

`t_falla_reportada`, `t_diagnostico`, `t_procedimientos`, `t_observaciones` y `t_resultado`. **Los cinco son nulos en `BORRADOR`**: el reporte se abre vacío al llegar al equipo y se llena allí. Al cerrarlo se exigen **dos** —procedimientos y resultado, que son qué se hizo y cómo quedó—; falla y diagnóstico se quedan opcionales incluso al cerrar, porque un preventivo que sale bien no tiene ninguna de las dos.

En `fill(...)`, **un nulo deja el campo como está y un texto en blanco lo borra**. Son dos intenciones distintas y las dos hacen falta: el formulario manda solo lo que se tocó, y un campo escrito por error tiene que poder vaciarse. La distinción no existe para el resultado, que es una enumeración: ahí un nulo solo puede significar «no lo cambies».

### ⚠️ El vocabulario de `t_resultado` no sale de la ERS

`OPERATIVO`, `OPERATIVO_CON_RESTRICCIONES`, `FUERA_DE_SERVICIO`. La ERS dice «resultado» y **no enumera valores**, a diferencia de las periodicidades y los tipos de servicio de `V6`, que sí estaban escritos en el documento. Los tres son una **propuesta**, y va como catálogo cerrado y no como texto libre porque de ese dato cuelgan el historial de la hoja de vida (RF-26) y las alertas (RF-40): un texto libre no se agrupa ni dispara nada. **Mientras no haya datos, cambiar la lista es una línea**; después es una migración. Está registrado en [[deuda-tecnica-y-riesgos]].

## La verificación: V8 configura, V9 registra

`V8` dejó dicho **dónde** se verifica un tipo de equipo —`punto_verificacion`— y **cuántas lecturas** se toman en cada punto —`tipo_equipo.i_cantidad_datos`—, y su cabecera decía que eso era «con qué se verifica, no el resultado de haberlo hecho». `dato_verificacion` es ese resultado, y vive con el reporte porque se mide durante el servicio y se imprime en el reporte de ese servicio.

Tres decisiones de esa tabla:

- **El punto es anulable.** Con `patron_equipo_variable` el tipo no declara puntos —lo impone un `CHECK` desde `V8`— y las lecturas existen igual. Nulo significa «esta lectura no corresponde a ningún valor constante declarado», no «falta el dato».
- **Se guardan las dos lecturas**, incluso la del lado que debería ser constante. Lo constante lo es por cómo se monta el ensayo: si el patrón marcó 50,2 donde el punto dice 50, el reporte tiene que decir 50,2. Y deducirla del punto obligaría a consultar una configuración que puede haber cambiado — el punto se retira, no se edita, justamente para que los reportes viejos sigan cuadrando. Ver [[congelar-una-referencia-historica]].
- **La unidad viaja en la lectura**, copiada del punto al tomar el dato. Sin ella no hay número que imprimir, y con modalidad variable no hay punto de donde sacarla.

**`NULLS NOT DISTINCT` en el índice único no es un adorno.** Por defecto PostgreSQL considera que dos `NULL` son distintos, así que sin esa cláusula la modalidad variable —la única que deja el punto nulo— sería **la única que admitiría lecturas duplicadas**, que es justo al revés de lo que se quiere.

## Las seis reglas que viven en el servicio

El esquema sostiene una sola de las cruzadas, y ni esa entera: la foránea compuesta ve que la fila del puente **existe**, no que siga **activa**. Las seis están en `ServiceReportService`:

| Regla | Por qué no cabe en otro sitio |
|---|---|
| La orden no está en `CREADA` | Hay que leer el estado de otro agregado. Un reporte cuenta lo que se hizo, y en una orden sin empezar no se ha hecho nada |
| La orden no está cancelada | Lo mismo |
| El equipo sigue en el alcance **vivo** | El borrado lógico: la foránea no distingue «existe» de «sigue en uso». Tercera vez que esta distinción sube una regla al servicio, tras `client` y `equipment` |
| No hay ya un reporte vivo para ese par | Lo impide el índice único parcial, pero el servicio lo convierte en una frase que **dice cuál es el reporte que estorba** |
| Las lecturas cuadran con el tipo | Cuatro comprobaciones: que el tipo se verifique, que el punto sea **activo de ese tipo** —la foránea solo comprueba que el punto exista—, que el número no pase de las lecturas declaradas, y que la unidad salga del punto |
| Al cerrar, la verificación está completa | Exige recorrer los puntos del tipo, que está en otro módulo |

**Una orden ya `EJECUTADA` sí admite abrir reportes**, y es deliberado: en este negocio se va a la sede y se registra después. Lo que no se admite es reportar antes de empezar.

**La excepción de la última regla es el caso normal de un equipo averiado**: si el resultado es `FUERA_DE_SERVICIO`, no se exigen lecturas. A un equipo que no enciende no se le puede tomar una, y exigirlas obligaría a inventárselas para poder cerrar el reporte que precisamente dice que está averiado.

### La unidad no se comprueba, se hace imposible de contradecir

Cuando la lectura declara un punto, `VerificationReadingCommand` **no acepta su unidad**: el servicio la copia del punto. Si el llamante la declarase, podría declarar «°C» en un punto medido en mmHg y el reporte saldría impreso con una unidad que nadie midió. Es la misma técnica que la orden de trabajo usa con el área de un equipo, que tampoco viene en el comando.

### Para saber cómo se verifica un equipo hay que caminar cuatro eslabones

`equipo_cliente` → `modelo` → `equipo` del catálogo → `tipo_equipo`. Así está construida la cadena del catálogo —«equipo» significa categoría y máquina, ver [[dominio-equipo-mantenimiento]]— y ninguna tabla intermedia guarda un atajo hacia el tipo. Está en un solo método, `tipoDelEquipo`, para que el coste esté a la vista y para saber dónde poner el atajo el día que haga falta.

## El hallazgo: Hibernate vacía los INSERT antes que los UPDATE

**Lo encontró la prueba de persistencia antes de que pasara ninguna vez**, y no habría aparecido nunca con dobles.

Corregir una lectura retira la vieja —misma fila, `b_estado_activo` a falso— e inserta la nueva. Hibernate ordena su descarga por categorías y **los `INSERT` van antes que los `UPDATE`**, de modo que la base ve por un instante **dos lecturas activas del mismo punto con el mismo número** y `UQ_dato_verificacion_activo` salta.

**Un índice único parcial no se puede declarar diferido en PostgreSQL**: `DEFERRABLE` es de las restricciones (`ADD CONSTRAINT`), y una restricción no admite `WHERE`. Así que el orden hay que imponerlo en el adaptador: se vuelca lo que ya existe, se vacía la sesión con `saveAndFlush`, y solo entonces se insertan las lecturas nuevas. **Un reporte nuevo no paga ninguna escritura extra**, porque sin fila previa no hay nada que vaciar antes.

Es el mismo tipo de choque que el proyecto ya conocía por otro lado —«un adaptador que mapea una colección perezosa lleva `@Transactional`»—, y la lección general es la misma: **el orden en que una ORM escribe no es el orden en que el código lo dice**.

## Lo que este módulo decidió no hacer

- **No hay `findAll`.** Un reporte vive dentro de su orden o dentro del historial de su equipo; «todos los reportes del sistema» no responde a ninguna pregunta del dominio. El listado del API **exige** uno de los dos filtros.
- **No hay operación para reabrir un reporte cerrado**, y hay dos pruebas que lo vigilan —una por reflexión sobre el agregado y otra sobre la ruta—. Un reporte cerrado es lo que se entregó al cliente; corregirlo es retirarlo y abrir otro, que es justo lo que permite el índice único parcial.
- **No hay una tercera autoridad para cerrar.** Quien llena el reporte es quien lo firma en campo: separarlas describiría un reparto de trabajo que en esta empresa no existe. La firma digital (RF-21) traerá la suya. Ver [[modelo-de-permisos]].
- **No hay protocolos** (RF-13, RF-14). Siguen sin existir y son la pieza que falta para que el reporte se cargue solo con lo que hay que revisar.

## Qué cierra y qué no de la ERS

**Cierra RF-09** —un reporte por equipo, asociado a los dos, accesible desde la orden— y **RF-15** —los cinco campos, guardados de forma independiente y consultables después—.

**No cierra RF-11**, y conviene ser estricto: el backend garantiza que esos datos **no se pueden teclear** —no hay columna ni campo para ellos— y que son consistentes, porque hay una sola fuente. Pero el requisito dice que «los campos del reporte muestran automáticamente» esos datos, y mostrar es una pantalla. Queda pendiente del frontend, con el mismo criterio que dejó cuatro requisitos de la orden de trabajo esperando su formulario.

## Notas relacionadas

[[dominio-orden-trabajo]] · [[dominio-equipo-mantenimiento]] · [[dominio-reportes]] · [[congelar-una-referencia-historica]] · [[modelo-de-permisos]] · [[deuda-tecnica-y-riesgos]] · [[hoja-de-ruta-producto]] · [[patron-catalogo-errores-por-contexto]] · [[openapi-swagger]]
