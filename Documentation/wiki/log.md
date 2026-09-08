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
