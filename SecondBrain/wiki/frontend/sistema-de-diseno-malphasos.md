---
name: sistema-de-diseno-malphasos
description: El manual de marca traducido a decisiones de interfaz: tokens, contrastes medidos, radio cero, escala de 8, estados por peso y no por color, y las dos extensiones propias
tags: [frontend, diseno, marca, accesibilidad, "describe:malphasos"]
source: Documentation/FrontendDesign/BrandManual/ y DeclaracionDeDisenoFrontend.tex
estado: estable
updated: 2026-09-13
---

# Sistema de diseño de MalphasOS

**La autoridad es el _Manual de Marca_ v1.0**, en `Documentation/FrontendDesign/BrandManual/`. Esta nota no decide: traduce el manual a lo que hace falta para escribir una interfaz. **Donde discrepen, manda el manual**, salvo en las dos extensiones que se señalan al final.

Su regla de desempate, literal: **ante una duda que el manual no resuelva, prevalece el criterio más sobrio: menos color, más estructura.**

## Tokens

| Token | Hex | Uso |
|---|---|---|
| `ink` | `#201E1D` | Texto principal |
| `brand-black` | `#2D2B2B` | Superficies oscuras; paso 900 de la escala neutra |
| `paper` | `#F3F2F2` | Fondo |
| `accent` | `#EC3013` | Bordes, anillo de foco, iconos de alerta, texto grande |
| `accent-700` | `#AE1800` | **Texto en acento y relleno de la acción principal** |

Escalas de nueve pasos, del manual:

- **Neutra** `#F8F4F4` `#EAE7E7` `#D7D3D3` `#BAB6B6` `#9B9797` `#7D7979` `#605D5D` `#444141` `#2D2B2B`
- **Acento** `#FFF2EF` `#FFE0D9` `#FFC4B8` `#FF9783` `#FF563C` `#DD2B0F` `#AE1800` `#7C1405` `#4D170E`

**`#6b6866` no se usa en la interfaz.** Es `mainGray`, un gris de los documentos LaTeX que no existe en la marca. Para texto secundario van los pasos de la escala neutra. Ver `Documentation/wiki/forma/paleta-de-colores.md` en la wiki de `Documentation/`.

## Contraste: los números, medidos

El manual trae su propia regla —`#EC3013` no se usa para texto de párrafo sobre papel; para texto en acento va `#AE1800` (paso 700)— y funciona:

| Combinación | Contraste | AA texto normal (4,5:1) |
|---|---|---|
| `#201E1D` sobre papel | 14,86:1 | ✅ |
| `#2D2B2B` sobre papel | 12,60:1 | ✅ |
| `#AE1800` sobre papel | 6,41:1 | ✅ |
| `#EC3013` sobre papel | 3,76:1 | ❌ |
| Papel sobre `#EC3013` | 3,76:1 | ❌ |
| **Papel sobre `#AE1800`** | **6,41:1** | ✅ |

Para elementos **no textuales** el umbral es 3,0 y `#EC3013` da 3,76:1: **ahí cumple**, y se queda donde el manual lo pone.

> ⚠️ **El botón es el caso que el manual no nombra.** Describe la acción principal como «relleno acento». Con `#EC3013` de relleno, la etiqueta da 3,76:1 y **el control más repetido de la aplicación no alcanza AA**. Por eso el relleno usa `accent-700`. No es corregir el manual: es su propia regla —el acento puro no sostiene texto encima— aplicada a un caso que no listó.

## Forma

| Aspecto | Regla |
|---|---|
| Radio de esquina | **Cero**, en todo: botones, campos, tarjetas, imágenes |
| Alineación | Siempre a la izquierda. No se centra ni se justifica |
| Espaciado | Múltiplos de **8 px** |
| Retícula | 12 columnas, medianil 24 px, márgenes 48 px |
| Área táctil | **Mínimo 44 × 44 px** en todo control accionable |
| Iconografía | **Lucide**, trazo 2 px, esquinas rectas, 16 / 20 / 24 px. En tinta; en acento sólo para alertas |

