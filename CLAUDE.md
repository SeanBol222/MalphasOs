# MalphasOS — workspace root

MalphasOS es la aplicación de **gestión de clientes** y **gestión de mantenimientos preventivos** extraída de `bolivarbioingenieria-app`. Este directorio es el workspace raíz y contiene dos cosas con propósitos distintos:

```
MalphasOS/
├── malphasos/       -> el proyecto real: backend Spring Boot (groupId com.malphasos, Spring Boot 4.1.1, Java 21)
└── SecondBrain/      -> wiki técnico de referencia (patrón LLM Wiki), NO es parte del código del proyecto
```

## Antes de tomar cualquier decisión de arquitectura, modelo de dominio o patrón de implementación

Consulta **`SecondBrain/`** — es un vault Obsidian con ~30 notas interconectadas que documentan y evalúan la arquitectura de `bolivarbioingenieria-app` (el proyecto del que MalphasOS se está extrayendo), con un juicio explícito de qué patrones portar tal cual, cuáles adaptar, y cuáles evitar.

Punto de entrada: lee `SecondBrain/index.md` (catálogo de notas) y `SecondBrain/CLAUDE.md` (convenciones del wiki) antes de abrir notas individuales. Las notas más importantes para arrancar el código de `malphasos/` son:

- `SecondBrain/wiki/overview/sintesis-malphasos.md` — la tesis general de qué reutilizar y por qué.
- `SecondBrain/wiki/malphasos/alcance-malphasos.md` — qué entra a MalphasOS módulo por módulo.
- `SecondBrain/wiki/malphasos/checklist-reutilizacion.md` — orden priorizado de trabajo.
- `SecondBrain/wiki/dominio/dominio-equipo-mantenimiento.md` — el núcleo de negocio (mantenimiento preventivo) y la plantilla arquitectónica principal a seguir (agregados ricos + commands + eventos de dominio).
- `SecondBrain/wiki/patrones-reutilizables/deuda-tecnica-y-riesgos.md` — bugs e inconsistencias conocidas del proyecto original; no asumir que algo documentado ahí ya funciona correctamente solo por estar en el código fuente.

## Regla importante

`SecondBrain/` es de solo lectura conceptual para el trabajo de código: se actualiza como wiki (agregando/editando notas cuando se explora algo nuevo del proyecto original o se documenta una decisión de MalphasOS), pero **no es el lugar donde vive el código de MalphasOS**. El código de MalphasOS vive exclusivamente en `malphasos/`.

## Estado actual (2026-08-27)

`malphasos/` es un proyecto Spring Boot recién generado con Spring Initializr (`spring-boot-starter-amqp`, `data-jpa`, `security-oauth2-resource-server`, `validation`, `webmvc`, `postgresql`, `lombok`) — todavía sin estructura de paquetes de dominio, sin MapStruct/springdoc/keycloak-admin-client añadidos al `pom.xml`, y sin ningún hexágono implementado aún. No hay convenciones de código propias de MalphasOS todavía más allá de las heredadas del `pom.xml` — para eso, usar el SecondBrain como referencia hasta que este mismo archivo se actualice con las convenciones reales una vez el proyecto tenga código.
