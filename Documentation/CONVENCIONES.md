# CONVENCIONES.md — Documentación de MalphasOS

Este directorio contiene la documentación formal de **MalphasOS**: la Especificación de Requisitos de Software (ERS) bajo IEEE 830, el documento de constitución, el plan de gestión del alcance, la matriz de trazabilidad, sus diagramas y los manuales que hagan falta.

## Entra por la wiki

Todo lo que hace falta saber para escribir o corregir un documento de aquí —qué pide cada requisito, cómo se llama cada convención LaTeX, qué significa cada término del dominio, qué defectos ya se conocen— está destilado en **`wiki/`**, con el mismo patrón que `SecondBrain/` en la raíz del proyecto.

**Empieza por [`wiki/index.md`](wiki/index.md).** Las convenciones de la wiki misma —frontmatter, wikilinks, flujo de trabajo— están en [`wiki/CONVENCIONES.md`](wiki/CONVENCIONES.md).

## La regla que gobierna todo lo que se escribe aquí

**No se documenta como existente algo que no está implementado.** Antes de afirmar que MalphasOS hace algo, se comprueba en `malphasos/`. Si no está, se marca **previsto** o **fuera del alcance actual**. Ver [`wiki/regla-implementado-vs-previsto.md`](wiki/regla-implementado-vs-previsto.md).

## Dónde se trabaja

Micro-commits en ramas `docs/<tema>` desde `main` actualizado, y merge con `--no-ff`, igual que el resto del proyecto. El detalle completo —convenciones de commit, compilación, estructura de carpetas— está en la wiki, no aquí: este archivo debe orientar en treinta segundos, no contarlo todo.

> **Corregido el 2026-09-13.** Aquí decía que se trabajaba en un «worktree propio sobre la rama `MalphasOS-Documentation`», con ramas que **no se mergeaban a `main`**. Esa rama y ese worktree **ya no existen**: se retiraron junto con los subagentes el 2026-09-12, y desde entonces `Documentation/` se trata como el resto del repositorio. La regla que sí sigue viva es la de más arriba: no se documenta como existente algo que no está implementado.
