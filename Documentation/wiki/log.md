# Log — Wiki de la documentación de MalphasOS

Registro cronológico append-only. Formato: `## [YYYY-MM-DD] tipo | tema`, con `tipo` en `ingest`, `query` o `lint`. Parseable con `grep "^## \[" log.md`.

## [2026-09-05] ingest | Creación de la Matriz de Interesados (standalone)

Construcción formal de `Documentation/StakeholdersMatrix/MatrizDeInteresados.tex` (compilada con `latexmk -pdf` a `MatrizDeInteresados.pdf`). Aplica la clase `standalone` (variante `varwidth=170mm`), la paleta institucional y la regla central de implementación. Encadena a los 8 interesados del Project Charter con los objetivos del proyecto (`OBJ-01` a `OBJ-07`), los requisitos de la ERS IEEE 830 y los 10 riesgos iniciales (`R-01` a `R-10`). Registra en su sección de hallazgos el riesgo estructural de unipersonalidad, la brecha de autorización técnica (`admin.full`), el desfase en valor para usuarios de campo y la tensión regulatoria por la exclusión de alertas de calibración.

Se añade la nota de wiki `documentos/matriz-de-interesados.md` y se actualiza el catálogo en `index.md`.

## [2026-09-05] ingest | Construcción inicial de la wiki

Primera versión completa. Destilada de los cuatro documentos oficiales existentes en `Documentation/` —`IEEE830/IEEE830.tex`, `constitutionDocument/constitutionDocument.tex`, `ScopeManagementPlan/PlanDeGestionDelAlcance.tex` y `TraceabilityMatrix/MatrizDeTrazabilidad.tex`— y contrastada de nuevo contra `malphasos/` para la correspondencia terminológica y para verificar que ningún estado citado había quedado desactualizado desde el 5 de septiembre.

`Documentation/CONVENCIONES.md` deja de llevar las convenciones, los datos del proyecto y la estructura de carpetas — todo eso se muda a notas propias de esta wiki — y pasa a ser una puerta de entrada corta que apunta a `wiki/index.md`.

Un hallazgo de la construcción, no de la documentación previa: el correspondiente enum `RoleType` del módulo `person` (`malphasos/src/main/java/.../person/domain/person/RoleType.java`) documenta explícitamente en su Javadoc que **no es lo mismo que `PersonType`**, aunque comparten tres nombres por coincidencia. Es la mejor fuente de código para la nota [[correspondencia-terminologica]]: el propio código ya advierte del riesgo de confusión que esta wiki existe en parte para resolver.

No se modificó ningún `.tex` ni ningún archivo de `SecondBrain/` o `malphasos/` durante esta construcción.

## [2026-09-13] ingest | El frontend se declara, y el manual de marca pasa a ser la autoridad visual

**Dos documentos nuevos en `Documentation/`**, y con ellos dos notas: [[declaracion-diseno-frontend]] y [[manual-de-marca]].

**La Declaración de Diseño del Frontend** (`FrontendDesign/`, 16 páginas) decide plataforma, arquitectura, sistema visual, accesibilidad, movilidad y pruebas. Sigue el preámbulo, la paleta y los macros de la ERS y del plan de alcance: es hermana de ellos, no un documento distinto que comparte carpeta.

**El Manual de Marca v1.0** llegó ya hecho y **cambió el reparto de autoridad**. Hasta hoy [[paleta-de-colores]] se presentaba como la definición de la paleta, con una sección llamada «paleta ampliada del design system». **Era falso**: no existía tal sistema, la nota describía los colores de unos documentos LaTeX y de un Gantt, y listaba `#201E1D` sin poder decir de dónde salía. Esa nota se reescribió entera: ahora remite al manual y se limita a registrar los colores de los `.tex` y las discrepancias entre ambos mundos.

**Tres discrepancias, comprobadas archivo por archivo y no supuestas:**

1. **`mainGray` `#6b6866` no existe en el manual de marca** —ni en el logo, ni en el documento—. Es un gris propio de los `.tex` y ahí se queda.
2. **La tinta `#201E1D` no la declara ningún `.tex`.** Viene de la marca. La nota la listaba sin procedencia; ahora se sabe cuál es.
3. **El gradiente del Gantt sólo coincide a medias con la escala de acento del manual**: cuatro de sus trece colores son pasos reales, los otros nueve no. El Gantt es anterior y se deja como está.

