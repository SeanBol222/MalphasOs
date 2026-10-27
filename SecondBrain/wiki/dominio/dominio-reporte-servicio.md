---
name: dominio-reporte-servicio
description: El modulo de reportes de servicio de MalphasOS, backend completo el 2026-09-27 y frontend el 2026-09-28; uno por equipo de la orden, distinto por tipo de servicio desde el 2026-10-14 (correctivo, patron y conformidad), con el formato que la empresa entrega desde el 2026-10-16 (numero, patrones por verificacion, ingreso, entrega y el documento imprimible), con sus seis reglas cruzadas, el choque de orden entre Hibernate y un indice unico parcial, y las cuatro pantallas que cierran RF-11
tags: [dominio, reportes, esquema, mantenimiento, "describe:malphasos"]
source: malphasos/src/main/java/com/malphasos/malphasos/report/, malphasos/src/main/resources/db/migration/V9__service_report.sql y malphasos-frontend/src/app/features/report/
estado: estable
updated: 2026-10-27
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
**no aplica**, de uno en uno —`PATCH /v1/api/reports/{id}/steps/{orden}`—, porque en pantalla cada
casilla se guarda al pulsarla. Un correctivo no lleva pasos: es una reparación distinta cada vez.

Cuatro decisiones del usuario, todas del mismo día:

- **Se copian, no se leen del catálogo.** Nombre, descripción e indicación son los del día en que se
  abrió el reporte: editar un protocolo después no cambia lo que dice un reporte viejo. Se copian al
  **abrir** y no al cerrar, porque lo que el ingeniero marca mientras trabaja tiene que ser lo que había.
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

### La recepción y la verificación se guardan solas (2026-10-27)

Pedido por el usuario para quitar pasos: los botones «Guardar recepción» y «Guardar verificación» se
retiraron. Cada una se guarda **al confirmar un cambio** —el evento `change`: marcar un estado, salir de
un campo, quitar un accesorio—, no en cada tecla ni con un temporizador, y **solo si hay algo que
mandar**: la recepción, completa; la verificación, con alguna fila con sus dos lecturas. Un cambio que
llega con un guardado en vuelo **espera a que termine** y se manda después: dos peticiones a la vez
podrían llegar al revés, y como la tabla se manda entera, ganaría la vieja. Y no se da por guardado lo
que cambió mientras tanto.

**Con patrón constante, la casilla del patrón nace con el valor del punto**, y con equipo constante la
del equipo. Lo reportó el usuario —«aparece el punto pero el patrón está en blanco»—: con una pesa patrón
de 5 kg en el punto 5 la casilla tenía que valer 5, y había que copiarlo a mano en cada fila. Se puede
corregir, y rellenarla no cuenta como cambio: no guarda nada hasta que se escribe la otra lectura.

**Los repuestos salen en la hoja de vida**: `descripcionDelServicio()` los añade al final —«Repuestos:
fuente 12 V.»—, porque es lo que alguien busca cuando vuelve a fallar la misma pieza.

### El patrón, la tolerancia y la conformidad

Decidido por el usuario: **todo reporte con lecturas** dice contra qué equipo patrón se midió, el patrón
sale del catálogo de [[dominio-equipo-mantenimiento]], cada verificación del tipo declara una
**tolerancia en su unidad**, y una lectura es **conforme si `|equipo − patrón| ≤ tolerancia`**, con el
borde dentro —«±0,5» admite 0,5—.

> **Corregido el 2026-10-16, por la noche: ya no es «el patrón» sino «los patrones de cada
> verificación».** Los dos párrafos que siguen y el primer punto del cierre se escribieron con un patrón
> por reporte, que es lo que `V27` guardaba. `V30` lo cambió el mismo día —ver «El formato que la empresa
> ya entrega», abajo—. La copia y su razón siguen valiendo; lo que cambió es cuántos y dónde.

**El reporte copia el patrón como estaba ese día** —nombre, marca, serie, certificado y vencimiento—,
además de guardar su identificador. Renovar el certificado en el catálogo cambia la fila del patrón, y
un reporte firmado tiene que seguir diciendo contra qué certificado se midió. Es
[[congelar-una-referencia-historica]] otra vez, y la prueba de persistencia lo comprueba renovando el
certificado por SQL después de guardar el reporte.

**La lectura copia la tolerancia**, igual que ya copiaba la unidad, y **por eso la regla de conformidad
vive en `VerificationReading` y no en el tipo de equipo**. Si el tipo cambiara su tolerancia mañana, un
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

## Lo que este módulo decidió no hacer

- **No hay `findAll`.** Un reporte vive dentro de su orden o dentro del historial de su equipo; «todos los reportes del sistema» no responde a ninguna pregunta del dominio. El listado del API **exige** uno de los dos filtros.
- **No hay operación para reabrir un reporte cerrado**, y hay dos pruebas que lo vigilan —una por reflexión sobre el agregado y otra sobre la ruta—. Un reporte cerrado es lo que se entregó al cliente; corregirlo es retirarlo y abrir otro, que es justo lo que permite el índice único parcial.
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

**Desde el 2026-10-10 cierra también RF-13 y RF-14**, con los protocolos —ver arriba y [[hoja-de-ruta-producto]]—. (Este párrafo decía que seguían fuera porque los protocolos no existían: cierto hasta ese día.) **Sigue fuera RF-17**, el PDF, que depende de la firma digital. (Desde el 2026-10-16 el documento se
imprime desde el navegador, y **no lo cierra**: el requisito pide el PDF firmado.)

**Y el historial de un equipo no es RF-26.** Esa pantalla responde «qué se le ha hecho a este aparato», que es la consulta sobre la que se construirá la hoja de vida; RF-26 y RF-27 piden el historial **dentro de** una hoja de vida que todavía no existe como entidad.

> **Actualizado el 2026-10-04.** RF-26 y RF-27 están construidos, y **no sobre esta consulta**, que es lo que el párrafo de arriba anunciaba: la hoja de vida es un compilado de la cadena del catálogo más un historial propio, `intervencion`, que un oyente escribe al cerrarse cada reporte. **Cerrar un reporte es ahora un hecho con consecuencias fuera de este módulo**, y la primera de las 51 que el sistema declara con alguien escuchando. Ver [[hoja-de-vida]].

## Notas relacionadas

[[dominio-orden-trabajo]] · [[dominio-equipo-mantenimiento]] · [[dominio-reportes]] · [[arquitectura-frontend-malphasos]] · [[congelar-una-referencia-historica]] · [[modelo-de-permisos]] · [[deuda-tecnica-y-riesgos]] · [[hoja-de-ruta-producto]] · [[patron-catalogo-errores-por-contexto]] · [[openapi-swagger]]
