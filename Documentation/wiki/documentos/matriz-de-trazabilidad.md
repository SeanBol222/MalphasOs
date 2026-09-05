---
name: matriz-de-trazabilidad
description: Qué es la Matriz de Trazabilidad, cómo está construida como standalone y por qué es la primera que existe de verdad
tags: [documento, trazabilidad, hallazgos]
fuente: Documentation/TraceabilityMatrix/MatrizDeTrazabilidad.tex
estado: vigente
updated: 2026-09-05
---

# Matriz de Trazabilidad de Requisitos

- **Archivo**: `TraceabilityMatrix/MatrizDeTrazabilidad.tex` (527 líneas) → `.pdf`
- **Fecha**: 5 de septiembre de 2026 — el documento más reciente de los cuatro
- **Formato**: clase `standalone` (variante `varwidth`, ancho de contenido 170mm), no `article`. No pagina: se piensa para lectura con zoom o para plotter, no para imprimirse en A4. Por eso no usa `longtable` (pensado para partir tablas entre páginas) sino `tabular` simple, y sus macros `\sectiontitle`/`\subsectiontitle` son bloques de texto con el mismo tamaño de fuente que en la ERS, no `\section*` redefinido. Ver [[convenciones-latex]].

## Por qué existe

El apartado 3.6 de la ERS, titulado "Matriz de Trazabilidad", **no contiene una tabla**: contiene el marcador de posición `[MATRIZ DE TRAZABILIDAD COMPLETA - Ver páginas 37-38 del PDF]`. Y el apartado 1.2.4 de la ERS afirma que el proyecto mantiene consistencia con una matriz "de etapas previas del proyecto" que **no existe en el repositorio** — se buscó y no se encontró ningún archivo, tabla ni referencia que se le pareciera.

Este documento es, hasta donde alcanza la revisión que lo produjo, **la primera matriz de trazabilidad real de MalphasOS**.

## Qué encadena

Cada uno de los 60 requisitos de la ERS (31 RF + 23 RNF + 6 RD) con: el objetivo de la constitución al que sirve, el criterio de éxito que lo mediría, su prioridad MoSCoW, su estado de implementación y la evidencia concreta de ese estado. Formato de seis columnas por sesenta requisitos, partido en dos tablas encadenadas por código para cada tipo: objetivo/criterio/MoSCoW en una, estado/evidencia en otra.

## Regla de las celdas vacías

Un requisito solo lleva un código de objetivo o de criterio cuando su propio enunciado o sus criterios de aceptación coinciden con lo que ese objetivo o criterio miden. Donde no hay correspondencia defendible, la celda lleva `\hueco{}` (una raya `---`), no un código elegido por plausibilidad. **Un hueco es información**: dice que ese requisito no tiene la cadena objetivo→criterio→requisito completa que un proyecto bien trazado debería tener.

## Metodología: verificado de nuevo, no copiado

La evidencia de implementación se verificó otra vez contra `malphasos/` el 5 de septiembre de 2026 —controladores en `*/infrastructure/input/rest/`, agregados de dominio, las cinco migraciones `V1` a `V5`— en vez de copiar el estado que ya declaraba la ERS. Coincidió. Es la primera vez que se evalúa el estado de los RNF y los RD, que la ERS nunca marca.

## Un tercer estado que la ERS no tiene

`\noVerificable` — ver [[regla-implementado-vs-previsto]] — para los requisitos de dominio sin enunciado: no es previsto ni implementado, es indeterminable.

## La sección 4, Hallazgos

Es la sección más citada del resto de esta wiki. Registra siete categorías de defectos de la especificación descubiertos al construir la matriz, sin proponer cómo cerrarlos —"eso mezclaría lo que hay con lo que debería hacerse"—. El detalle completo está en [[defectos-conocidos-de-la-ers]]; en una línea: 18 referencias rotas, 15+11 requisitos sin objetivo, 2 criterios sin requisito que los mida, 6 RD sin enunciado, 10 requisitos reales sin MoSCoW, dos escalas de tiempo incompatibles bajo el mismo objetivo de rendimiento, y RF-49 dependiendo de sí mismo.

## Notas relacionadas

[[regla-implementado-vs-previsto]] · [[estado-de-implementacion]] · [[defectos-conocidos-de-la-ers]] · [[ers-ieee830]] · [[documento-constitucion]]
