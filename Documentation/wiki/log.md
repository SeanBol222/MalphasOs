# Log — Wiki de la documentación de MalphasOS

Registro cronológico append-only. Formato: `## [YYYY-MM-DD] tipo | tema`, con `tipo` en `ingest`, `query` o `lint`. Parseable con `grep "^## \[" log.md`.

## [2026-09-05] ingest | Creación de la Matriz de Interesados (standalone)

Construcción formal de `Documentation/StakeholdersMatrix/MatrizDeInteresados.tex` (compilada con `latexmk -pdf` a `MatrizDeInteresados.pdf`). Aplica la clase `standalone` (variante `varwidth=170mm`), la paleta institucional y la regla central de implementación. Encadena a los 8 interesados del Project Charter con los objetivos del proyecto (`OBJ-01` a `OBJ-07`), los requisitos de la ERS IEEE 830 y los 10 riesgos iniciales (`R-01` a `R-10`). Registra en su sección de hallazgos el riesgo estructural de unipersonalidad, la brecha de autorización técnica (`admin.full`), el desfase en valor para usuarios de campo y la tensión regulatoria por la exclusión de alertas de calibración.

Se añade la nota de wiki `documentos/matriz-de-interesados.md` y se actualiza el catálogo en `index.md`.

## [2026-09-05] ingest | Construcción inicial de la wiki

Primera versión completa. Destilada de los cuatro documentos oficiales existentes en `Documentation/` —`IEEE830/IEEE830.tex`, `constitutionDocument/constitutionDocument.tex`, `ScopeManagementPlan/PlanDeGestionDelAlcance.tex` y `TraceabilityMatrix/MatrizDeTrazabilidad.tex`— y contrastada de nuevo contra `malphasos/` para la correspondencia terminológica y para verificar que ningún estado citado había quedado desactualizado desde el 5 de septiembre.

`Documentation/CLAUDE.md` deja de llevar las convenciones, los datos del proyecto y la estructura de carpetas — todo eso se muda a notas propias de esta wiki — y pasa a ser una puerta de entrada corta que apunta a `wiki/index.md`.

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

**Corregido además el `CLAUDE.md` de este directorio**, que mandaba trabajar en un worktree sobre la rama `MalphasOS-Documentation`. Ni la rama ni el worktree existen desde el 2026-09-12.

**Tocadas**: [[paleta-de-colores]] (reescrita), `index.md`, `CLAUDE.md`. **Nuevas**: [[declaracion-diseno-frontend]], [[manual-de-marca]].
