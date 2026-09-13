---
name: manual-de-marca
description: El Manual de Marca v1.0, autoridad del sistema visual de MalphasOS. Logotipo, color, tipografia, reticula y tono de voz, con lo que obliga a una interfaz
tags: [documentos, marca, design-system, forma]
fuente: "Documentation/FrontendDesign/BrandManual/MalphasOS Manual de Marca.html"
estado: vigente
updated: 2026-09-13
---

# Manual de Marca v1.0

**Qué es.** La definición de la identidad visual de MalphasOS: logotipo y sus versiones, construcción y resguardo, usos incorrectos, color, tipografía, retícula, iconografía, fotografía y aplicaciones. Diez páginas, fechado en 2026, marcado como documento interno.

**Dónde vive.** `Documentation/FrontendDesign/BrandManual/`. El manual es una página HTML autocontenida; junto a ella va el proyecto de diseño con los archivos fuente y los logotipos exportados en PNG.

**Qué autoridad tiene.** **Es la fuente del sistema visual, por encima de esta wiki y de cualquier otra nota.** [[paleta-de-colores]] dejó de definir colores el 2026-09-13 y pasó a registrarlos remitiendo aquí.

## La idea que gobierna todo el manual

> «Una herramienta de trabajo, no un adorno.»

Y su regla de desempate, literal: **ante una duda que el documento no resuelva, prevalece el criterio más sobrio: menos color, más estructura.**

Tres atributos declarados: **sobrio** (negro, blanco y un solo acento; sin degradados ni sombras), **técnico** (ángulos rectos, radio cero, retícula visible) y **confiable** (consistencia por encima de novedad).

## Lo que obliga en una interfaz

Esto es lo que importa para el desarrollo, y lo que [[declaracion-diseno-frontend]] adopta sin discutir:

| Aspecto | Regla |
|---|---|
| Color | Monocromo con un solo acento. **El acento nunca cubre más del 10 % de una composición** |
| Radio | **Cero**, en todo |
| Alineación | Siempre a la izquierda. No se centra ni se justifica |
| Tipografía | **Archivo**. Títulos 700, subtítulos 600, cuerpo 400. Sustitutas: Helvetica Neue, luego Arial |
| Interlineado | Títulos 0,95–1,05; cuerpo 1,5–1,6. Medida máxima 70 caracteres |
| Retícula | 12 columnas, medianil 24 px, márgenes 48 px |
| Espaciado | Múltiplos de **8 px** |
| Iconografía | **Lucide**, trazo 2 px, esquinas rectas, 16 / 20 / 24 px. En tinta; en acento sólo para alertas |
| Fotografía | Blanco y negro puro, sin virados. Nunca fotografía de banco con gente sonriendo |

### La decisión que más cambia una interfaz

**Los estados operativos —vencido, en curso, cerrado— se diferencian por peso tipográfico y regla, no por colores nuevos.**

Va contra el reflejo habitual de las insignias de colores: aquí **una orden vencida no se pinta de rojo**. El acento señala acciones y alertas; no clasifica estados. Es coherente con el límite del 10 % y con «el color se reserva para señalar, nunca para decorar».

### Componentes que el manual ya especifica

- **Acción principal**: relleno acento, etiqueta a la izquierda.
- **Acción secundaria**: contorno de 2 px, sin relleno.
- **Campos**: radio cero, borde de 1 px, foco en acento.

## El tono de voz, que afecta a un requisito

El manual fija el tono con ejemplos, y eso aterriza directamente en **RNF-17** (mensajes de error claros):

| | |
|---|---|
| **Sí** | Directo e instructivo. «Orden cerrada. Próximo mantenimiento: 14 días.» |
| **No** | Entusiasta o publicitario. «¡Felicidades, lo lograste!» |

## El logotipo

Tres versiones: **horizontal** (principal), **apilada** (anchos limitados) e **ícono** (donde la marca ya es conocida: app, favicon, avatar). Más una **inversa** para fondos oscuros, donde «Malphas» pasa a blanco y «OS» conserva el acento.

- Unidad de medida **X**, la altura de caja alta de la «M». Resguardo de **1X** por los cuatro lados, reducible a 0,5X en espacios comprimidos y nunca menos.
- Mínimos: **120 px** de ancho en horizontal, **24 px** el ícono, **25 mm** impreso.
- La bajada «Gestión de mantenimiento» va en mayúsculas, peso regular, interletrado 0,14em, al 22–26 % del tamaño del logotipo, y **se omite por debajo de 180 px** de ancho.
- El cuadro negro **es parte del ícono y nunca se elimina**.

Seis usos incorrectos explícitos: rotar, deformar, recolorear fuera de la paleta, sustituir la tipografía, colocar sobre fondos de bajo contraste y añadir sombras o contornos. Tampoco separar «Malphas» de «OS», ni usar el ícono con la bajada.

## El color, en una línea

Cuatro colores nombrados —tinta `#201E1D`, negro marca `#2D2B2B`, papel `#F3F2F2`, acento `#EC3013`— más dos escalas de nueve pasos, y una regla de contraste propia que remite al paso 700 `#AE1800` para texto en acento. El detalle, medido, en [[paleta-de-colores]].

## Un hallazgo al contrastarlo con el logo

El logo `malphasos-stacked.png` que usa el documento de constitución contiene exactamente los colores del manual: `#F3F2F2` en el 84,2 % de sus píxeles opacos, `#2D2B2B` en el 9,2 %, `#201E1D` en el 3,3 % y `#EC3013` en el 1,2 %. **La marca y los documentos ya eran coherentes antes de que existiera el manual**; lo que faltaba era que alguien lo escribiera.

## Notas relacionadas

[[paleta-de-colores]] · [[declaracion-diseno-frontend]] · [[convenciones-latex]] · [[documento-constitucion]]
