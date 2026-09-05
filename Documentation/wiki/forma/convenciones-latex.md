---
name: convenciones-latex
description: Macros propios, portada, margenes, tablas, enfasis y formatos de codigo que comparten los documentos de MalphasOS
tags: [forma, latex, convenciones]
fuente: "Documentation/IEEE830/IEEE830.tex, Documentation/ScopeManagementPlan/PlanDeGestionDelAlcance.tex"
estado: vigente
updated: 2026-09-05
---

# Convenciones LaTeX

Un documento nuevo de MalphasOS debe verse como hermano de la ERS y del plan de alcance, no como un documento distinto que comparte carpeta.

## Preámbulo de referencia

```latex
\documentclass[12pt,a4paper]{article}
\usepackage[margin=2.5cm]{geometry}
\usepackage{graphicx}
\usepackage{xcolor}
\usepackage{fancyhdr}
\usepackage{array}
\usepackage{longtable}
\usepackage{hyperref}
\usepackage{pgfgantt}   % solo si el documento lleva cronograma
```

La `TraceabilityMatrix/MatrizDeTrazabilidad.tex` es la excepción deliberada: usa `standalone` en vez de `article` porque no pagina. Ver [[matriz-de-trazabilidad]] para por qué y con qué preámbulo.

**Márgenes y tipografía**: 12pt, A4, márgenes de 2,5 cm en todos los lados, Computer Modern estándar, 6pt de espaciado entre párrafos, sin sangría.

## Macros propios: no usar `\section`/`\subsection` directamente

```latex
\newcommand{\sectiontitle}[1]{\section*{#1}\addcontentsline{toc}{section}{#1}}
\newcommand{\subsectiontitle}[1]{\subsection*{#1}\addcontentsline{toc}{subsection}{#1}}
```

Componen la sección sin numerar automática y la inscriben a mano en el índice. La numeración va **dentro del título** (`\sectiontitle{3. Requisitos Específicos}`, `\subsectiontitle{3.2.2. Gestión de Clientes}`), no delegada a LaTeX. El Plan de Gestión del Alcance redefine estos mismos macros con espaciado más corto (`-2.2ex`/`-1.7ex` en vez del de `article`) porque acumula 24 subsecciones y el espaciado por omisión lo estira sin añadir información — ver el preámbulo de `PlanDeGestionDelAlcance.tex`.

## `\ruta{}`: rutas de API y de archivo

```latex
\urlstyle{tt}
\newcommand{\ruta}[1]{\nolinkurl{#1}}
```

`\texttt{}` es indivisible: una ruta larga (`/v1/api/service-areas/{idAreaServicio}/equipments`) se sale de la caja de la página. `\nolinkurl` la compone con la misma tipografía monoespaciada pero admite corte de línea tras cada barra y cada guion. **Usar siempre `\ruta{}` para endpoints y rutas de archivo, nunca `\texttt{}`.**

## Marcas de estado

```latex
\newcommand{\estadoImplementado}{\textcolor{mainRed}{\textbf{[IMPLEMENTADO]}}}
\newcommand{\estadoPrevisto}{\textcolor{mainGray}{\textbf{[PREVISTO]}}}
```

Ver [[regla-implementado-vs-previsto]] para su significado y dónde se usan.

## Portada

`\begin{titlepage}...\end{titlepage}`, centrada, con este orden fijo: nombre de la universidad en mayúsculas (dos líneas), el título del documento en `\Huge\textbf{}`, el nombre del proyecto (`MalphasOS`) y su subtítulo (`Gestión de Clientes`), la autoría (`Sean Sebastián Bolívar Calderón - 20231020135`) y la fecha al pie con `\vfill`. El documento de constitución añade el logo (`Images/malphasos-stacked.png`) y una tabla de metadatos (elaborado por, código, asignatura, institución, fecha) porque es un charter formal con firma; la ERS y el plan de alcance no llevan logo ni tabla, solo el bloque de texto centrado. La matriz de trazabilidad, por ser `standalone`, sustituye la portada de página completa por el mismo bloque de texto sin `\titlepage` ni `\vfill`, porque no hay página que llenar — ver [[matriz-de-trazabilidad]].

## Formatos de código de referencia

| Tipo | Formato | Ejemplo |
|---|---|---|
| Requisito funcional | `RF-NN` | `RF-08` |
| Requisito no funcional | `RNF-NN` | `RNF-23` |
| Requisito de dominio | `RD-N` | `RD-03` |
| Objetivo | `OBJ-NN` | `OBJ-01` |
| Criterio de éxito | `CE-NN` | `CE-08` |
| Riesgo | `R-NN` | `R-04` |
| Hito | `H-NN` | `H-00` |

## Tablas

`longtable` para tablas multi-página (la matriz de trazabilidad usa `tabular` simple porque es `standalone` y no pagina — ver [[matriz-de-trazabilidad]]). Ancho mínimo de columna 2 cm salvo la de ID. Encabezados en negrita. Alineación izquierda para texto, centro para números e identificadores.

## Énfasis

- **Negrita** (`\textbf{}`): conceptos clave, decisiones importantes.
- *Cursiva* (`\textit{}`): primera mención de un término técnico, notas.
- `Monoespaciado` (`\texttt{}` o `\ruta{}`): códigos, nombres de tabla/clase, comandos, rutas.

## Números y formato — español de Colombia

- Porcentajes: `40\,\%`, `99\,\%` (espacio fino antes del símbolo).
- Desigualdades: `$\geq$`, `$\leq$`, `$<$`, `$>$`.
- Separador decimal: coma, `1{,}5` (nunca `1.5`).
- Miles: punto, `15.000.000`.
- Evitar anglicismos innecesarios; el documento se escribe en español (Colombia).

## Referencias cruzadas

`\label{}` y `\ref{}` para figuras y tablas: `Ver Tabla~\ref{tab:criterios}` o `Figura~\ref{fig:cronograma}`.

## Terminología: la ERS manda para el lector, el código manda para la verdad

La ERS fija un vocabulario —**sede**, **área de servicio**, **encargado**, **orden de trabajo**— y no se introducen sinónimos al escribir sobre ella. Cuando el código llama a algo de otra forma (`Headquarter`, `ServiceArea`, `Manager`), se explica la correspondencia en vez de elegir un término a ciegas. Ver [[correspondencia-terminologica]].

## Notas relacionadas

[[paleta-de-colores]] · [[regla-implementado-vs-previsto]] · [[herramientas-y-compilacion]] · [[correspondencia-terminologica]]
