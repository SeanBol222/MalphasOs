---
name: dominio-reporte-servicio
description: El modulo de reportes de servicio de MalphasOS, backend completo el 2026-09-27 y frontend el 2026-09-28; uno por equipo de la orden, con sus seis reglas cruzadas, el choque de orden entre Hibernate y un indice unico parcial, y las cuatro pantallas que cierran RF-11
tags: [dominio, reportes, esquema, mantenimiento, "describe:malphasos"]
source: malphasos/src/main/java/com/malphasos/malphasos/report/, malphasos/src/main/resources/db/migration/V9__service_report.sql y malphasos-frontend/src/app/features/report/
estado: estable
updated: 2026-10-04
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

## La verificación: el catálogo configura, el reporte registra

El módulo de equipos dice **qué** se verifica, **en qué valores** y **cuántas lecturas** por valor;
`dato_verificacion` guarda el resultado, y vive con el reporte porque se mide durante el servicio y se
imprime en el reporte de ese servicio.

(Este apartado se llamaba «V8 configura, V9 registra» y situaba la cantidad de lecturas en
`tipo_equipo.i_cantidad_datos`: cierto hasta el **2026-10-03**, cuando `V10` bajó esos datos a cada
verificación del tipo. Ver [[dominio-equipo-mantenimiento]].)

Cuatro decisiones de esa tabla:

- **La verificación es obligatoria, y entró el 2026-10-03.** Es lo único que dice **qué se midió**
  cuando no hay punto. Ver más abajo: es un defecto que el cambio de ese día creaba.
- **El punto es anulable.** Con `patron_equipo_variable` no hay puntos declarados y las lecturas
  existen igual. Nulo significa «esta lectura no corresponde a ningún valor constante declarado», no
  «falta el dato».
- **Se guardan las dos lecturas**, incluso la del lado que debería ser constante. Lo constante lo es por cómo se monta el ensayo: si el patrón marcó 50,2 donde el punto dice 50, el reporte tiene que decir 50,2. Y deducirla del punto obligaría a consultar una configuración que puede haber cambiado — el punto se retira, no se edita, justamente para que los reportes viejos sigan cuadrando. Ver [[congelar-una-referencia-historica]].
- **La unidad viaja en la lectura**, copiada de su verificación al tomar el dato. Sin ella no hay número
  que imprimir, y sigue siendo una copia y no una foránea para que reconfigurar un tipo no cambie un
  reporte ya firmado.

**`NULLS NOT DISTINCT` en el índice único no es un adorno.** Por defecto PostgreSQL considera que dos `NULL` son distintos, así que sin esa cláusula la modalidad variable —la única que deja el punto nulo— sería **la única que admitiría lecturas duplicadas**, que es justo al revés de lo que se quiere.

### Un defecto que el cambio del 2026-10-03 creaba, y se arregló en la misma pasada

El índice de `V9` era `(reporte, punto, secuencia)` con `NULLS NOT DISTINCT`, y **era correcto
entonces**: había una sola modalidad por tipo de equipo, de modo que una lectura sin punto solo podía
pertenecer a la única verificación que existía.

En cuanto un termohigrómetro puede verificar temperatura **y** humedad las dos con patrón y equipo
variables, las dos producen lecturas sin punto: **la número 1 de temperatura y la número 1 de humedad
chocaban entre sí** aunque midieran cosas distintas, y antes de chocar eran indistinguibles — el reporte
no sabía en qué columna imprimirlas.

`V10` mete la verificación en la clave del índice y la hace `NOT NULL` en la tabla. Hay una prueba de
esquema que fija el caso que antes fallaba: dos lecturas sin punto, con el mismo número, en
verificaciones distintas, **pasan**.

**Lo que esto enseña sobre el índice de V9**: no estaba mal escrito, estaba escrito contra un modelo que
cambió. Un índice único codifica una afirmación sobre qué cosas son la misma cosa, y esa afirmación
caduca cuando el modelo que la sostiene se mueve. Es la primera vez en este proyecto que un índice hay
que rehacerlo por eso y no por un descuido.

### Lo que el punto discrimina y lo que no (2026-10-04)

**El filtro por verificación de `requireCompleteVerification` solo importa cuando no hay puntos**, y
saberlo cambia cómo se prueba la regla.

