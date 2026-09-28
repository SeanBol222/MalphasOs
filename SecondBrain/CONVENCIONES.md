# SecondBrain de MalphasOS — Schema del wiki

Este directorio es un **Second Brain** construido con el patrón "LLM Wiki" (Andrej Karpathy). Es la referencia técnica de **MalphasOS**, la aplicación de gestión de mantenimientos preventivos y clientes extraída de `bolivarbioingenieria-app`.

## El propósito cambió el 2026-09-09, y conviene saber cuál era

Este wiki **nació para responder una pregunta que ya está contestada**: *qué portar de `bolivarbioingenieria-app`*. Por eso su eje de juicio era la etiqueta `reusable:*` y por eso su índice estaba ordenado por las categorías técnicas del sistema viejo. **Esa migración terminó**: los cuatro módulos —`person`, `location`, `client`, `equipment`— están completos.

Quien lo consulta hoy pregunta otras dos cosas:

1. **¿Cómo funciona MalphasOS?** — antes de construir encima, para no re-decidir lo ya decidido.
2. **¿Qué falta por construir?** — [[hoja-de-ruta-producto]].

El wiki sigue conteniendo, y debe seguir conteniendo, la descripción del sistema original: **es lo que explica de dónde viene cada decisión**. Lo que cambia es que deja de ser el sujeto y pasa a ser el contexto. **No se borra ninguna nota por haber quedado histórica**; se marca como tal.

## Las tres capas

1. **Raw source** (inmutable, nunca se modifica desde aquí): el repositorio `bolivarbioingenieria-app` en `/home/sean-omarchy/Documents/UDistrital/SeptimoSemestre/IngenieriaDeRequerimientos/bolivarbioingenieria-app`. Toda nota de este wiki que cite código debe referenciar rutas relativas a ese repo (ej. `Backend/sigma-bb/src/main/java/.../EquipmentService.java`). **Ninguna sesión de Claude debe escribir, editar ni mover archivos dentro de ese repo desde este proyecto.** Si en el futuro se necesita tocar ese repo, es una tarea completamente distinta y explícita del usuario, no una consecuencia de mantener este wiki.
2. **El wiki** (`wiki/`): notas markdown atómicas, generadas y mantenidas por el LLM. Cada nota tiene frontmatter YAML y enlaces `[[wikilink]]` a notas relacionadas.
3. **El schema** (este archivo): las convenciones y flujos de trabajo que siguen ambas partes.

## Estructura de `wiki/`

- `overview/` — visión general del sistema original y su stack completo, más [[sintesis-malphasos]], la tesis de qué reutilizar, **cerrada el 2026-09-09** con el registro de cómo salió.
- `arquitectura/` — patrones transversales del backend: hexagonal, CQRS por commands, eventos de dominio, manejo de excepciones, seguridad, OpenAPI.
- `dominio/` — una nota por bounded context (hexágono): cliente, persona, ubicación, equipo/mantenimiento, reportes. Incluye modelos, casos de uso, y ambigüedades de diseño detectadas.
- `base-de-datos/` — esquema PostgreSQL actual y su evolución histórica.
- `frontend/` — arquitectura React y su integración con Keycloak.
- `infraestructura/` — docker-compose, configuración de Keycloak.
- `patrones-reutilizables/` — patrones de implementación atómicos (mappers, catálogos de error, soft-delete, etc.) que aplican transversalmente a varios hexágonos, más un registro explícito de deuda técnica y riesgos conocidos.
- `malphasos/` — lo propio de MalphasOS: la hoja de ruta, el registro de decisiones, los hallazgos de cada migración y el modelo de permisos.

**Los directorios no dicen de qué sistema habla cada nota** —una nota de `dominio/` puede describir el hexágono original, el módulo de MalphasOS, o el camino de uno al otro—. Eso lo dice la etiqueta `describe:*` del frontmatter, y es por lo que `index.md` está ordenado.

## Convenciones de frontmatter

