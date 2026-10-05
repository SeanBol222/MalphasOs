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