**Y un hallazgo que sí aporta la declaración.** El manual trae su propia regla de contraste —`#AE1800` para texto en acento— y funciona: 6,41:1 medido. **Pero no nombra el botón.** Describe la acción principal como «relleno acento», y con `#EC3013` de relleno la etiqueta da **3,76:1**, por debajo del 4,5:1 de AA. El control más repetido de una interfaz no alcanzaría el nivel declarado. Se resolvió **extendiendo la regla del propio manual** a ese caso —relleno con el paso 700— en vez de corregirlo: el manual ya había decidido que el acento puro no sostiene texto encima.

**Un dato que confirma que marca y documentos ya eran coherentes**: muestreado el logo `malphasos-stacked.png`, sus píxeles opacos son `#F3F2F2` en un 84,2 %, `#2D2B2B` en un 9,2 %, `#201E1D` en un 3,3 % y `#EC3013` en un 1,2 %. Lo que faltaba no era coherencia sino que alguien la escribiera.

**Corregido además el `CONVENCIONES.md` de este directorio**, que mandaba trabajar en un worktree sobre la rama `MalphasOS-Documentation`. Ni la rama ni el worktree existen desde el 2026-09-12.

**Tocadas**: [[paleta-de-colores]] (reescrita), `index.md`, `CONVENCIONES.md`. **Nuevas**: [[declaracion-diseno-frontend]], [[manual-de-marca]].

## [2026-09-27] ingest | Tres notas de requisitos caducadas, y la cifra agregada corregida de 8 a 16

**Esta wiki llevaba tres semanas diciendo cosas falsas, y el aviso para evitarlo estaba escrito dentro de la nota que falló.** [[estado-de-implementacion]] daba **8 de 31** requisitos funcionales, medidos el 2026-09-05. Dejó de ser cierto el **2026-09-13** con el módulo de órdenes de trabajo, otra vez el **2026-09-27** con sus cuatro pantallas, y una tercera ese mismo día con los reportes de servicio. La cifra correcta, recontada contra el código y los commits, es **16**.

**Lo que falló no fue la regla, fue el sitio donde estaba escrita.** La nota termina con un apartado —«antes de citar una cifra de estado»— que dice exactamente lo que había que hacer. No sirvió de nada: quien construye no abre la nota que no está buscando. Lo que sí funcionó fue el contador paralelo de `SecondBrain/wiki/malphasos/hoja-de-ruta-producto.md`, que se actualizó **en la misma sesión** de cada tanda. **Dos contadores de lo mismo en dos wikis** es la causa de fondo, y queda anotada en la propia nota.

**Corregidas también las dos notas de categoría que lo decían en primera línea**: [[rf-ordenes-trabajo]] afirmaba que «ninguno de los siete requisitos tiene implementación», y [[rf-reportes-mantenimiento]] que «ninguno implementado: el reporte de mantenimiento no existe como entidad del sistema». Las dos evidencias citaban además «las cinco migraciones (`V1` a `V5`)», que hoy son nueve.

**Dos desviaciones quedan registradas como tales, y no como implementadas**: **RF-04** —la pantalla no pide elegir áreas antes de mostrar sus equipos— y **RF-11** —el backend hace imposible teclear los datos de la orden, pero «mostrarlos» es una pantalla que no existe—. Es la regla de [[regla-implementado-vs-previsto]] aplicada en contra del marcador, que es cuando de verdad sirve.

**Y tres cosas que la implementación añadió y la ERS no pide**, anotadas en [[rf-reportes-mantenimiento]] para que el documento las recoja o las descarte: el **estado** del reporte —borrador y finalizado—, el **catálogo cerrado** del campo «resultado» —tres valores que la ERS no enumera— y las **lecturas de la verificación metrológica**, que la ERS solo menciona para decir que no son RF-15.

**Tocadas**: [[estado-de-implementacion]], [[rf-ordenes-trabajo]], [[rf-reportes-mantenimiento]], `index.md`.

