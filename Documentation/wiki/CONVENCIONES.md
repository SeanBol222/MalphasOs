# Wiki de la documentación de MalphasOS — schema

Este directorio es un **Second Brain** construido con el mismo patrón "LLM Wiki" que `SecondBrain/` en la raíz del proyecto (Andrej Karpathy). Su único propósito: que **cualquier modelo, sin más contexto que esta wiki, pueda generar documentación oficial de MalphasOS correcta y coherente con lo ya escrito**.

No es la documentación oficial en sí — eso son los `.tex` de `IEEE830/`, `constitutionDocument/`, `ScopeManagementPlan/` y `TraceabilityMatrix/` —. Es el conocimiento destilado de esos documentos: qué pide cada requisito, qué decide cada convención, qué significa cada término, con puntero al `.tex` para cuando haga falta la redacción exacta.

## Qué documenta `SecondBrain/` y qué documenta esta wiki

`SecondBrain/` (raíz del proyecto) documenta y evalúa **el sistema y su código**. Esta wiki documenta **la documentación oficial**: la ERS IEEE 830, el documento de constitución, el plan de gestión del alcance y la matriz de trazabilidad — qué dicen, cómo están escritos, y dónde se contradicen entre sí o contra el código.

Las dos wikis se citan, no se duplican. Si una nota necesita un dato sobre el código (un endpoint, un agregado, una migración), cita `SecondBrain/` en vez de repetirlo; si `SecondBrain/` necesita un dato sobre un requisito o un objetivo, debería citar esta wiki.

## La regla que gobierna todo lo que documenta esta wiki, y todo lo que se escriba a partir de ella

**No se documenta como existente algo que no está implementado.** Antes de afirmar que MalphasOS hace algo, se comprueba contra `malphasos/`. Si no está, se marca **previsto** o **fuera del alcance actual**. Ver [[regla-implementado-vs-previsto]].

## Estructura de `wiki/`

- `proyecto/` — datos del proyecto, objetivos y criterios de éxito, interesados, riesgos e hitos. Todo procede del documento de constitución.
- `requisitos/` — los 31 requisitos funcionales por sus nueve categorías, los 23 no funcionales, los 6 de dominio, la priorización MoSCoW y el estado de implementación agregado. Proceden de la ERS, contrastados contra el código.
- `glosario/` — el vocabulario del dominio (sede, área de servicio, encargado...) y su correspondencia con el código y con el realm de Keycloak.
- `documentos/` — una nota por documento oficial: qué es, qué contiene, cómo está estructurado, dónde vive el `.tex`.
- `forma/` — cómo se escribe: macros LaTeX propios, paleta de colores, herramientas de compilación y control de versiones.
- `defectos/` — los defectos conocidos de la especificación (referencias rotas, huecos de trazabilidad, incoherencias) y de sus diagramas. Es lo que impide que un modelo repita los errores del original.

## Convenciones de frontmatter

```yaml
---
name: kebab-slug-unico
description: una línea, específica
tags: [categoria1, categoria2]
fuente: ruta/relativa/dentro/de/Documentation   # el .tex o .pdf del que se destiló la nota
estado: vigente | defecto-conocido | inferido   # inferido: no hay enunciado, se dedujo de otra fuente
updated: YYYY-MM-DD
---
```

`estado: defecto-conocido` marca una nota que documenta, precisamente, un problema de la especificación (un requisito sin objetivo, una referencia rota). `estado: inferido` marca contenido que no tiene enunciado propio en ningún documento y se dedujo por la mejor pista disponible — como los requisitos de dominio `RD-01` a `RD-07` —.

## Enlaces

`[[nombre-de-nota]]` con el `name` del frontmatter, sin extensión. Enlaza generosamente.

## Índice y log

- `index.md` es el catálogo — toda nota nueva se agrega ahí bajo su categoría. Léelo primero para ubicar las notas relevantes.
- `log.md` es el registro cronológico append-only, formato `## [YYYY-MM-DD] tipo | tema` con `tipo` en `ingest`, `query` o `lint`.

## Flujos de trabajo

**Ingest** (un `.tex` cambió, o se destila una parte de la documentación oficial aún no cubierta):
1. Leer el `.tex` o `.pdf` relevante en `Documentation/` (nunca modificarlos desde aquí — esta wiki no es dueña de los documentos oficiales, es dueña de su destilado).
2. Actualizar o crear las notas afectadas.
3. Actualizar `index.md`.
4. Agregar una entrada a `log.md`.

**Query** (alguien pregunta algo para escribir documentación oficial):
1. Leer `index.md`, ubicar notas relevantes.
2. Leer esas notas y sus enlaces.
3. Responder citando las notas usadas. Si la ubicación exacta del texto canónico importa, seguir el puntero al `.tex`.

**Lint** (cuando se pida explícitamente):
Buscar notas desincronizadas de los `.tex` que citan, defectos nuevos de la especificación descubiertos y no registrados, y notas huérfanas.

## Regla permanente: esta wiki se mantiene sola

Todo cambio a un documento oficial de `Documentation/` que afecte una afirmación de esta wiki se refleja aquí en la misma sesión que lo hizo, sin esperar a que se pida. Una nota que dice algo que el `.tex` ya no dice es peor que no tener nota.

## Reglas duras

- Esta wiki **describe y evalúa** los documentos oficiales de `Documentation/`. Escribirlos ocurre en `IEEE830/`, `constitutionDocument/`, `ScopeManagementPlan/` y `TraceabilityMatrix/`, no aquí.
- Nunca escribir fuera de `Documentation/wiki/` como parte de mantener esta wiki.
- Cuando una nota documenta un defecto real de la especificación, decirlo explícitamente y marcar `estado: defecto-conocido` — el valor de esta wiki depende de no idealizar los documentos que describe.