Con una modalidad **constante**, cada lectura lleva su punto, y los puntos de una verificación no son
los de otra: el identificador del punto **ya separa** las lecturas de temperatura de las de presión.
Quitar el filtro por verificación no cambia el resultado en ese caso.

Con **patrón y equipo variables** no hay punto. La comprobación se reduce a «esta verificación tiene al
menos una lectura», y sin filtrar por verificación **la lectura de una magnitud satisface a la otra**:
el reporte se cerraría afirmando que se verificó algo que nadie midió.

**Lo descubrió una mutación, y en dos pasos.** Se quitó el filtro y la batería siguió verde. Se escribió
una prueba con dos magnitudes constantes y **la mutación siguió viva**. Hizo falta el caso de dos
verificaciones variables con lecturas de una sola. La lección vale más que el caso: **una prueba del
caso correcto por el camino equivocado se lee igual que una buena**, y lo único que distingue las dos es
ver fallar la mutación.

## Las seis reglas que viven en el servicio

El esquema sostiene una sola de las cruzadas, y ni esa entera: la foránea compuesta ve que la fila del puente **existe**, no que siga **activa**. Las seis están en `ServiceReportService`:

| Regla | Por qué no cabe en otro sitio |
|---|---|
| La orden no está en `CREADA` | Hay que leer el estado de otro agregado. Un reporte cuenta lo que se hizo, y en una orden sin empezar no se ha hecho nada |
| La orden no está cancelada | Lo mismo |
| El equipo sigue en el alcance **vivo** | El borrado lógico: la foránea no distingue «existe» de «sigue en uso». Tercera vez que esta distinción sube una regla al servicio, tras `client` y `equipment` |
| No hay ya un reporte vivo para ese par | Lo impide el índice único parcial, pero el servicio lo convierte en una frase que **dice cuál es el reporte que estorba** |
| Las lecturas cuadran con el tipo | **Cinco** comprobaciones desde el 2026-10-03: que el tipo se verifique, que la lectura señale una **verificación activa** de ese tipo, que el punto sea activo **de esa verificación**, que el número no pase de las lecturas que **esa verificación** declara, y que la unidad salga de ella. Eran cuatro y se razonaban por tipo, porque un tipo tenía una sola modalidad |
| Al cerrar, la verificación está completa | Exige recorrer las verificaciones del tipo y sus puntos, que están en otro módulo. **Y es más estricta que antes**: se exige por verificación, de modo que un termohigrómetro con las dos magnitudes variables ya no se puede cerrar con una lectura de temperatura y ninguna de humedad |

**Una orden ya `EJECUTADA` sí admite abrir reportes**, y es deliberado: en este negocio se va a la sede y se registra después. Lo que no se admite es reportar antes de empezar.

**La excepción de la última regla es el caso normal de un equipo averiado**: si el resultado es `FUERA_DE_SERVICIO`, no se exigen lecturas. A un equipo que no enciende no se le puede tomar una, y exigirlas obligaría a inventárselas para poder cerrar el reporte que precisamente dice que está averiado.

### La unidad no se comprueba, se hace imposible de contradecir

`VerificationReadingCommand` **no acepta la unidad en ningún caso**: el servicio la copia de la
verificación. Si el llamante la declarase, podría declarar «°C» en una verificación medida en mmHg y el
reporte saldría impreso con una unidad que nadie midió. Es la misma técnica que la orden de trabajo usa
con el área de un equipo, que tampoco viene en el comando.

**Hasta el 2026-10-03 había un hueco en esto y estaba escrito aquí como si no lo hubiera**: la unidad se
copiaba del punto, pero con modalidad variable **no hay punto**, así que en ese caso el comando sí la
pedía —`unidadSinPunto`— y ahí se podía inventar cualquier cosa. Al subir la unidad a la verificación el
campo desapareció: ya no hay forma de enviarla, de modo que no hay forma de contradecirla.

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

## Las cuatro pantallas (2026-09-28)

El backend estuvo completo un día sin que se pudiera tocar nada desde un navegador — la misma situación que las órdenes de trabajo vivieron dos semanas. Cuatro tandas lo cerraron: `3f3c170e`, `87b8cf3b`, `2c184d6e` y `30982c0f`.