## [2026-09-28] ingest | RF-11 cierra un día después de anotarse como pendiente

**El requisito más rápido de cerrar de todos los registrados en esta wiki.** Ayer se anotó como previsto con el razonamiento estricto —el backend hacía imposible teclear esos datos y garantizaba que no discreparan, pero «mostrar» es una pantalla y no había pantalla—. Hoy la ficha del reporte trae el bloque de datos del servicio, y los tres criterios de aceptación se cumplen.

**Lo que hace que cuente, y no solo se parezca**: los datos **se leen de la orden y no se copian** —el reporte no tiene columna para ellos, lo decidió `V9`—, así que no pueden discrepar; y **no hay ningún control que editar**, que es lo que RNF-07 pide de lo autocompletado. Hay una prueba de frontend que cuenta los controles del formulario y falla si uno de esos datos aparece como campo.

**La cifra pasa de 16 a 17 de 31**, y el contraste con la corrección de ayer merece quedarse: esa nota llevaba **tres semanas** caducada en 8 porque nadie recontaba; esta caducó **en un día** porque el trabajo fue rápido. Las dos formas de caducar existen, y solo una se arregla recontando al citar.

**Tocadas**: [[estado-de-implementacion]], [[rf-reportes-mantenimiento]], `index.md`.

## [2026-09-28] lint | `CLAUDE.md` pasa a `CONVENCIONES.md`, aquí y en la raíz

Decisión del usuario: ese nombre aparecía en la portada del repositorio en GitHub. Se renombraron los cuatro archivos del proyecto con `git mv` —el de este directorio y el de esta wiki entre ellos—, con su contenido y su historial intactos.

**Las referencias de esta wiki se actualizaron todas**, incluidas las de las entradas anteriores a hoy: hablan del mismo archivo, que ahora se llama de otra manera. La convención que este directorio impone no cambia — **no se documenta como existente algo que no está implementado** — y sigue viviendo en `Documentation/CONVENCIONES.md` y en `Documentation/wiki/CONVENCIONES.md`.

**Tocadas**: [[index]], las cinco notas que citaban el nombre viejo, y el propio archivo de convenciones.

## [2026-10-03] ingest | dos notas corregidas por el remodelado del catálogo de equipos

El módulo `equipment` se remodeló: un tipo de equipo se verifica ahora en **varias magnitudes**, porque
un termohigrómetro mide temperatura y humedad relativa. El detalle vive en `SecondBrain/`; aquí entra
solo por lo que deja falso en esta wiki, que era la regla de este directorio — **no se documenta como
existente algo que no está implementado**, y su contrapartida es no dejar documentado como existente
algo que dejó de estarlo.

**[[rf-alertas-calibracion]] afirmaba dos cosas falsas.** Decía que `tipo_equipo` tiene las columnas
`b_verificable` y `n_tipo_verificacion` —se fueron con la migración `V10`— y que las verificaciones
técnicas eran «la segunda tanda pendiente» del módulo —están construidas desde el 27 de septiembre, y
el resultado de verificar también—. Lo que sigue sin existir, y es lo único que RF-40 y RF-41
necesitan, es **la fecha de vencimiento**: una columna y quien la vigile. Corregido dejando constancia.

**[[estado-de-implementacion]] gana una quinta verificación y el marcador no se mueve**, lo que es en sí
el dato: **ningún requisito de la ERS describe magnitudes ni unidades**, así que no hay nada que contar.
Lo que el cambio hace es que **RF-15 deje de ser una verdad a medias** —un termohigrómetro no se podía
reportar sin inventarse dos tipos de equipo— y el requisito se daba por implementado igualmente.

Es el primer caso en esta wiki de **un requisito marcado como implementado que mejora sin cambiar de
estado**, y conviene que quede dicho porque invita a un error de lectura: el marcador cuenta requisitos
satisfechos, no la calidad con la que se satisfacen. Un 17 de 31 no dice que esos 17 estén igual de
bien resueltos.

**Tocadas**: [[rf-alertas-calibracion]] y [[estado-de-implementacion]].

## [2026-10-04] lint | la ERS contra el código, en la dirección contraria

