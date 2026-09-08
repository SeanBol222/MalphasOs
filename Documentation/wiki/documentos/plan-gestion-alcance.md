---
name: plan-gestion-alcance
description: Qué es el Plan de Gestión del Alcance de MSO, qué contiene y por qué no lleva un diccionario de la EDT aparte
tags: [documento, alcance, gestion-de-proyectos]
fuente: Documentation/ScopeManagementPlan/PlanDeGestionDelAlcance.tex
estado: vigente
updated: 2026-09-05
---

# Plan de Gestión del Alcance

- **Archivo**: `ScopeManagementPlan/PlanDeGestionDelAlcance.tex` (298 líneas) → `.pdf` (6 páginas)
- **Fecha**: 2 de septiembre de 2026
- **Siglas del proyecto**: MSO (es el único documento que las usa de forma sistemática)

## Qué hace este documento

Describe, para los cuatro procesos clásicos de gestión del alcance (PMBOK), **cómo MSO los ejecuta de hecho**, no cómo debería ejecutarlos un proyecto ideal. Cada proceso se responde en seis campos: Qué, Quién, Cómo, Cuándo, Dónde, Con qué.

| Sección | Proceso |
|---|---|
| 1 | Definición del alcance |
| 2 | Elaboración de la EDT/WBS |
| 3 | Verificación del alcance |
| 4 | Control del alcance |

## El alcance de MSO es la ERS, literalmente

El documento no redefine el alcance: toma como enunciado los 31 requisitos funcionales del apartado 3.2 de la ERS y sus exclusiones (apartado 1.2.2), y construye los cuatro procesos sobre ese material. Trae, en su nota preliminar, la misma tabla de categoría-por-categoría con conteo de implementados que resume [[estado-de-implementacion]].

## Sin diccionario de la EDT aparte, por decisión explícita

El apartado 2.1 lo declara: **MSO no mantiene un diccionario de la EDT como documento separado**. Su equivalente es el propio enunciado de cada requisito —que ya trae complejidad, criterios de aceptación y dependencias—, de modo que "el diccionario está dentro de la descomposición". No es un vacío sin explicar: es una decisión de diseño documentada.

## Todos los roles de gestión son la misma persona

El apartado 4.2 es explícito sobre esto: quien detecta un cambio de alcance (analista de requisitos), quien decide si entra (gerente de proyecto) y quien asume el costo (patrocinador) son la misma persona. El documento reconoce el riesgo que eso supone —nadie más revisa un entregable y puede decir que no cumple— y describe qué lo compensa parcialmente: criterios de aceptación redactados antes de construir, una batería de pruebas automatizada, y el contraste sistemático contra el código en vez de la opinión de quien construyó. Ver [[datos-del-proyecto]].

## Tres cambios pendientes que el propio plan deja abiertos

El apartado 4.3 registra, sin resolverlos, tres problemas de coherencia de la ERS descubiertos al escribir este plan:

1. RF-49 depende de sí mismo.
2. Hay dependencias declaradas hacia requisitos ausentes del apartado 3.2 (RF-20 desde RF-18, RF-35 desde RF-36/RF-37, RF-46 desde RF-45).
3. El apartado 2.2 de la ERS promete exportación a Excel que ningún requisito recoge.

La [[matriz-de-trazabilidad]], escrita tres días después, amplía el segundo punto a **18** referencias rotas en total. Ver [[defectos-conocidos-de-la-ers]].

## Notas relacionadas

[[ers-ieee830]] · [[estado-de-implementacion]] · [[matriz-de-trazabilidad]] · [[defectos-conocidos-de-la-ers]] · [[convenciones-latex]]