```yaml
---
name: kebab-slug-unico
description: una línea, específica
tags: [categoria1, categoria2, "describe:malphasos|original|ambos"]
source: ruta relativa dentro de bolivarbioingenieria-app, o dentro de malphasos/   # opcional
estado: estable | deuda-tecnica | incompleto | inconsistente   # opcional, marca si lo documentado tiene problemas conocidos
updated: YYYY-MM-DD
---
```

### `describe:*` — de qué sistema habla la nota (obligatoria)

Es el eje por el que se ordena `index.md`. Responde a "¿esto me sirve para construir, o me explica de dónde viene algo?".

- `describe:malphasos` — el sujeto es **MalphasOS tal como es hoy**, o algo aprendido construyéndolo. Se consulta antes de escribir código.
- `describe:original` — el sujeto es **`bolivarbioingenieria-app`** y nada de eso está construido aquí. Valor histórico y de referencia para lo que aún no existe.
- `describe:ambos` — la nota **sigue una pieza del original hasta MalphasOS**: qué había, qué se corrigió y cómo quedó. Es la mayoría, y son las notas que más se usan.

Reparto al **2026-09-12**: **32** `ambos`, **8** `original`, **9** `malphasos` — **49 notas**, contadas sobre el frontmatter. (Al 2026-09-09 eran 31 / 8 / 8 y 47; entraron [[dominio-orden-trabajo]] y [[congelar-una-referencia-historica]] con la primera tanda de órdenes de trabajo.)

### `reusable:*` — congelada, se conserva, no se aplica a notas nuevas

Fue el criterio central del wiki mientras la pregunta era *qué portar*. **Hoy está gastada**: sobrevive en las notas anteriores al 2026-09-09 y **no se retira**, porque es el registro fechado del juicio con el que se decidió portar cada pieza, y borrarla en silencio sería exactamente lo que este wiki prohíbe.

Reglas de uso a partir de ahora:

- **No se pone en notas nuevas.** Una nota sobre MalphasOS no tiene nada que "portar".
- **No se actualiza** en las viejas: es un dato de 2026-08/09, no una afirmación sobre el presente.
- **No se usa para ordenar ni para decidir nada.** Para eso está `describe:*` y, si la pregunta es qué construir, [[hoja-de-ruta-producto]].
- Si aparece en una nota que describe MalphasOS —hay dos o tres, con el sentido corrido de "aplicable a otros módulos"—, es una señal de que la etiqueta se desgastó, no una instrucción.

Su significado original, para leer las notas viejas: `alta` = portar casi sin cambios; `media` = sirve corrigiendo algo conocido o adaptándolo; `baja` = referencia, el contenido concreto es de otro dominio; `no` = explícitamente no portar.

## Enlaces

Usa `[[nombre-de-nota]]` (el `name` del frontmatter, sin extensión) para enlazar. Enlaza generosamente — una nota que menciona un concepto sin nota propia todavía es una nota pendiente de crear, no un error.

## Índice y log

- `index.md` es el catálogo de contenido, **ordenado por la pregunta con la que se llega** —qué falta, cómo funciona hoy, cómo se llegó, de dónde viene— y no por la categoría técnica del sistema viejo. Toda nota nueva se agrega bajo la sección que corresponda a su `describe:*`, con enlace y resumen de una línea. Al responder una consulta, lee primero `index.md` para ubicar las notas relevantes antes de abrir cada una. **Actualiza también el conteo del pie.**
- `log.md` es el registro cronológico append-only. Cada entrada empieza con `## [YYYY-MM-DD] tipo | tema` donde `tipo` es `ingest`, `query` o `lint`. Esto lo hace parseable con `grep "^## \[" log.md`.

## Flujos de trabajo

**Ingest** (cuando el código fuente cambió y hay que re-sincronizar el wiki, o cuando se explora una parte del sistema no cubierta aún):
1. Leer el código relevante en el repo raw (nunca modificarlo).
2. Actualizar o crear las notas afectadas en `wiki/` — puede tocar varias notas a la vez (una nota de dominio, una de patrón, la síntesis de MalphasOS).
3. Actualizar `index.md`.
4. Agregar una entrada a `log.md`.