**Primera vez que esta wiki compara la ERS con el sistema buscando lo inverso de su regla.** La regla
dice «no se documenta como existente algo que no está implementado»; esta vez se buscó lo contrario — y
apareció.

**Seis anotaciones «Estado en la implementación actual» del `.tex` están caducadas.** Cinco dicen «las
cinco migraciones del esquema» cuando hay **once**, y una, la de RF-26, dice que el oyente del cierre de
un reporte irá en su directorio «cuando el reporte exista»: el reporte existe desde el 2026-09-27, con
sus cinco eventos. Un lector concluiría que RF-26 está bloqueado por una pieza ya construida.

Las de «cinco migraciones» son más benignas y conviene decirlo sin exagerar: la conclusión que sostienen
—que no hay tabla de historial, ni de inventario, ni de calibración— **sigue siendo cierta**. Lo que
caducó es el número con el que se justifica.

**Y dos requisitos figuran como implementados contra el texto de la propia ERS.** RF-22 exige un
formulario con cuatro secciones y el `.tex` dice que la de servicio técnico «falta, y falta entera»;
además no hay *un* formulario, sino cinco recursos. RF-24 depende de RF-22 y pide editar «la hoja de
vida», que no existe como entidad.

Es **la misma situación que RF-04**, marcado como desviación y no como implementado. Con el criterio que
el proyecto se fijó por escrito —«lo que el backend satisface **por completo**»— el marcador sería 15 de
31 con tres desviaciones. **No se cambió la cifra**: es la decisión que quedó abierta con RF-04 y
conviene tomarla una vez para los tres. Entra [[estado-de-la-ers-caducado]] con las citas.

### Lo que resultó correcto, que es la mayor parte

- **El denominador 31 es exacto**, por dos vías que coinciden: 31 declaraciones `\textbf{RF-xx}` y 31
  filas de tabla, con la misma lista. Los otros 18 códigos que la ERS menciona se citan sin declararse,
  y eso ya estaba en [[priorizacion-moscow]] —se reencontró por otra vía y coincidió—.
- **RF-27 está bien marcado como previsto.** Hay pantalla de historial de un equipo desde el 2026-09-28
  y podría parecer que lo cubre; no lo cubre, porque RF-27 depende de RF-26 y ese pide el registro
  **automático** al cerrar un reporte. El javadoc de `historial-equipo.ts` ya lo distinguía solo.

### Cuatro notas de esta wiki no existen, y se citan 55 veces

`defectos-conocidos-de-la-ers` es **la más citada de esta wiki —28 veces— y no está escrita**;
`correspondencia-terminologica` va por 11, `glosario-dominio` por 9 y `diagramas-de-casos-de-uso` por 7.
Un enlace sin destino es una nota pendiente y no un error, pero **el `CONVENCIONES.md` de la raíz decía
que quedaban dos**: corregido allí.

**Tocadas**: [[estado-de-la-ers-caducado]] (nueva), [[rf-hojas-vida]], [[estado-de-implementacion]],
[[index]], y el `CONVENCIONES.md` de la raíz.

## [2026-10-04] lint | una nota caducada un mes, y una propiedad de seguridad sin requisito

**[[rf-usuarios-seguridad]] afirmaba algo falso desde hacía un mes.** Decía que el segundo criterio de
RF-50 «no se cumple todavía» porque «las 83 operaciones de la API exigen la misma autoridad,
`admin.full`» y porque los roles de lectura de `engineers` y `clients` «ninguna operación REST comprueba
todavía», de modo que un ingeniero «recibe 403 en toda llamada». Era cierto el 2026-09-05 y **dejó de
serlo el 2026-09-08**, con el modelo de permisos; nadie volvió sobre la nota. Es exactamente la
desincronización que esta wiki existe para evitar, y queda escrita en lugar de sustituida en silencio.

Hoy ese criterio **se cumple en tres capas**: cada operación exige la autoridad de su recurso y una
sola, el menú del frontend oculta por autoridad desde el 2026-10-02, y desde el 2026-10-04 las lecturas
acotan además **por dueño** —18 en cuatro módulos—.

### El hallazgo que sí importa: una propiedad de seguridad que ningún requisito pide

