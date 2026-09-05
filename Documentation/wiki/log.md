# Log — Wiki de la documentación de MalphasOS

Registro cronológico append-only. Formato: `## [YYYY-MM-DD] tipo | tema`, con `tipo` en `ingest`, `query` o `lint`. Parseable con `grep "^## \[" log.md`.

## [2026-09-05] ingest | Construcción inicial de la wiki

Primera versión completa. Destilada de los cuatro documentos oficiales existentes en `Documentation/` —`IEEE830/IEEE830.tex`, `constitutionDocument/constitutionDocument.tex`, `ScopeManagementPlan/PlanDeGestionDelAlcance.tex` y `TraceabilityMatrix/MatrizDeTrazabilidad.tex`— y contrastada de nuevo contra `malphasos/` para la correspondencia terminológica y para verificar que ningún estado citado había quedado desactualizado desde el 5 de septiembre.

`Documentation/CLAUDE.md` deja de llevar las convenciones, los datos del proyecto y la estructura de carpetas — todo eso se muda a notas propias de esta wiki — y pasa a ser una puerta de entrada corta que apunta a `wiki/index.md`.

Un hallazgo de la construcción, no de la documentación previa: el correspondiente enum `RoleType` del módulo `person` (`malphasos/src/main/java/.../person/domain/person/RoleType.java`) documenta explícitamente en su Javadoc que **no es lo mismo que `PersonType`**, aunque comparten tres nombres por coincidencia. Es la mejor fuente de código para la nota [[correspondencia-terminologica]]: el propio código ya advierte del riesgo de confusión que esta wiki existe en parte para resolver.

No se modificó ningún `.tex` ni ningún archivo de `SecondBrain/` o `malphasos/` durante esta construcción.