**Query** (el usuario pregunta algo para usar en MalphasOS):
1. Leer `index.md`, ubicar notas relevantes.
2. Leer esas notas (y sus enlaces si hace falta profundizar).
3. Responder citando qué notas se usaron. Si la respuesta genera contenido nuevo con valor duradero (una comparación, un checklist, una decisión), considerar archivarlo como nota nueva en vez de dejarlo solo en el chat.
5. Agregar una entrada `query` a `log.md` si la consulta produjo una nota nueva o un cambio relevante al wiki (no hace falta loguear preguntas triviales de lectura).

**Lint** (cuando el usuario lo pida explícitamente, ej. "revisa la salud del wiki"):
Buscar contradicciones entre notas, notas huérfanas (sin enlaces entrantes), afirmaciones desactualizadas frente al código actual, y conceptos mencionados repetidamente que aún no tienen nota propia. Reportar hallazgos y, si el usuario lo confirma, corregir.

## Regla permanente: el wiki se mantiene solo, sin que lo pidan

**Siempre que se haga un cambio grande en MalphasOS, o se descubra algo relevante que no esté ya documentado aquí, hay que actualizar el wiki en la misma sesión, sin esperar a que el usuario lo pida.** Esto incluye:

- Bugs o inconsistencias descubiertos en `bolivarbioingenieria-app` → añadir a [[deuda-tecnica-y-riesgos]] y a la nota del área correspondiente.
- Afirmaciones del wiki que resulten **falsas** al verificarlas contra el código → corregirlas explícitamente, dejando constancia de que se corrigieron y cuándo.
- Decisiones técnicas tomadas al construir MalphasOS → registrar en [[decisiones-tecnicas-malphasos]] con su justificación.
- Conocimiento técnico nuevo que no se deduce del proyecto original (versiones, APIs que cambiaron, comportamientos del framework) → nota propia, como [[stack-spring-boot-4-particularidades]].
- Progreso real del proyecto → marcar en [[hoja-de-ruta-producto]]. [[checklist-reutilizacion]] es el registro **cerrado** de la migración y no se convierte en una segunda lista de tareas.

Y siempre: actualizar `index.md` y agregar una entrada a `log.md`. Un wiki que no refleja el estado real del conocimiento deja de servir como referencia, que es exactamente el problema que este patrón busca evitar.

## Reglas duras

- Este wiki **describe MalphasOS**, **registra** sus decisiones y **conserva** la descripción del sistema original como contexto histórico. La construcción de MalphasOS ocurre en `../malphasos/`, no aquí.
- **Ninguna nota se borra por haber quedado histórica.** Reclasificar sí; borrar no. Lo mismo con las filas de [[deuda-tecnica-y-riesgos]]: una deuda cerrada se marca como resuelta con su fecha, no se suprime.
- Nunca escribir, mover ni borrar archivos fuera de `/home/sean-omarchy/Documents/BolivarBioIngenieria/MalphasOS/SecondBrain/` como parte del mantenimiento de este wiki.
- **No se escribe el hash de una rama, solo el de un commit.** «614 medidos sobre `b9563a9`» o «mergeada por `1ef55cf`» nombran algo inmutable y se quedan; «`main` está en `b9563a9`» nombra el valor de hoy de un puntero que se mueve y **caduca sola** — el `CONVENCIONES.md` de la raíz lo fijó cuatro veces en dos días, y la última caducó **por su propio merge**: la pasada que anotaba dónde estaba `main` lo movía al mergearse. Una rama se describe por **contenido**; su hash lo da `git`.
- Cuando una nota documenta un bug o inconsistencia real detectada en el código fuente (hay varios, ver [[deuda-tecnica-y-riesgos]]), decirlo explícitamente — el valor de este wiki depende de no idealizar el sistema original.