44 no es múltiplo de 8 y no pasa nada: es una **superficie mínima de interacción**, no una medida de maquetación.

## Tipografía

Una sola familia: **Archivo**. Sustitutas donde no esté disponible: Helvetica Neue, luego Arial.

| Rol | Peso | Interlineado |
|---|---|---|
| Display y títulos | 700 | 0,95 – 1,05 |
| Subtítulos | 600 | 0,95 – 1,05 |
| Cuerpo | 400 | 1,5 – 1,6 |
| Etiquetas | 600 | — |

**Medida máxima de lectura: 70 caracteres.** Tamaño base del cuerpo **16 px**: por debajo, los navegadores móviles amplían al enfocar un campo y producen un salto visual que confunde.

## La regla que más cambia una interfaz

> **Los estados operativos —vencido, en curso, cerrado— se distinguen por peso tipográfico y por regla, no por colores nuevos.**

Va contra el reflejo de las insignias de colores. **Una orden vencida no se pinta de rojo.** El acento señala acciones y alertas; **no clasifica estados**. Es coherente con el límite que el manual impone al acento: **nunca más del 10 % de una composición**.

Cuando aparezca la tentación de añadir un verde de «correcto» o un ámbar de «pendiente», la respuesta del manual ya está escrita: menos color, más estructura.

## Componentes que el manual ya especifica

- **Acción principal**: relleno `accent-700`, etiqueta a la izquierda.
- **Acción secundaria**: contorno de 2 px, sin relleno.
- **Campos**: radio cero, borde de 1 px, foco en acento.

**spartan/ui hay que re-vestirlo.** Sus plantillas traen esquinas redondeadas y una escala de color propias, y el manual exige radio cero y su paleta. Cada componente que se incorpore se re-viste antes de usarse — que el código viva en el repositorio es precisamente lo que lo hace posible.

## Tono de los textos, que es un requisito

El manual fija el tono de voz, y eso aterriza en **RNF-17**:

| | |
|---|---|
| **Sí** | Directo e instructivo. «Orden cerrada. Próximo mantenimiento: 14 días.» |
| **No** | Entusiasta o publicitario. «¡Felicidades, lo lograste!» |

Los mensajes de error siguen ese tono: dicen **qué pasó y qué hacer**, sin disculpas ni exclamaciones. «Esta orden ya se ejecutó y no admite cambios», no «¡Ups! Algo salió mal».

## Accesibilidad

**WCAG 2.1 nivel AA, declarado y verificado.** La ERS pide accesibilidad (RNF-12) sin fijar nivel; sin nivel el requisito no tiene criterio de aceptación. El analizador automático corre sobre cada pantalla dentro de las pruebas de integración, y un fallo rompe la batería.

La razón de automatizarlo está aprendida aquí: **una regla declarada y no ejercitada envejece sola** — ver [[regla-traslado-mismo-cliente]], donde una regla se dio por construida un día entero sin estarlo.

Lo que la comprobación automática **no** cubre y exige ojo humano: el orden de lectura, la claridad del lenguaje y la utilidad real para un lector de pantalla.

## Las dos extensiones propias

Señaladas como tales porque el manual manda:

1. **Retícula en teléfono.** El manual fija 12 columnas y márgenes de 48 px pensando en escritorio y no resuelve anchos estrechos. En teléfono: **4 columnas y márgenes de 16 px**, conservando el medianil de 24 y la escala de 8. Hace falta porque la aplicación es **móvil primero**: el teléfono del ingeniero no es un caso degradado, es el principal.
2. **Relleno de la acción principal** con `accent-700`, por el contraste de arriba.

## Notas relacionadas

[[arquitectura-frontend-malphasos]] · [[hoja-de-ruta-producto]] · [[regla-traslado-mismo-cliente]]
