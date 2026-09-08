# CLAUDE.md — Documentación de MalphasOS

Este directorio contiene la documentación formal de **MalphasOS**: la Especificación de Requisitos de Software (ERS) bajo IEEE 830, el documento de constitución, el plan de gestión del alcance, la matriz de trazabilidad, sus diagramas y los manuales que hagan falta.

## Entra por la wiki

Todo lo que hace falta saber para escribir o corregir un documento de aquí —qué pide cada requisito, cómo se llama cada convención LaTeX, qué significa cada término del dominio, qué defectos ya se conocen— está destilado en **`wiki/`**, con el mismo patrón que `SecondBrain/` en la raíz del proyecto.

**Empieza por [`wiki/index.md`](wiki/index.md).** Las convenciones de la wiki misma —frontmatter, wikilinks, flujo de trabajo— están en [`wiki/CLAUDE.md`](wiki/CLAUDE.md).

## La regla que gobierna todo lo que se escribe aquí

**No se documenta como existente algo que no está implementado.** Antes de afirmar que MalphasOS hace algo, se comprueba en `malphasos/`. Si no está, se marca **previsto** o **fuera del alcance actual**. Ver [`wiki/regla-implementado-vs-previsto.md`](wiki/regla-implementado-vs-previsto.md).

## Dónde se trabaja

Worktree propio sobre la rama `MalphasOS-Documentation`; `malphasos/` y `SecondBrain/` son de solo lectura, incluso la copia que se ve desde aquí. Micro-commits en ramas `docs/<tema>`, sin mergear a `main`. El detalle completo —convenciones de commit, compilación, estructura de carpetas— está en la wiki, no aquí: este archivo se carga entero al abrir el worktree y debe orientar en treinta segundos, no contarlo todo.