**Ningún requisito de la ERS pide el filtrado por dueño.** No hay RNF de aislamiento de datos, y RF-50
habla de «funcionalidades», no de filas. Se construyó una propiedad de seguridad real que ninguna línea
del documento reclamaba, y con la regla de esta wiki eso **no es un extra del código: es un requisito
que falta**.

**Y RF-50 no contempla al usuario cliente.** Enumera «SuperUsuario, Administrador o Ingeniero», y el
realm tiene un cuarto grupo, `clients`, con cinco autoridades de lectura que el sistema sirve de verdad.
El rol existe en el código y en Keycloak, y no existe en el requisito. **El marcador no se mueve** —RF-50
ya contaba como implementado—, pero el modelo de roles de la ERS está incompleto.

**Tocadas**: [[rf-usuarios-seguridad]].

## [2026-10-04] lint | las hojas de vida, completas: 19 de 31

**La categoría 3.2.5 pasa de 2 a 4 de 4**, y el marcador de **17 a 19**. Entran RF-26 —el historial se
escribe solo al cerrar un reporte— y RF-27 —se consulta ordenado—, y RF-22 y RF-24 dejan de estar «en
revisión» desde la mañana. **La duda se resolvió construyendo y no reinterpretando**: la sección de
servicio técnico que faltaba entera existe, y la hoja de vida es un documento y no cinco recursos. Para
RF-24 hizo falta además una decisión del usuario: la hoja de vida es de solo lectura, y modificarla es
corregir cada dato donde vive.

**RF-04 no se arrastra con ellos.** Su decisión sigue abierta, porque la de las hojas de vida no se
tomó relajando el criterio, de modo que no sirve de precedente.

### Un defecto nuevo de la ERS, y de otra clase

El documento reserva `equipment/infrastructure/input/listeners/` para el oyente de RF-26, y **ahí no
podía estar**: habría creado un ciclo entre `equipment` y `report`. No es una frase que envejeció; es
una decisión de arquitectura escrita en el documento sin comprobarla contra el grafo de dependencias.
Registrado en [[estado-de-la-ers-caducado]], con la anotación de RF-27, que era falsa en la mitad.

**Tocadas**: [[estado-de-implementacion]], [[rf-hojas-vida]], [[estado-de-la-ers-caducado]].

## [2026-10-05] lint | el cuerpo de rf-hojas-vida seguía en el 3 de octubre

El resumen de [[rf-hojas-vida]] se actualizó el 2026-10-04 con la hoja de vida construida, y el cuerpo
no: seguía diciendo que el historial «no existe» y que RF-26 y RF-27 estaban previstos, con el oyente en
el directorio que la ERS le reservó. Corregido dejando constancia, y con un matiz que faltaba: RF-22
pide el **valor unitario de mantenimiento** y la hoja de vida no lo trae. El diseño impreso aprobado ese
día lo deja fuera por ser un precio interno, pendiente de confirmación.

**Tocadas**: [[rf-hojas-vida]].

## [2026-10-05] ingest | voltaje y amperaje ya no son del tipo de equipo

`V15` los movió al modelo, en su ficha técnica, y [[rf-hojas-vida]] los situaba en el tipo. Corregido
con constancia. **RF-22 sigue cubierto**: pide esos datos en la hoja de vida, no en una tabla concreta,
y la hoja de vida los imprime desde el modelo.

**Tocadas**: [[rf-hojas-vida]].


## [2026-10-10] ingest | RF-13 y RF-14, implementados: 21 de 31

Los protocolos de mantenimiento se construyeron en `malphasos/` (`V21` y `V22`): el catálogo con su
asignación a tipos de equipo, y la copia de los pasos en cada reporte preventivo. Entran **RF-13** tal
cual y **RF-14** con una interpretación escrita —solo el preventivo lleva protocolo, por decisión del
usuario—. El marcador pasa de 19 a 21. **El `.tex` de la ERS no se tocó**: su campo de estado sigue
diciendo «Previsto», y es otra de las afirmaciones que [[estado-de-la-ers-caducado]] recoge.

**Tocadas**: [[estado-de-implementacion]], [[rf-reportes-mantenimiento]].

