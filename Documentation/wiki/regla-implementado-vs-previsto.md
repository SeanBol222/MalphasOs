---
name: regla-implementado-vs-previsto
description: La regla que gobierna toda la documentación oficial de MalphasOS, sus dos macros LaTeX y cómo se verifica
tags: [regla-central, ers, estado]
fuente: Documentation/IEEE830/IEEE830.tex
estado: vigente
updated: 2026-09-05
---

# No se documenta como existente algo que no está implementado

Es la regla más importante de toda la documentación de MalphasOS. La ERS heredada (el borrador de partida) describía módulos completos que el sistema nunca tuvo —órdenes de trabajo, firma digital, reportes, inventario, alertas de calibración, módulo comercial—. Antes de escribir que MalphasOS hace algo, se comprueba en `malphasos/`. Si no está, se marca **previsto** (sigue siendo parte del alcance comprometido, sin implementación) o **fuera del alcance actual**, nunca como un hecho consumado.

## Los dos macros

El apartado 3.2 de la ERS aplica la regla requisito por requisito con dos macros LaTeX propios, definidos en `IEEE830.tex` (también en `TraceabilityMatrix/MatrizDeTrazabilidad.tex`, que añade un tercero):

```latex
\newcommand{\estadoImplementado}{\textcolor{mainRed}{\textbf{[IMPLEMENTADO]}}}
\newcommand{\estadoPrevisto}{\textcolor{mainGray}{\textbf{[PREVISTO]}}}
```

El rojo (`mainRed`, `#EC3013`) es el color de acento del documento: lo construido resalta. El gris (`mainGray`, `#6b6866`) es el de los elementos atenuados: lo que falta queda en segundo plano. Ver [[paleta-de-colores]].

La matriz de trazabilidad añade un tercer estado que la ERS no tiene, para los seis requisitos de dominio sin enunciado (ver [[requisitos-de-dominio]]):

```latex
\newcommand{\noVerificable}{\textcolor{mainGray}{\textit{[NO VERIFICABLE]}}}
```

`[NO VERIFICABLE]` no es ni implementado ni previsto: es indeterminable, porque no hay enunciado del requisito contra el que contrastar el código. Usarlo en vez de forzar una de las otras dos marcas es, en sí mismo, aplicar la regla con honestidad.

## Dónde se aplica hoy

- **La ERS** marca así los 31 requisitos funcionales (apartado 3.2). Es la única categoría que la propia ERS evalúa; el texto aclara explícitamente que las marcas "se usan solo en este apartado".
- **La matriz de trazabilidad** (`TraceabilityMatrix/MatrizDeTrazabilidad.tex`) extiende la evaluación a los 23 requisitos no funcionales y a los 6 de dominio — la primera vez que se hace, el 5 de septiembre de 2026 —, y **repite la verificación de los 31 funcionales en vez de copiar el estado de la ERS**, confirmando que coincide.

## Cada marca cierra con evidencia, no con la palabra sola

Un requisito no se da por implementado porque a alguien se lo parezca. El apartado 3.2 de la ERS y la columna "Evidencia" de la matriz citan siempre algo verificable: la ruta del endpoint REST, la tabla del esquema, el agregado de dominio o el directorio vacío que hace de marcador de lo pendiente. Ejemplos reales:

- RF-08 (crear cliente): `POST /v1/api/clients` persiste en la tabla `cliente` a través del agregado `Client`.
- RF-01 (crear orden de trabajo): ninguna de las cinco migraciones (`V1` a `V5`) crea una tabla de orden de trabajo; no hay agregado ni ruta. El directorio `equipment/infrastructure/input/model/report/` está creado y vacío — es el sitio reservado, no una implementación.

## El resultado agregado

De 31 requisitos funcionales, **8 están implementados y 23 previstos**. De 23 no funcionales, **solo 1** (RNF-23, autenticación JWT). Ver [[estado-de-implementacion]] para el detalle completo con su evidencia.

## Marcas parciales: cuando el criterio de aceptación no se cumple del todo

`[IMPLEMENTADO]` no significa "cumple todos sus criterios de aceptación". Cuando un requisito está implementado pero un criterio concreto no se satisface, la ERS lo dice explícitamente junto a la marca, en vez de mentir por omisión. El caso más pesado es RF-50 (identificación de rol): está `[IMPLEMENTADO]` porque el sistema sí identifica el rol del token, pero el criterio "cada rol accede solo a sus funcionalidades" no se cumple — las 83 operaciones REST exigen la misma autoridad `admin.full`. Ver [[correspondencia-terminologica]] y `SecondBrain/wiki/malphasos/decisiones-tecnicas-malphasos.md` para el estado de la seguridad por roles.

## Notas relacionadas

[[estado-de-implementacion]] · [[ers-ieee830]] · [[matriz-de-trazabilidad]] · [[paleta-de-colores]]
