---
name: dominio-reporte-servicio
description: El modulo de reportes de servicio de MalphasOS, backend completo el 2026-09-27 y frontend el 2026-09-28; uno por equipo de la orden, distinto por tipo de servicio desde el 2026-10-14 (correctivo, patron y conformidad), con el formato que la empresa entrega desde el 2026-10-16 (numero, patrones por verificacion, ingreso, entrega y el documento imprimible), con sus seis reglas cruzadas, el choque de orden entre Hibernate y un indice unico parcial, y las cuatro pantallas que cierran RF-11
tags: [dominio, reportes, esquema, mantenimiento, "describe:malphasos"]
source: malphasos/src/main/java/com/malphasos/malphasos/report/, malphasos/src/main/resources/db/migration/V9__service_report.sql y malphasos-frontend/src/app/features/report/
estado: estable
updated: 2026-11-02
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
orden_trabajo ──┐
                ├──> reporte_servicio ──> dato_verificacion ──> punto_verificacion
equipo_cliente ─┘     (que se hizo)        (que se midio)      (donde se mide, V8)
```

**Desde `V50` el reporte es también lo que pone a su equipo en la orden**: no hay tabla puente, un equipo
está en una orden si y solo si tiene en ella un reporte activo, y el borrador nace al añadir el equipo. Ver
[[dominio-orden-trabajo]], «Sin tabla puente».

~~**La llave foránea es compuesta contra el puente**, no dos sueltas hacia `orden_trabajo` y `equipo_cliente`.~~ **Cierto hasta `V50`, el 2026-10-28**, que la cambió por dos sueltas a propósito. La compuesta impedía un reporte de un equipo que la orden no incluía; sin puente es el reporte el que lo incluye, y que el equipo sea de la sede de la orden lo comprueba el servicio de órdenes al añadirlo. Era la misma técnica de `UQ_sede_identidad_con_cliente` en `V6`.

**El reporte no copia cliente, sede, tipo de servicio ni ingeniero**, y eso *es* RF-11. El requisito pide autocompletar esos datos «a partir de la información registrada en la orden de trabajo»; consultarlos por el identificador de la orden es autocompletarlos, y la orden ya los congeló al crearse —por eso `V6` los guarda de forma redundante—. Una tercera copia habría que mantenerla de acuerdo con las otras dos. **El original sí copiaba `k_id_cliente` aquí, y tenía el vínculo con la orden roto**: `k_id_orden_trabajo varchar(10)` contra un `uuid`.

## Los cinco campos de RF-15 y los dos que hacen falta para cerrar

> **Desde el 2026-10-14 depende del tipo de servicio**: un correctivo exige además falla y diagnóstico, y
> todo reporte con lecturas, un patrón vigente. Ver «Los reportes por tipo de servicio», más abajo.
> **Corregido el 2026-10-27**: el correctivo ya no exige falla ni diagnóstico —lo pidió el usuario—, y
> el cierre vuelve a ser el mismo para los dos tipos de servicio.

`t_falla_reportada`, `t_diagnostico`, `t_procedimientos`, `t_observaciones` y `t_resultado`. **Los cinco son nulos en `BORRADOR`**: el reporte se abre vacío al llegar al equipo y se llena allí. Al cerrarlo se exigen **dos** —procedimientos y resultado, que son qué se hizo y cómo quedó—; falla y diagnóstico se quedan opcionales incluso al cerrar, porque un preventivo que sale bien no tiene ninguna de las dos.

En `fill(...)`, **un nulo deja el campo como está y un texto en blanco lo borra**. Son dos intenciones distintas y las dos hacen falta: el formulario manda solo lo que se tocó, y un campo escrito por error tiene que poder vaciarse. La distinción no existe para el resultado, que es una enumeración: ahí un nulo solo puede significar «no lo cambies».

### ⚠️ El vocabulario de `t_resultado` no sale de la ERS

`OPERATIVO`, `OPERATIVO_CON_RESTRICCIONES`, `FUERA_DE_SERVICIO`. La ERS dice «resultado» y **no enumera valores**, a diferencia de las periodicidades y los tipos de servicio de `V6`, que sí estaban escritos en el documento. Los tres son una **propuesta**, y va como catálogo cerrado y no como texto libre porque de ese dato cuelgan el historial de la hoja de vida (RF-26) y las alertas (RF-40): un texto libre no se agrupa ni dispara nada. **Mientras no haya datos, cambiar la lista es una línea**; después es una migración. Está registrado en [[deuda-tecnica-y-riesgos]].

## Los pasos del protocolo (desde el 2026-10-10, `V22`)

**Al abrir el reporte de una orden preventiva** se copian como pasos, solos y en su orden, los
protocolos que el tipo del equipo tiene asignados —RF-13—. En el reporte se marca cada uno **hecho** o
**no aplica**, de uno en uno —`PATCH /v1/api/reports/{id}/steps/{idProtocolo}`, que hasta `V51` era
`{orden}`—, porque en pantalla cada
casilla se guarda al pulsarla. Un correctivo no lleva pasos: es una reparación distinta cada vez.

Cuatro decisiones del usuario, todas del mismo día:

- ~~**Se copian, no se leen del catálogo.**~~ **Cierto hasta `V51`, el 2026-10-28.** Desde entonces el
  paso solo dice de qué protocolo es: su nombre, descripción, indicación y posición se leen del catálogo
  mientras el reporte es borrador —reordenar los protocolos de un tipo reordena sus borradores, que es lo
  que «abierto, en vivo» quiere decir— y del documento cuando se cierra. Ver «El reporte cerrado vive en
  su documento», abajo.
- **«No aplica» exige nota**, en el agregado y en la base: saltarse un paso queda justificado.
- **La regla de cierre se parte en dos.** Con pasos, se exigen **todos marcados** y el texto
  —`t_procedimientos`, que la pantalla llamará «trabajo adicional»— es opcional. Sin pasos, el texto
  sigue siendo obligatorio. Como mira otra tabla, `V22` **la sacó del `CHECK` de `V9`** y vive en
  `ServiceReport.finish`; la base sigue exigiendo resultado y fecha.
- **La línea de la hoja de vida** son los pasos hechos y detrás el trabajo adicional: «Limpieza;
  Eléctrico. Además: Cambio de fusible». Los que no aplicaron no salen. Con pasos, el texto a secas
  podía quedar vacío. Ver [[hoja-de-vida]].

Los borradores abiertos antes de `V22` no tienen pasos y se cierran como siempre.

### La lista de verificación en pantalla (2026-10-11, `c94b0f77`)

Es la de la maqueta A del rediseño, dentro del bloque «Lo que se hizo» de la ficha:

- **Cada casilla se guarda al pulsarla**, porque se marca con el equipo delante. La respuesta del
  `PATCH` ya es el reporte entero, así que `ReporteApi.marcarPaso` la pone en la caché con
  `setQueryData` y **no invalida**: recargar en cada clic haría parpadear la ficha con el formulario a
  medio escribir.
- **«No aplica» abre la nota en el mismo paso** y no se confirma sin ella: la regla del agregado y del
  `CHECK`, reflejada en vez de descubierta con un 400.
- **«Procedimientos» se llama «Trabajo adicional»** cuando hay pasos, y es opcional; sin pasos sigue
  siendo el campo obligatorio de siempre. El panel de cierre y `sePuedeCerrar` siguen la misma regla
  que `finish`.
- **El porcentaje del panel cuenta lo marcado**: con pasos es la media entre la fracción de pasos
  marcados y el resultado. Con la fórmula de antes —un requisito incompleto cuenta cero— dos de tres
  pasos daban **0 %**. Y la primera prueba que se escribió para eso **no distinguía las dos fórmulas**,
  porque con cero pasos marcados dan lo mismo: la que quedó usa dos de tres y espera 75 %.

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
- ~~**La unidad viaja en la lectura**, copiada de su verificación al tomar el dato.~~ **Cierto hasta
  `V51`**: desde entonces la unidad es la de su verificación —del tipo en un borrador, del documento en un
  reporte cerrado—, y es el documento lo que impide que reconfigurar un tipo cambie un reporte firmado.

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

## Los reportes por tipo de servicio (2026-10-14 a 2026-10-16)

Hasta el 2026-10-14 el reporte era **el mismo formulario para los tres tipos de servicio**: el
preventivo añadía los pasos del protocolo y nada distinguía un correctivo de una calibración. **Ni la
ERS ni el original describen esos formatos**, así que lo de esta sección son decisiones del usuario de
ese día, con el plan en [[hoja-de-ruta-producto]].

| Tanda | Commits | Qué trae |
|---|---|---|
| El correctivo | `bab66abe` · `becf1011` | `V24`: causa y repuestos; falla y diagnóstico obligatorios para cerrar un correctivo |
| El patrón en el reporte | `b9bc6517` · `ed3c8c9d` | `V27`: el patrón copiado, la tolerancia copiada en cada lectura, error y conformidad |
| El avance por orden | `c005aca7` · `da7d7ded` | `GET /reports/progress`, para el listado de órdenes |

### El correctivo: qué falló y qué se cambió

`V24` añade `t_causa` y `t_repuestos`, los dos opcionales y de texto libre —no hay inventario que
descuente repuestos: RF-36 es *Won't Have*—. **Cerrar un correctivo exige además la falla reportada y el
diagnóstico**: uno que no dice qué falló ni qué se encontró no cuenta qué se corrigió. Causa y repuestos
no se exigen: no siempre se sabe la causa, y no siempre se cambia algo. Es **una decisión de la sesión**,
anotada para que el usuario la discuta.

**El reporte no sabe de qué tipo de servicio es** —lo dice la orden—, así que el agregado tenía dos
cierres, `finish()` y `finishCorrective()`, y el servicio elegía cuál consultando la orden. Hacerlo un
parámetro de `finish` habría puesto en el agregado un dato que no es suyo.

> **Retirado el 2026-10-27**, a petición del usuario: la falla y el diagnóstico frenaban el cierre de
> un correctivo y no lo pide ningún requisito. `finishCorrective()` desapareció y el servicio ya no
> consulta la orden al cerrar. Los dos campos siguen en el reporte, se piden en la pantalla con su
> ayuda de correctivo y se imprimen si se llenan. Era una decisión de la sesión, anotada justamente
> para que el usuario la discutiera.

### La recepción, la verificación y la entrega se guardan solas (2026-10-27)

Pedido por el usuario para quitar pasos: los botones «Guardar recepción» y «Guardar verificación» se
retiraron, y unas horas después «Guardar entrega» —quién recibe, sin firmar—, que se guarda sola si hay
nombre, y solo en un borrador sin firmar: en uno cerrado lo escrito va con la firma, que **sigue siendo
un botón** porque firmar es una decisión explícita. Cada una se guarda **al confirmar un cambio** —el evento `change`: marcar un estado, salir de
un campo, quitar un accesorio—, no en cada tecla ni con un temporizador, y **solo si hay algo que
mandar**: la recepción, completa; la verificación, con alguna fila con sus dos lecturas. Un cambio que
llega con un guardado en vuelo **espera a que termine** y se manda después: dos peticiones a la vez
podrían llegar al revés, y como la tabla se manda entera, ganaría la vieja. Y no se da por guardado lo
que cambió mientras tanto.

**Y rehacer la tabla reutiliza sus casillas** (corregido el mismo día). Tras guardarse una fila, la
ficha recarga el reporte y la tabla se rehace con lo guardado; antes eso vaciaba el formulario y creaba
casillas nuevas, y las que se veían en pantalla seguían atadas a las viejas. Lo que se escribía en la
fila siguiente no contaba: su error y su conformidad se quedaban en «—». Lo reportó el usuario, y
proponía guardar solo al cerrar; se eligió arreglar la causa, porque guardar al cerrar pierde lo medido
si se sale sin cerrar. El fallo existía también con el botón, solo que aparecía después de pulsarlo.

**Con patrón constante, la casilla del patrón nace con el valor del punto**, y con equipo constante la
del equipo. Lo reportó el usuario —«aparece el punto pero el patrón está en blanco»—: con una pesa patrón
de 5 kg en el punto 5 la casilla tenía que valer 5, y había que copiarlo a mano en cada fila. Se puede
corregir, y rellenarla no cuenta como cambio: no guarda nada hasta que se escribe la otra lectura.

**Los repuestos salen en la hoja de vida**: `descripcionDelServicio()` los añade al final —«Repuestos:
fuente 12 V.»—, porque es lo que alguien busca cuando vuelve a fallar la misma pieza.

### La firma de quien recibe, a un reporte, a un área o a la orden (2026-10-30)

`V39` replicaba la firma con una casilla, «aplicar a los demás reportes de la orden» (RF-21). El usuario
pidió con el análisis del esquema **tres formas de firmar**: un reporte, los de **un área** de la orden o
**todos los de la orden**, que es toda la sede porque una orden es de una sola. La casilla se volvió un
`alcance` —`REPORTE`, `AREA` u `ORDEN`, opcional y `REPORTE` si falta— y en la pantalla, tres opciones.

- **Sin migración**: quien recibe no es un dato del área ni de la orden —en el periodo siguiente puede ser
  otra persona—, así que la firma sigue en cada reporte y el alcance solo dice cuántos se rellenan de golpe.
  La imagen se guarda una vez, por su huella.
- **El área es la de hoy de cada equipo**, que es la que tenía al empezar la orden: desde `V50` un equipo no
  se traslada mientras está en una orden sin ejecutar. El servicio pide los equipos del área del pedido en
  una consulta y firma los reportes de la orden cuyo equipo está entre ellos.
- **Las reglas de `V39` no cambian**: se firman los activos sin firma, abiertos o cerrados; el pedido tiene
  que poder firmarse, o falla todo.

### Los informes de una orden, juntos en un ZIP (2026-10-31)

Pedido por el usuario: un botón en la ficha de la orden que descargue el PDF de cada reporte en un ZIP,
cuando todos los equipos tienen su reporte cerrado. `GET /reports/archive?idOrdenTrabajo=` devuelve
`informes-orden-<número>.zip`, con un `informe-<número>.pdf` por reporte activo, el mismo de
`/{id}/pdf`, por orden de número.

- **Solo con todos cerrados, y lo exige el servidor**: con un borrador, o sin reportes, responde 409 sin
  convertir ninguno. Un borrador saldría marcado como tal, y lo que se descarga junto es lo que se
  entrega. La orden puede estar ejecutada o no: lo que cuenta son los reportes.
- **El alcance es el de la orden**, comprobado por `work-order` como en la lista de reportes; una cuenta de
  cliente también lo descarga, porque el botón pide `report.read` y no permisos de escritura.
- **Se convierte uno tras otro**, una llamada a Gotenberg por equipo, y el ZIP se arma en memoria. Una
  orden con decenas de equipos tarda; la pantalla lo dice («Preparando los informes…») y el botón se
  desactiva mientras tanto. Si el tiempo llega a molestar, el siguiente paso es convertir en paralelo.
- **En la pantalla es una mutación, no una consulta**: se pide al pulsar, no al abrir la ficha, y se
  guarda con un enlace temporal que se pulsa solo.

### El resumen de la orden en PDF (2026-10-31)

Pedido el mismo día que el ZIP: un documento de la orden con una tabla de todos los equipos atendidos.
El usuario eligió el diseño sobre tres propuestas dibujadas en un lienzo —la tabla de control en carta
horizontal, con la cabecera del resumen por área: la banda oscura con el logo de la empresa y los totales
por resultado— y dos decisiones: **se descarga con todos los reportes cerrados**, como el ZIP, y va **dentro
del ZIP como primera hoja** (`orden-<número>.pdf`) **y con su propio botón**, `GET /reports/summary`.

> **Corregido el mismo día: ya no tiene botón.** El usuario los quitó, el del resumen y el del cronograma:
> los dos documentos salen solo dentro del ZIP, que es lo que se entrega. **Y unas horas después se
> retiraron también las rutas** `/summary` y `/schedule`, a petición suya: sin pantalla que las llamara
> eran API muerta. El resumen y el cronograma se componen solo desde el ZIP, `GET /reports/archive`, y sus
> pruebas del servicio se reescribieron contra él.

- **Una fila por equipo**: área, equipo, marca y modelo, serie, hoja de vida, informe, fecha de cierre,
  resultado y quién firmó el recibido —una raya si nadie—. Los no operativos pesan más; solo el total de
  fuera de servicio va en el color de alerta.
- **Lo que la orden dice sale de su documento** —`WorkOrderServicePort.documentOf`—: el congelado si se
  ejecutó (`V49`), y si no el que se compondría hoy, **sin fecha de ejecución**, que el resumen imprime
  como «En curso». El compositor de la orden pone la fecha de hoy porque es lo que guarda al ejecutar, y
  un resumen de una orden sin ejecutar no puede decir que se ejecutó hoy.
- **El informe de cada fila es el del reporte activo**, no el número que el documento congeló: un
  reporte cerrado se puede sustituir con la orden ya ejecutada, y el resumen nombra el que vale.
- **No se congela**: se compone al pedirlo, con la empresa de hoy. Lo congelado ya está en la orden y en
  cada informe.
- **Una orden larga sigue en más hojas**: la cabecera de la tabla se repite y la banda va solo en la
  primera. Las firmas del ingeniero y del supervisor del cliente son líneas para firmar a mano.

**Corregido el mismo día**: la línea del servicio buscaba la periodicidad en un `Map.of`, y `Map.of` **lanza
al buscar nulo**. El resumen de una orden sin periodicidad —un correctivo— daba un 500. Lo destapó la
prueba del cronograma, no la del resumen, que solo usaba órdenes con periodicidad.

### El cronograma de la orden en PDF (2026-10-31)

Pedido el mismo día: un documento con los equipos atendidos, cuándo se les hizo el mantenimiento y cuándo
toca el próximo. El usuario eligió **«B · Plan de doce meses»** entre tres propuestas dibujadas en el
lienzo de la orden: una fila por equipo y una columna por mes, desde el del servicio hasta doce después,
con un cuadro negro para lo realizado y uno rojo en cada mantenimiento programado. Va **en el ZIP, tras el
resumen** (`cronograma-orden-<número>.pdf`). Tuvo unas horas **su propio botón** y su ruta,
`GET /reports/schedule`: ver la corrección del resumen, arriba.

- **Una sola fecha para toda la orden**, decidido por el usuario: se considera que todos los equipos se
  atienden el mismo día. Es la de ejecución de la orden, y si su documento no la tiene —una orden
  congelada tarde, o sin ejecutar todavía— la programada. **No** la del cierre de cada informe.
- **El próximo es esa fecha más la periodicidad**, con la misma regla que el informe de cada equipo
  imprime en «próximo servicio» (`ReportSheetComposer.proximoServicio`): el 31 de agosto más seis meses es
  el 28 de febrero. Los cuadros rojos caen cada tantos meses como diga la periodicidad, y se cuentan por
  la periodicidad y no por los meses entre las dos fechas —del 31 de agosto al 28 de febrero hay cinco
  meses completos—. Una mutación lo comprueba.
- **Un equipo fuera de servicio no se programa**, decidido por el usuario: su fila dice «Fuera de servicio»
  en lugar de la fecha y no tiene cuadros rojos.
- **Sin periodicidad no hay cronograma**: una orden correctiva no lo lleva en el ZIP.
- Las filas, la cabecera y la línea del cliente son las del resumen, del mismo compositor.

### El patrón, la tolerancia y la conformidad

Decidido por el usuario: **todo reporte con lecturas** dice contra qué equipo patrón se midió, el patrón
sale del catálogo de [[dominio-equipo-mantenimiento]], cada verificación del tipo declara una
**tolerancia en su unidad**, y una lectura es **conforme si `|equipo − patrón| ≤ tolerancia`**, con el
borde dentro —«±0,5» admite 0,5—.

> **Corregido el 2026-10-16, por la noche: ya no es «el patrón» sino «los patrones de cada
> verificación».** Los dos párrafos que siguen y el primer punto del cierre se escribieron con un patrón
> por reporte, que es lo que `V27` guardaba. `V30` lo cambió el mismo día —ver «El formato que la empresa
> ya entrega», abajo—. La copia y su razón siguen valiendo; lo que cambió es cuántos y dónde.

**El reporte copiaba el patrón como estaba ese día** —nombre, marca, serie, certificado y vencimiento—,
además de guardar su identificador. (Hasta `V51`: desde entonces solo guarda el identificador, la ficha se
lee del catálogo en un borrador y del documento en uno cerrado, y la razón de abajo la cumple el documento.) Renovar el certificado en el catálogo cambia la fila del patrón, y
un reporte firmado tiene que seguir diciendo contra qué certificado se midió. Es
[[congelar-una-referencia-historica]] otra vez, y la prueba de persistencia lo comprueba renovando el
certificado por SQL después de guardar el reporte.

**La lectura copiaba la tolerancia** hasta `V51`, igual que la unidad, y **por eso la regla de conformidad
vive en `VerificationReading` y no en el tipo de equipo** —desde `V51` recibe la tolerancia de quien sabe
de qué momento se habla: el documento o el tipo—. Si el tipo cambiara su tolerancia mañana, un
reporte cerrado no puede pasar de conforme a no conforme. La tanda de la tolerancia había puesto la regla
en `TypeVerification.conforme`; la tanda siguiente **la retiró** al descubrir que se calculaba con un
dato que no estaba congelado. Una regla en dos sitios es una regla que un día dice dos cosas.

Y una consecuencia que conviene saber: **registrar las mismas lecturas con otra tolerancia es un
cambio**. Si el tipo cambió su tolerancia y el ingeniero vuelve a guardar la tabla, el reporte se queda
con la de hoy; si no, seguiría diciendo conforme con un criterio que ya no es el del tipo.

**Al cerrar**, el agregado exige dos cosas más, y las dos se deciden con lo que tiene delante:

- **Con lecturas, un patrón** —desde `V30`, **uno en cada verificación medida**—. Unas lecturas sin
  patrón no dicen contra qué se compararon. Sin lecturas
  —un tipo que no se verifica, o un equipo fuera de servicio— no se pide.
- **Un patrón vigente el día del cierre**, que es el día del servicio. El último día todavía vale: vence
  al terminar.

**Elegir un patrón vencido sí se deja**, y es a propósito: se juzga al cerrar, no al elegir, por si el
certificado se renovó en papel y aún no está cargado. Uno **retirado** no se puede elegir: dejó de
usarse. La pantalla, en cambio, **solo ofrece los vigentes**: ofrecer uno vencido sería ofrecer un
callejón.

**La conformidad no decide el resultado del servicio.** Un equipo puede quedar operativo con una lectura
justo fuera y una salvedad; el resultado lo sigue eligiendo el ingeniero, y la pantalla **advierte** si
marca «Operativo» con lecturas fuera de tolerancia. Decisión de la sesión, para discutir.

**En pantalla, el error se calcula en diezmilésimas enteras.** 50,7 − 50,2 da 0,5000000000000071 en
coma flotante, y con una tolerancia de 0,5 la pantalla habría dicho «No» donde el servidor —que cuenta
en decimal— dice «Sí». Una prueba escribe exactamente ese caso.

### El avance de los reportes por orden

`GET /v1/api/reports/progress` devuelve, para cada orden con algún reporte vivo, **cuántos equipos lo
tienen cerrado y cuántos en borrador**, contado en la base con un `GROUP BY` y sin cargar ningún reporte.
Es lo que el listado de órdenes enseña en cada fila —«3 de 5 cerrados»—, y vive aquí porque los
reportes son de aquí y `work-order` no puede depender de este módulo.

**No lleva parámetros, a propósito**: con los identificadores de las órdenes en la URL, un listado largo
superaría el límite de su longitud —36 caracteres por orden—. Con alcance de cliente se filtra por las
órdenes que ese cliente ve, **preguntándoselo al módulo de órdenes**, que es el dueño de ese dato.

**Una mutación sobrevive y es equivalente**: quitar el `DISTINCT` del conteo. El índice único parcial ya
garantiza un reporte vivo por equipo y orden, así que contar reportes y contar equipos da lo mismo; se
cuentan equipos para no depender de eso, y el javadoc lo dice.

### Lo que la orden pregunta a este módulo

Desde el 2026-10-14 **una orden no se ejecuta con reportes abiertos**, y quien lo impone es
`work-order`, que **no puede depender de `report`** —es al revés—. El módulo de órdenes declara
`WorkOrderReportsPort` y este lo implementa con `WorkOrderReportsAdapter`: inversión de dependencias,
igual que la hoja de vida. Ver [[dominio-orden-trabajo]].

## El formato que la empresa ya entrega (2026-10-16)

El usuario aportó **cuatro informes reales** de la empresa —una balanza, un oxímetro, un
termohigrómetro y una pedalera, en `Documentation/raw/`, **sin versionar**: llevan datos personales de un
cliente real, y ningún nombre ni dato de ellos se usa en código, pruebas ni base de desarrollo—, y
eligió entre tres diseños el **C**: el informe de siempre, con una gráfica de error y su banda de
tolerancia. Construirlo pedía datos que el reporte no guardaba, y entraron en seis tandas, todas con sus
decisiones tomadas por el usuario:

| Tanda | Commits | Qué trae |
|---|---|---|
| El número | `0e4012ae` · `42e7c793` | `V28`: los seis primeros caracteres del identificador, en mayúsculas —`1DC6F5`— |
| La ficha del patrón | `2487351f` · `19db813c` | `V29`: modelo, código interno, rango, resolución, calibración y empresa calibradora |
| Patrones por verificación | `9f769654` · `cfa6b24f` | `V30`: varios patrones en cada verificación, con la ficha copiada |
| Ingreso y entrega | `6a588034` · `521ebab2` | `V31`: las dos verificaciones al ingreso, los accesorios y quién recibe |
| El formato en el tipo | `deabd70b` · `042970e4` | `V32`: `BB-ING-MP0`, `MP1` o `MP2`, que elige el tipo de equipo |
| El documento | `85bc3303` · `ac44e406` · `3ddfc3a1` | `/reportes/:id/documento`, imprimible desde el navegador |

**El número es un prefijo, como un commit de git**, y no un consecutivo: el usuario lo pidió así. Git
alarga el prefijo cuando dos commits lo comparten; aquí no se puede, porque el número va impreso en un
documento firmado. **Lo resuelve quien abre el reporte**: si el prefijo ya está tomado, genera otro
identificador —`conNumeroLibre`, con un número de intentos acotado— y un índice único de `V28` lo
garantiza. **No es parcial**: un reporte retirado conserva su número.

**Varios patrones por verificación**, porque una balanza se verifica con tres juegos de pesas y un
termohigrómetro con un patrón de temperatura y otro de humedad. Al cerrar se exige **un patrón vigente
en cada verificación con lecturas**, y el mensaje nombra la magnitud que falta. Quitar un patrón
**retira** la fila y volver a ponerlo **la reactiva** en vez de insertar otra: así nunca compiten dos
filas activas por el mismo sitio en un vaciado de Hibernate, que es la trampa de siempre con un índice
parcial. `V30` repartió el patrón de `V27` entre las verificaciones de las que el reporte tenía lecturas,
retiradas incluidas.

**Al ingreso, dos filas fijas en todo reporte** —estado de la estructura física y estado operativo—,
cada una conforme, no conforme o no aplica, y **«no conforme» exige observación**: sin ella el reporte
dice que algo estaba mal y no qué. Más **una lista libre de accesorios**, que se sustituye entera al
guardarla. **Quién recibe el equipo es texto libre** —nombre, cédula y cargo—, porque muchas veces no
está registrado en el sistema. Ninguna de las tres cosas se exige para cerrar: es una pregunta abierta,
no una decisión.

**El formato lo elige el tipo de equipo**: `MP0` para los que no se verifican —una sola página—, y `MP1`
o `MP2` para los que sí. `EquipmentType.chooseReportFormat` rechaza la combinación que no sirve, y
**declarar o retirar verificaciones ajusta el formato solo** si deja de servir. **Qué distingue `MP1` de
`MP2` no se sabe todavía**: los cuatro informes son `MP0` y `MP2`. Se imprimen igual.

> **Corregido el 2026-10-16, y dos veces.** La frase de arriba era **falsa el día que se escribió**: la
> balanza es `MP1`. Releídos, los informes dicen `MP0` para un equipo mecánico sin verificación, `MP1`
> para uno mecánico con ella y `MP2` para los electrónicos con ella. Y **la pregunta dejó de importar
> ese mismo día**: el usuario decidió que los formatos `MPx` eran del sistema antiguo y **se retiran**.
> Habrá un solo «Informe de mantenimiento», con secciones que aparecen según los datos. El diseño
> completo está en [[hoja-de-ruta-producto]], en «El diseño único del informe».

### El documento: se compone, no se guarda

`/reportes/:id/documento` pinta el informe en hojas carta y se imprime o se guarda en PDF **desde el
navegador**, como la hoja de vida. **No es una entidad ni guarda nada**: se compone con el reporte, su
orden y la hoja de vida del equipo, que ya trae cliente, ficha técnica, responsable del área y membrete.

- **Página 1**: banda con el número y un QR que lleva al documento, resultado, fechas y conteo de
  conformes; cliente, equipo, tipo de servicio, ingreso y accesorios, protocolo, relato, aclaración y
  tres firmas —ingeniero, supervisor, quien recibe—.
- **Página 2**, solo en `MP1` y `MP2`: la trazabilidad de los patrones con la ficha copiada, y por cada
  verificación su tabla y **la gráfica de error con la banda de tolerancia**, con la lectura fuera de la
  banda en rojo. Cierra con el texto de garantía de la empresa.

**Lo que se deriva en vez de guardarse**: el **supervisor** es el encargado del área —decisión del
usuario—; el **próximo servicio** es la fecha más la periodicidad de la orden en meses de calendario, con
el último día del mes si no existe —31 de agosto más seis meses es el último de febrero—; y la casilla
de **verificación metrológica** se marca sola si hay lecturas. **Un reporte cerrado** lleva su fecha de
cierre y el ingeniero congelado en la hoja de vida; **un borrador**, la fecha programada y la marca de
agua «BORRADOR».

**Lo que no queda congelado, y conviene saberlo**: cliente, equipo y responsable salen de la hoja de vida
**de hoy**, así que el documento de un reporte cerrado cambia si cambian esos datos. Lo que el propio
reporte congela —lecturas, patrones, tolerancia, ingeniero— sí sale congelado. Congelar el resto es
trabajo del PDF del servidor, cuando llegue la firma. Anotado en [[deuda-tecnica-y-riesgos]], con el texto
de garantía fijo en el código y la página 2 que, con muchas lecturas, se partiría en una tercera hoja
diciendo «Página 2 de 2».

**No sube el marcador**: RF-17 pide el PDF **firmado**, y eso sigue esperando a RF-18 y RF-21.

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

**Desde el 2026-10-16, una más**: un patrón se pone en una **verificación activa del tipo del
equipo**, que está a cuatro saltos del reporte y por eso la foránea de `V30` es simple. Reutiliza la
misma guarda que las lecturas. Y el servicio **repite** al cerrar la exigencia de patrón por verificación
que el agregado ya impone, solo para que el mensaje diga «temperatura» y no un identificador: no es una
regla nueva sino una frase mejor.

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

## El reporte cerrado vive en su documento (2026-10-28, `V51`)

Decidido por el usuario con el análisis del esquema: **una sola fuente para cada momento**. Hasta `V51`
había dos a la vez: el documento de `V37`, congelado al cerrar, y las copias que el reporte hacía en sus
filas —la ficha de cada patrón, la unidad y la tolerancia de cada lectura, el texto y la posición de cada
paso—. Salen las copias, y el documento crece a su **versión 2** con lo que guardaban.

| | Borrador | Cerrado |
|---|---|---|
| Texto y posición de cada paso | El protocolo y su asignación al tipo, hoy | El documento |
| Unidad y tolerancia de cada verificación | El tipo, hoy | El documento |
| Ficha de cada patrón | El catálogo, hoy | El documento |
| Qué paso se marcó, qué se midió, qué patrón se eligió | Las filas del reporte | Las filas del reporte |

**Lo compone `ReportDocumentComposer.catalog`**, y el servicio lo publica como `catalogOf`: el API y la
hoja del informe leen de ahí sin saber de cuál de las dos fuentes sale. **El cierre comprueba el
vencimiento de cada patrón en el documento que congela**, así que un certificado renovado en el catálogo
después de elegir el patrón ya vale sin volver a elegirlo. Y **la línea de la hoja de vida** —los pasos
hechos— sale del documento con que el reporte se cierra.

**El relleno**: los documentos de los cerrados pasan a la versión 2 en SQL, leyendo las copias antes de
quitarlas. Con lecturas, la tolerancia del documento es la que se copió en ellas —la que juzgó cada una—;
sin lecturas, la del tipo. **La migración se detiene** si queda un cerrado sin documento, porque sus copias
no tendrían dónde ir: los congelaba la aplicación al arrancar desde `V37`.

**La prueba gemela**, `ClosedReportDocumentTest`, siembra los mismos datos en la base principal —donde Java
cierra el reporte— y en un esquema en `V50` —donde ya está cerrado, con sus copias y su documento en la
versión 1—, aplica `V51` y compara. **Al mutarla aparecieron dos supervivientes con causa**: quitar el
`ORDER BY` de los pasos o de los patrones no cambiaba nada, porque PostgreSQL resolvía la consulta con los
índices únicos de esas tablas y ya devolvían las filas en orden. Orden por plan, no garantizado; la prueba
migra ahora con los recorridos por índice apagados, y las dos mueren.

**Dos costes, aceptados al construirlo**: un borrador retirado pierde el texto de sus pasos —no tenía
documento y ya no hay copias, de modo que se lee del catálogo de hoy—, y listar los reportes de una orden
compone el catálogo de cada borrador, que son unas pocas consultas por reporte.

## Quien recibe puede ser una persona registrada (2026-11-01, `V55`)

El supervisor del cliente a veces recibe y aprueba a la vez —el doctor de un consultorio—. Entonces el
reporte apunta a su ficha con `k_id_recibe` y **no copia su nombre, su cédula ni su cargo**: se leen de
`persona` y de `supervisor_cliente` al pintar el informe y el resumen. El nombre escrito sigue para quien
no está registrado, como la jefa de enfermería de turno; un `CHECK` impide las dos cosas a la vez.

- **Solo un supervisor activo del cliente de la orden** firma como persona registrada —regla del servicio,
  `ERR_SERVICE_REPORT_011`—; a cualquier otra persona se la escribe por su nombre.
- **El alcance de siempre**: la misma firma con alcance `AREA` u `ORDEN` pone a la misma persona en los
  reportes que aún no la tengan.
- **La petición exige una de las dos**, el nombre o la persona, con un `@AssertTrue` que responde el 400 de
  datos inválidos antes de llegar al servicio.
- **La firma guardada en la cuenta** pide desde aquí `work-order.approve` y no `report.write`: la tienen
  exactamente quienes firman algo, y con la de antes el supervisor no podía guardar la firma con que aprueba.

## Lo que este módulo decidió no hacer

- **No hay `findAll`.** Un reporte vive dentro de su orden o dentro del historial de su equipo; «todos los reportes del sistema» no responde a ninguna pregunta del dominio. El listado del API **exige** uno de los dos filtros.
- **No hay operación para reabrir un reporte cerrado**, y hay dos pruebas que lo vigilan —una por reflexión sobre el agregado y otra sobre la ruta—. Un reporte cerrado es lo que se entregó al cliente; corregirlo es **sustituirlo** —desde `V50` en un solo paso, que lo retira y abre el borrador del mismo equipo; decía «retirarlo y abrir otro», cierto hasta ese día—, que es justo lo que permite el índice único parcial. **Y un cerrado ya no se retira por su cuenta**: dejaría a su equipo fuera de una orden en la que sí se le prestó el servicio.
- **No hay una tercera autoridad para cerrar.** Quien llena el reporte es quien lo firma en campo: separarlas describiría un reparto de trabajo que en esta empresa no existe. La firma digital (RF-21) traerá la suya. Ver [[modelo-de-permisos]].
- ~~**No hay protocolos** (RF-13, RF-14).~~ **Falso desde el 2026-10-10**: existen, se copian en cada reporte preventivo y se marcan uno a uno —ver «Los pasos del protocolo», arriba—. Esta línea sobrevivió seis días a su propia sección.

## Las cuatro pantallas (2026-09-28)

El backend estuvo completo un día sin que se pudiera tocar nada desde un navegador — la misma situación que las órdenes de trabajo vivieron dos semanas. Cuatro tandas lo cerraron: `3f3c170e`, `87b8cf3b`, `2c184d6e` y `30982c0f`.

| Pantalla | Dónde | Qué resuelve |
|---|---|---|
| Ficha del reporte | `/reportes/:id` | Los cinco campos de RF-15, cerrar y retirar |
| Tabla de verificación | dentro de la ficha | Las lecturas, **con la forma que dicta el tipo del equipo** |
| Historial de un equipo | `/equipos/:id/historial` | Los reportes de un aparato, borradores y retirados incluidos. **No es la hoja de vida**, que existe desde el 2026-10-04 en `/equipos/:id/hoja-de-vida` y solo cuenta mantenimientos cerrados. Ver [[hoja-de-vida]] |
| Datos del servicio | dentro de la ficha | RF-11: cliente, sede, responsables y tipo de servicio |

**Se entra desde la orden**, que es lo que pide el tercer criterio de RF-09: cada equipo del alcance lleva su reporte al lado. (Con un enlace si existía y un botón de abrirlo si no, hasta `V50`: desde entonces todo equipo de la orden tiene el suyo, y el botón desapareció.)

### Lo que la pantalla refleja del servidor, en vez de descubrirlo a golpes

- **Cerrar es un botón, no un desplegable de estado**, y se desactiva sin procedimientos ni resultado — lo mismo que el servidor exige. Ofrecerlo y responder 409 sería ofrecer algo que no existe.
- ~~**Los reportes se abren cuando la orden empieza.**~~ **Desde `V50` se llenan cuando la orden empieza**: el borrador existe desde que se añadió el equipo, la ficha del reporte se desactiva con la orden en `CREADA` y dice por qué, y la de la orden enlaza a «Ver borrador».
- **Un cerrado se sustituye y un borrador se quita de la orden** (`V50`): son los dos botones del panel, y «Quitar de la orden» no aparece con la orden ya ejecutada.
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

**Desde el 2026-10-10 cierra también RF-13 y RF-14**, con los protocolos —ver arriba y [[hoja-de-ruta-producto]]—. (Este párrafo decía que seguían fuera porque los protocolos no existían: cierto hasta ese día.) **Sigue fuera RF-17**, el PDF, que depende de la firma digital. (Desde el 2026-10-16 el documento se
imprime desde el navegador, y **no lo cierra**: el requisito pide el PDF firmado.)

**Y el historial de un equipo no es RF-26.** Esa pantalla responde «qué se le ha hecho a este aparato», que es la consulta sobre la que se construirá la hoja de vida; RF-26 y RF-27 piden el historial **dentro de** una hoja de vida que todavía no existe como entidad.

> **Actualizado el 2026-10-04.** RF-26 y RF-27 están construidos, y **no sobre esta consulta**, que es lo que el párrafo de arriba anunciaba: la hoja de vida es un compilado de la cadena del catálogo más un historial propio, `intervencion`, que un oyente escribe al cerrarse cada reporte. **Cerrar un reporte es ahora un hecho con consecuencias fuera de este módulo**, y la primera de las 51 que el sistema declara con alguien escuchando. Ver [[hoja-de-vida]]. (Hasta `V52`, el 2026-10-28: el historial se deriva desde entonces de los reportes cerrados, el oyente se retiró y ningún evento tiene ya consumidor.)

## Notas relacionadas

[[dominio-orden-trabajo]] · [[dominio-equipo-mantenimiento]] · [[dominio-reportes]] · [[arquitectura-frontend-malphasos]] · [[congelar-una-referencia-historica]] · [[modelo-de-permisos]] · [[deuda-tecnica-y-riesgos]] · [[hoja-de-ruta-producto]] · [[patron-catalogo-errores-por-contexto]] · [[openapi-swagger]]
