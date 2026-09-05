---
name: paleta-de-colores
description: Los hexadecimales del cuerpo del documento y de la paleta ampliada del design system, y que papel cumple cada uno
tags: [forma, colores, design-system]
fuente: "Documentation/IEEE830/IEEE830.tex, Documentation/constitutionDocument/constitutionDocument.tex"
estado: vigente
updated: 2026-09-05
---

# Paleta de colores del proyecto

## Colores del cuerpo del documento (ERS, plan de alcance, matriz de trazabilidad)

Idénticos en los tres, definidos con `\definecolor{...}{HTML}{...}`:

| Nombre LaTeX | Hex | Uso | Rol |
|---|---|---|---|
| `mainRed` | `#EC3013` | Filete del encabezado (`\fancyhead`/`\headrule`), marca `\estadoImplementado` | Primary |
| `mainGray` | `#6b6866` | Texto del encabezado, marca `\estadoPrevisto` y `\noVerificable` | Texto atenuado |
| `mainWhite` | `#F3F2F2` | Fondos y resaltes | Background |

El principio de fondo: **lo construido resalta en rojo, lo que falta queda en gris**. Ver [[regla-implementado-vs-previsto]].

## Paleta ampliada del design system (Gantt del charter, diagramas, énfasis)

| Color | Hex | Uso | Rol |
|---|---|---|---|
| Rojo principal | `#EC3013` | Inicio, crítico | Primary |
| Naranja | `#ef6853` | Desarrollo temprano | Secondary |
| Coral | `#ff9783` | Desarrollo medio | Accent 1 |
| Rojo medio | `#ff563c` | Desarrollo avanzado | Accent 2 |
| Gris oscuro | `#201E1D` | Texto, fondo | Text |
| Gris claro | `#F3F2F2` | Fondo, resalte | Background |

## Gradiente del Gantt de la constitución

El Gantt de `constitutionDocument.pdf` extiende la gama ampliada hacia rojos progresivamente más oscuros, uno por módulo, para que el orden del color siga el orden del cronograma:

`#EC3013` (Landing Page) → `#ef6853` (Auth) → `#ff9783` (Clientes) → `#ff563c` (Órdenes) → `#e15b47` (Reportes) → `#dd2b0f` (Equipos) → `#C91E10` (Firma digital) → `#B51A0D` (Hojas de vida) → `#9E1609` (Inventario) → `#8B1407` (Alertas) → `#7A1206` (Módulo comercial) → `#6B0F05` (Exportación) → `#5C0D04` (Capacitación)

Trece colores para trece barras del Gantt (ver [[interesados-riesgos-hitos]]), cada uno definido con su propio `\definecolor{colorNNN}{HTML}{...}` justo antes de la figura del cronograma en `constitutionDocument.tex`.

## Uso en documentos nuevos

- Gantt: un color por fase o módulo, siguiendo el gradiente si el cronograma es secuencial.
- Títulos enfatizados y marcas de estado positivas: rojo principal (`mainRed`).
- Información secundaria o atenuada: grises (`mainGray`, gris oscuro `#201E1D`).
- Diagramas: la paleta ampliada completa, en orden.

## Notas relacionadas

[[convenciones-latex]] · [[regla-implementado-vs-previsto]] · [[interesados-riesgos-hitos]]