## [2026-10-16] ingest | los equipos patrón existen: RF-40 y RF-41, corregidos

La primera versión funcional construyó en `malphasos/` un catálogo de equipos patrón con certificado y
vencimiento (`V25`), y el reporte copia el que se usó (`V27`). [[rf-alertas-calibracion]] decía que
nada de eso estaba modelado: corregido, con el estado de cada criterio de aceptación de RF-40 —uno
cumplido, uno a medias, uno no—. **El marcador no se mueve: 21 de 31.** Y la ERS suma dos afirmaciones
caducadas más, en RF-40 y RF-41, que [[estado-de-la-ers-caducado]] recoge: el `.tex` no se tocó.

**Tocadas**: [[rf-alertas-calibracion]], [[estado-de-la-ers-caducado]].

## [2026-10-16] ingest | el reporte se imprime, y RF-17 sigue previsto

`malphasos/` construyó el formato del reporte que la empresa ya entrega, con un documento que se imprime
o se guarda en PDF **desde el navegador**. [[rf-reportes-mantenimiento]] decía de RF-17 que no había
código que produjera documentos: corregido. **RF-17 sigue previsto**, porque depende de RF-21 y pide el
reporte firmado. [[rf-alertas-calibracion]] se matiza: el reporte copia ya varios patrones por
verificación (`V30`), con su fecha de calibración (`V29`). **El marcador no se mueve: 21 de 31.** El
`.tex` de la ERS no se tocó.

**Tocadas**: [[rf-reportes-mantenimiento]], [[rf-alertas-calibracion]].

## [2026-10-17] ingest | la versión 1.0.0: 24 de 31, y la ERS al día

`malphasos/` construyó la firma de quien recibe y su réplica —RF-18 y RF-21— y el PDF que genera el
servidor —RF-17—. **El marcador sube de 21 a 24**, recontado criterio por criterio contra el `.tex`, con
tres matices que la ERS recoge: RF-18 es una aplicación web y no nativa, RF-21 no replica a los reportes
ya firmados ni a los retirados, y el membrete de RF-17 sale de la configuración y no del código.

**Y la ERS se puso al día**, que es lo que [[estado-de-la-ers-caducado]] pedía desde el 2026-10-04: sus
marcas decían ocho implementados, los de la primera revisión. Se cambiaron las dieciséis que faltaban,
sus anotaciones de estado, la tabla resumen, el párrafo de la revisión y los cierres de categoría, y se
corrigieron tres afirmaciones más que el código desmentía —RF-47, RF-53 y el cierre de Usuarios y
seguridad—. Al escribir la de RF-40 dije que el catálogo de patrones no marcaba los próximos a vencer, y
[[rf-alertas-calibracion]] me corrigió antes de commitear: los marca. El PDF se recompiló: 46 páginas.

**Y la lista de [[estado-de-implementacion]] se titulaba «Los 17»** con diecinueve filas y la cifra en
21. Ahora tiene las veinticuatro, cruzadas una a una con las marcas del `.tex`.

**Tocadas**: [[estado-de-implementacion]], [[rf-firma-digital]], [[rf-reportes-mantenimiento]],
[[estado-de-la-ers-caducado]], [[index]].

## [2026-10-27] ingest | Los diagramas de la base de datos, y el plan de V41

Entra `Documentation/DatabaseDesign/`, pedido por el usuario para revisar un cambio de esquema antes de
escribir la migración: un encargado que responde por varias sedes y áreas. Tres diagramas PlantUML
—la base de hoy completa, el cambio y la base como quedaría—, **generados** desde el esquema vivo de
desarrollo en `V40` y no dibujados a mano. Lo propuesto va marcado «PROPUESTO» en cada título, porque
no está implementado. Nota nueva, [[diseno-base-de-datos]].

**Y el pie de [[index]] decía 28 notas «actualizado el 2026-10-04»** con 28 notas reales: el número
seguía siendo cierto y la fecha no. Ahora son 29, contadas.

## [2026-10-27] ingest | V41 implementada

