# SecondBrain de MalphasOS — Schema del wiki

Este directorio es un **Second Brain** construido con el patrón "LLM Wiki" (Andrej Karpathy). Su único propósito es servir de referencia técnica reutilizable al construir **MalphasOS**, la nueva aplicación de gestión de mantenimientos preventivos y clientes que se extrae de `bolivarbioingenieria-app`.

No es documentación del proyecto original ni un plan de migración. Es un mapa interconectado de qué patrones, estructuras y decisiones existen hoy, con juicio explícito sobre qué vale la pena portar y qué no.

## Las tres capas

1. **Raw source** (inmutable, nunca se modifica desde aquí): el repositorio `bolivarbioingenieria-app` en `/home/sean-omarchy/Documents/UDistrital/SeptimoSemestre/IngenieriaDeRequerimientos/bolivarbioingenieria-app`. Toda nota de este wiki que cite código debe referenciar rutas relativas a ese repo (ej. `Backend/sigma-bb/src/main/java/.../EquipmentService.java`). **Ninguna sesión de Claude debe escribir, editar ni mover archivos dentro de ese repo desde este proyecto.** Si en el futuro se necesita tocar ese repo, es una tarea completamente distinta y explícita del usuario, no una consecuencia de mantener este wiki.
2. **El wiki** (`wiki/`): notas markdown atómicas, generadas y mantenidas por el LLM. Cada nota tiene frontmatter YAML y enlaces `[[wikilink]]` a notas relacionadas.
3. **El schema** (este archivo): las convenciones y flujos de trabajo que siguen ambas partes.

## Estructura de `wiki/`

- `overview/` — visión general del sistema, stack tecnológico completo, y la síntesis evolutiva de qué reutilizar en MalphasOS ([[sintesis-malphasos]] es la nota más importante del wiki, se actualiza en cada ingest relevante).
- `arquitectura/` — patrones transversales del backend: hexagonal, CQRS por commands, eventos de dominio, manejo de excepciones, seguridad, OpenAPI.
- `dominio/` — una nota por bounded context (hexágono): cliente, persona, ubicación, equipo/mantenimiento, reportes. Incluye modelos, casos de uso, y ambigüedades de diseño detectadas.
- `base-de-datos/` — esquema PostgreSQL actual y su evolución histórica.
- `frontend/` — arquitectura React y su integración con Keycloak.
- `infraestructura/` — docker-compose, configuración de Keycloak.
- `patrones-reutilizables/` — patrones de implementación atómicos (mappers, catálogos de error, soft-delete, etc.) que aplican transversalmente a varios hexágonos, más un registro explícito de deuda técnica y riesgos conocidos.
- `malphasos/` — alcance propuesto para el nuevo proyecto y checklist de reutilización priorizado.

## Convenciones de frontmatter

```yaml
---
name: kebab-slug-unico
description: una línea, específica
tags: [categoria1, categoria2, "reusable:alta|media|baja|no"]
source: ruta/relativa/dentro/de/bolivarbioingenieria-app   # opcional, solo si la nota describe código concreto
estado: estable | deuda-tecnica | incompleto | inconsistente   # opcional, marca si lo documentado tiene problemas conocidos
updated: YYYY-MM-DD
---
```

La etiqueta `reusable:*` es el criterio central de este wiki (no existe en el proyecto original, es una capa de juicio que este wiki añade): indica qué tan directamente aplica una pieza a MalphasOS.

- `reusable:alta` — portar casi sin cambios, solo renombrando (paquetes, client-id de Keycloak, nombres de tabla si aplica).
- `reusable:media` — el patrón sirve pero requiere corregir algo conocido (ver `estado: deuda-tecnica`) o adaptar a un dominio distinto.
- `reusable:baja` — sirve como referencia/inspiración pero el contenido concreto es específico del dominio actual (facturación, roles de este negocio, etc.).
- `reusable:no` — explícitamente no portar (ej. el patrón CRUD anémico de `client_hexagon`, ya superado dentro del propio repo original).

## Enlaces

Usa `[[nombre-de-nota]]` (el `name` del frontmatter, sin extensión) para enlazar. Enlaza generosamente — una nota que menciona un concepto sin nota propia todavía es una nota pendiente de crear, no un error.

## Índice y log

- `index.md` es el catálogo de contenido — toda nota nueva se agrega ahí bajo su categoría, con link + resumen de una línea + tag de reusabilidad. Al responder una consulta, lee primero `index.md` para ubicar las notas relevantes antes de abrir cada una.
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

## Reglas duras

- Este wiki **describe y evalúa** el sistema actual. **No planifica ni ejecuta** la migración/creación de MalphasOS a menos que el usuario lo pida explícitamente en una conversación futura — eso es un proyecto de código, no de documentación.
- Nunca escribir, mover ni borrar archivos fuera de `/home/sean-omarchy/Documents/BolivarBioIngenieria/MalphasOS/SecondBrain/` como parte del mantenimiento de este wiki.
- Cuando una nota documenta un bug o inconsistencia real detectada en el código fuente (hay varios, ver [[deuda-tecnica-y-riesgos]]), decirlo explícitamente — el valor de este wiki depende de no idealizar el sistema original.
