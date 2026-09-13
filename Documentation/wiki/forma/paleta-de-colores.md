---
name: paleta-de-colores
description: La autoridad es el manual de marca, no esta nota. Aqui se registran los colores de los documentos LaTeX, su relacion con la marca y las tres discrepancias entre ambos
tags: [forma, colores, design-system, marca]
fuente: "Documentation/FrontendDesign/BrandManual/ (autoridad), Documentation/IEEE830/IEEE830.tex, Documentation/constitutionDocument/constitutionDocument.tex"
estado: vigente
updated: 2026-09-13
---

# Paleta de colores del proyecto

> **Corregida por completo el 2026-09-13.** Hasta esa fecha esta nota se presentaba como la definición de la paleta, con una tabla llamada «paleta ampliada del design system». **Era falso**: no había ningún sistema de diseño, la nota describía los colores de los documentos LaTeX y de un Gantt, y listaba `#201E1D` sin poder decir de dónde salía. Lo que sigue corrige eso y deja constancia de qué decía antes.

## La autoridad es el manual de marca

**El sistema visual de MalphasOS lo define el _Manual de Marca_ v1.0**, en
`Documentation/FrontendDesign/BrandManual/`. Esta nota **no decide colores**:
registra los de los documentos LaTeX y explica cómo se relacionan con la marca.

Ante cualquier discrepancia, **manda el manual**.

Lo que el manual establece, resumido, para no tener que abrirlo por una consulta rápida:

| Nombre | Hex | Papel |
|---|---|---|
| Tinta | `#201E1D` | Texto |
| Negro marca | `#2D2B2B` | Superficies oscuras; paso 900 de la escala neutra |
| Papel | `#F3F2F2` | Fondo |
| Acento | `#EC3013` | Acción principal y alertas. **Nunca más del 10 % de una composición** |
| Acento 700 | `#AE1800` | Texto en acento, por contraste |

Más dos escalas de nueve pasos:

- **Neutra**: `#F8F4F4` `#EAE7E7` `#D7D3D3` `#BAB6B6` `#9B9797` `#7D7979` `#605D5D` `#444141` `#2D2B2B`
- **Acento**: `#FFF2EF` `#FFE0D9` `#FFC4B8` `#FF9783` `#FF563C` `#DD2B0F` `#AE1800` `#7C1405` `#4D170E`

El principio de la marca, en sus propias palabras: **la paleta es monocroma con un solo acento, y el color se reserva para señalar, nunca para decorar**. Los estados operativos se distinguen **por peso tipográfico y regla, no por colores nuevos**.

### La regla de contraste, que es del manual y no de nadie más

> «El rojo `#EC3013` no se usa para texto de párrafo sobre papel. Para texto en acento emplear `#AE1800` (paso 700).»

Medido el 2026-09-13, la regla funciona y hacía falta:

| Combinación | Contraste | WCAG AA texto normal |
|---|---|---|
| `#201E1D` sobre papel | 14,86:1 | Cumple |
| `#2D2B2B` sobre papel | 12,60:1 | Cumple |
| `#AE1800` sobre papel | 6,41:1 | Cumple |
| `#EC3013` sobre papel | 3,76:1 | **No cumple** |

**Un caso que el manual no nombra**: describe la acción principal como «relleno acento». Con `#EC3013` de relleno y la etiqueta en papel el contraste es **3,76:1**, de modo que el botón más repetido de una interfaz no alcanzaría AA. [[declaracion-diseno-frontend]] lo resuelve aplicando la propia regla del manual un caso más allá —relleno con el paso 700, 6,41:1— y lo declara como extensión, no como corrección.

Para elementos **no textuales** —bordes, anillo de foco, iconos— el umbral es 3,0 y `#EC3013` da 3,76:1: **ahí sí cumple**, y se queda donde el manual lo pone.

## Los colores de los documentos LaTeX

Declarados con `\definecolor{...}{HTML}{...}` en **cinco** documentos —la ERS, el plan de gestión del alcance, la matriz de trazabilidad, la matriz de interesados y la declaración de diseño del frontend—, idénticos en los cinco:

| Nombre LaTeX | Hex | Uso en el documento |
|---|---|---|
| `mainRed` | `#EC3013` | Filete del encabezado, marca `\estadoImplementado` |
| `mainGray` | `#6b6866` | Texto del encabezado, marcas `\estadoPrevisto` y `\noVerificable` |
| `mainWhite` | `#F3F2F2` | Fondos y resaltes |

El principio: **lo construido resalta en rojo, lo que falta queda en gris**. Ver [[regla-implementado-vs-previsto]].

## Las tres discrepancias entre los documentos y la marca

Comprobadas archivo por archivo el 2026-09-13, no supuestas. **Ninguna es un error que haya que arreglar**: son dos sistemas con propósitos distintos, y conviene saber cuál rige dónde.

1. **`mainGray` `#6b6866` no existe en el manual de marca.** No aparece ni en el logo ni en el documento. Es un gris propio de los documentos LaTeX, y ahí se queda: no debe usarse en la interfaz, que tiene su escala neutra de nueve pasos.

2. **La tinta `#201E1D` no la declara ningún `.tex`.** Es la tinta de la marca y está en el logo, pero los documentos componen su texto en negro por omisión y nunca la nombran. Esta nota la listaba antes **sin poder decir de dónde salía**; ahora se sabe.

3. **El gradiente del Gantt sólo coincide a medias con la escala de acento.** Cuatro de sus trece colores son pasos reales de la escala —`#EC3013`, `#FF9783`, `#FF563C`, `#DD2B0F`— y los otros nueve no. El Gantt es anterior al manual y se deja como está: es un documento ya entregado.

## El gradiente del Gantt de la constitución

Trece colores para trece barras, uno por módulo, en `constitutionDocument.tex` líneas 374–386:

`#EC3013` (Landing Page) → `#ef6853` (Auth) → `#ff9783` (Clientes) → `#ff563c` (Órdenes) → `#e15b47` (Reportes) → `#dd2b0f` (Equipos) → `#C91E10` (Firma digital) → `#B51A0D` (Hojas de vida) → `#9E1609` (Inventario) → `#8B1407` (Alertas) → `#7A1206` (Módulo comercial) → `#6B0F05` (Exportación) → `#5C0D04` (Capacitación)

## Qué usar en un documento nuevo

- **LaTeX**: `mainRed`, `mainGray`, `mainWhite`, copiados del preámbulo de la ERS.
- **Interfaz**: el manual de marca, y sólo el manual. Ver [[declaracion-diseno-frontend]].
- **Gantt**: un color por fase, siguiendo el gradiente si el cronograma es secuencial.
- **Diagramas**: la escala de acento del manual, en orden.

## Notas relacionadas

[[manual-de-marca]] · [[declaracion-diseno-frontend]] · [[convenciones-latex]] · [[regla-implementado-vs-previsto]] · [[interesados-riesgos-hitos]]