El plan de [[diseno-base-de-datos]] se construyó el mismo día, sin cambios sobre lo dibujado. La nota lo
dice con su fecha, y los diagramas se quedan como estaban: son la foto del plan aprobado, y su título
«PROPUESTO» es el de ese momento. Ningún documento oficial cambia: ningún requisito de la ERS describe
cuántas sedes puede tener a cargo un encargado.

## [2026-10-27] ingest | El plan de V42: el recibido por sede o por área

Segunda carpeta de [[diseno-base-de-datos]], `V42-recibido-por-orden/`, generada igual que la de `V41`
desde el esquema vivo, ahora en `V41`. Marca el cambio como «PROPUESTO». Ningún documento oficial cambia
todavía; cuando se construya, RF-18 y RF-21 —la firma de quien recibe y su réplica a la orden— cambian de
forma, y la ERS lo tendrá que decir.

## [2026-10-27] ingest | El análisis del esquema y la refactorización a 3FN

Entran `DatabaseDesign/analisis-2026-10-27.md` y `DatabaseDesign/refactor-3fn/`. El usuario pidió revisar
la base antes de seguir: tercera forma normal y los menos ciclos posible. Sus decisiones y el orden de las
seis migraciones están en el análisis; el recibido por orden pasa de `V42` a `V47`. Ver
[[diseno-base-de-datos]], que recoge también la corrección del recuento de ciclos.

## [2026-10-27] ingest | El documento congelado sustituye a las copias

Decidido por el usuario sobre el análisis de [[diseno-base-de-datos]]: las copias del reporte se
sustituyen por su documento congelado al cerrar, el área de cada equipo en la orden se quita bloqueando
el traslado mientras la orden viva, y la orden se congela al ejecutarse. El plan pasa de seis migraciones
a ocho, `V42` a `V49`, y el recibido por orden de `V47` a `V49`. Los diagramas de `refactor-3fn/` se
regeneraron. Ningún documento oficial cambia todavía.

## [2026-10-27] ingest | El recibido se queda en el reporte

El plan de `V42-recibido-por-orden/` queda descartado: el usuario decidió que quien recibe es cualquier
persona, sin relación con el área, y que se firma un reporte, un área o la orden entera sobre las mismas
columnas de `V39`. El plan baja a siete migraciones, `V42` a `V48`, y `refactor-3fn/` a dos carpetas. Ver
[[diseno-base-de-datos]]. Cuando se construya, RF-21 —la réplica de la firma a la orden— gana la opción
del área, y la ERS lo tendrá que decir.

## [2026-10-27] ingest | Sin tabla puente entre la orden y el equipo

El usuario propuso quitar `orden_trabajo_equipo` y que el reporte una la orden con el equipo. El análisis
de [[diseno-base-de-datos]] lo recoge con sus cuatro reglas y lo mete en `V46`; los diagramas de
`refactor-3fn/` se regeneraron y la propuesta final baja a 36 tablas. Cuando se construya, RF-09 —un
reporte por equipo de la orden— pasa a cumplirse por construcción, y la ERS no necesita cambiar.

## [2026-10-27] ingest | La posición de un paso del reporte también era copia

`paso_reporte.i_orden` copiaba la prioridad del protocolo en el tipo. Sale en `V47` con las otras copias
del reporte, y su regla única pasa de «no repetir posición» a «no repetir protocolo». Recogido en el
análisis de [[diseno-base-de-datos]], con los diagramas regenerados.

## [2026-10-27] ingest | El plan de la refactorización, renumerado al construirlo

El `V43` del análisis de [[diseno-base-de-datos]] juntaba los teléfonos, la ciudad y las imágenes de la
empresa, y se partió en tres al construirlo —`V43`, `V44` y `V45`—: el plan va ahora de `V42` a `V50`.
`V42` y `V43` están construidas. El diagrama de la propuesta final dibuja `telefono_empresa` como se
construyó, sin identificador ni borrado lógico.

## [2026-10-27] ingest | V44 construida

La ciudad de la empresa sale del catálogo. El análisis de [[diseno-base-de-datos]] lo marca en C2.
Ningún documento oficial cambia: ningún requisito de la ERS describe la ciudad de la empresa.

## [2026-10-27] ingest | V45 construida

El logo de la empresa y las firmas se guardan por huella en `imagen_documento`. El análisis de
[[diseno-base-de-datos]] lo marca en C1. Ningún documento oficial cambia.