| Pantalla | Dónde | Qué resuelve |
|---|---|---|
| Ficha del reporte | `/reportes/:id` | Los cinco campos de RF-15, cerrar y retirar |
| Tabla de verificación | dentro de la ficha | Las lecturas, **con la forma que dicta el tipo del equipo** |
| Historial de un equipo | `/equipos/:id/historial` | Lo que se le ha hecho a un aparato. **No es la hoja de vida** |
| Datos del servicio | dentro de la ficha | RF-11: cliente, sede, responsables y tipo de servicio |

**Se entra desde la orden**, que es lo que pide el tercer criterio de RF-09: cada equipo del alcance lleva su reporte al lado, con un enlace si existe y un botón de abrirlo si no.

### Lo que la pantalla refleja del servidor, en vez de descubrirlo a golpes

- **Cerrar es un botón, no un desplegable de estado**, y se desactiva sin procedimientos ni resultado — lo mismo que el servidor exige. Ofrecerlo y responder 409 sería ofrecer algo que no existe.
- **Los reportes se abren cuando la orden empieza.** Con la orden en `CREADA` no hay botón, y la pantalla dice por qué.
- **El reporte que cuenta es el vivo.** El API devuelve el historial de la orden, retirados incluidos; quedarse con el primero dejaría al equipo con reporte para siempre.
- **Un reporte cerrado desactiva el formulario en vez de esconderlo**: lo escrito es el documento que se entregó.
- **La tabla de verificación la dicta el tipo**: un punto por tres lecturas son tres casillas, así que la lectura en un punto ajeno o la número cuatro donde se piden tres **no se pueden escribir**.
- **La unidad no se manda cuando hay punto**: la pone el servidor desde el punto, por lo mismo que el área de un equipo no viaja en el comando de la orden.

### Un campo vacío borra, y eso hay que decirlo en los dos lados

El backend distingue el nulo —«no lo cambies»— del blanco —«bórralo»—. La pantalla muestra todo lo que hay, de modo que **vaciar una casilla es borrar el dato**, y así está escrito en el agregado y en el componente. La distinción no existe para el resultado, que es una enumeración: sin elegir se manda **ausente** y no cadena vacía, que no es un valor del catálogo.

### Averiguar cómo se verifica un equipo cuesta cuatro consultas, también aquí

`equipo_cliente` → `modelo` → `equipo` del catálogo → `tipo`. El servicio del backend camina la misma cadena en un solo método, y el componente hace lo propio con las cuatro listas que ya están en caché. Hay una prueba de que son **cuatro para toda la tabla y no cuatro por casilla**.

## Qué cierra y qué no de la ERS

**Cierra RF-09** —un reporte por equipo, asociado a los dos, accesible desde la orden—, **RF-15** —los cinco campos, guardados de forma independiente y consultables después— y, desde el **2026-09-28**, **RF-11**.

> **Corregido el 2026-09-28.** Esta nota decía que RF-11 **no** se cerraba, y era cierto el día que se escribió: el backend garantizaba que esos datos no se pudieran teclear y que no discreparan, pero el requisito dice que el reporte los «muestra» y no había pantalla. Ahora la ficha trae un bloque de **datos del servicio** con cliente, sede, servicio, fecha, ingeniero y encargado de la sede, **leídos de la orden y no copiados**, y **sin un solo control que editar** — que es además lo que RNF-07 pide de lo autocompletado. Hay una prueba que cuenta los controles del formulario y falla si alguno de esos datos aparece como campo.

**Siguen fuera RF-13 y RF-14** —los protocolos, que no existen ni en el esquema ni en el código— y **RF-17**, el PDF, que depende de la firma digital. Ya hay qué exportar, que es lo que faltaba.

**Y el historial de un equipo no es RF-26.** Esa pantalla responde «qué se le ha hecho a este aparato», que es la consulta sobre la que se construirá la hoja de vida; RF-26 y RF-27 piden el historial **dentro de** una hoja de vida que todavía no existe como entidad.

## Notas relacionadas

[[dominio-orden-trabajo]] · [[dominio-equipo-mantenimiento]] · [[dominio-reportes]] · [[arquitectura-frontend-malphasos]] · [[congelar-una-referencia-historica]] · [[modelo-de-permisos]] · [[deuda-tecnica-y-riesgos]] · [[hoja-de-ruta-producto]] · [[patron-catalogo-errores-por-contexto]] · [[openapi-swagger]]