## [2026-10-28] ingest | V46 construida

El segundo tipo de persona sale; es encargado quien tiene asignaciones, y solo pueden tenerlas
encargados y representantes del cliente. El análisis de [[diseno-base-de-datos]] lo marca en A2. Ningún
documento oficial cambia todavía, aunque el glosario de la ERS que describa al encargado tendrá que
decirlo.

## [2026-10-28] ingest | V47 construida, y el plan hasta V51

La orden llega a su cliente por la sede (A3 del análisis de [[diseno-base-de-datos]]). El `V47` del plan
juntaba esto con la verificación por la unidad, y se partió: la verificación es `V48`, y las tres
últimas pasan a `V49`–`V51`. Ningún documento oficial cambia.

## [2026-10-28] ingest | V48 construida

La verificación llega a su magnitud por la unidad (A4 del análisis de [[diseno-base-de-datos]]). La nota
decía que el plan eran **siete** migraciones, `V42` a `V48`: caducó al partirse dos de ellas, y se corrige
a diez, `V42` a `V51`, con siete construidas. Ningún documento oficial cambia.

## [2026-10-28] ingest | V49 construida, y el plan hasta V52

La orden se congela al ejecutarse (B6 y «la orden también se congela» del análisis de
[[diseno-base-de-datos]]). El `V49` del plan juntaba esto con quitar la tabla puente, y se partió: el
puente es `V50`, y las dos últimas pasan a `V51` y `V52`. El análisis recoge además dos decisiones del
usuario: el botón «Sustituir» y que se puede sustituir con la orden ejecutada. Ningún documento oficial
cambia.

## [2026-10-28] ingest | V50 construida

Sin tabla puente entre la orden y el equipo: un equipo está en una orden si y solo si tiene en ella un
reporte activo. El análisis de [[diseno-base-de-datos]] recoge cómo quedó al construirlo: retirar un
borrador es quitar su equipo de la orden, un cerrado solo se sustituye, el borrador no se escribe hasta
que la orden empieza y el traslado solo se bloquea si el área cambia. Ningún documento oficial cambia, y
ningún requisito se mueve: RF-09 sigue diciendo «un reporte por cada equipo seleccionado», que ahora es
literal.

## [2026-10-28] ingest | V51 construida

El reporte cerrado vive en su documento: salen las copias del catálogo de sus filas (B1, B2 y B3 del
análisis de [[diseno-base-de-datos]]) y el documento de cada reporte cerrado pasa a la versión 2 con lo que
guardaban. El análisis recoge las decisiones de detalle: la tolerancia que se copió en las lecturas manda
sobre la del tipo, la migración se detiene ante un cerrado sin documento y un paso se marca por su
protocolo. Ningún documento oficial cambia, y ningún requisito se mueve.

## [2026-10-28] ingest | V52 construida: la refactorización está completa

Sin `intervencion`: el historial de la hoja de vida sale de los reportes cerrados, una línea por par
(orden, equipo). Con ella terminan las once migraciones del análisis de [[diseno-base-de-datos]], de `V42`
a `V52`: 36 tablas, ningún ciclo dirigido y ninguna foránea sin índice. Ningún documento oficial cambia, y
ningún requisito se mueve: RF-26 y RF-27 se siguen cumpliendo, con el historial derivado en vez de copiado.

## [2026-10-30] ingest | RF-21 con tres alcances

La réplica de la firma de quien recibe deja de ser una casilla de la orden: se firma un reporte, los del
área del equipo en la orden o todos los de la orden. Lo pedía el análisis de [[diseno-base-de-datos]], y
no lleva migración. RF-21 sigue implementado; cambia cómo se elige a cuántos reportes va. Ver
[[rf-firma-digital]].

## [2026-11-22] ingest | RF-40 y RF-41: la campanita no los cumple

El frontend avisa desde el 2026-11-21 de los patrones vencidos o que vencen en 30 días. Se anota en
[[rf-alertas-calibracion]] por qué los dos requisitos siguen previstos: el aviso se calcula al mirar, sin
tarea diaria ni estado de alerta en el servidor.

