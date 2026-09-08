---
name: requisitos-de-dominio
description: RD-01 a RD-07 (sin RD-02). Los seis citados en la ERS no tienen enunciado en ningun documento del proyecto
tags: [requisitos, rd, defecto-conocido]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.5; Documentation/TraceabilityMatrix/MatrizDeTrazabilidad.tex, seccion 3"
estado: defecto-conocido
updated: 2026-09-05
---

# Requisitos de dominio (RD-01 a RD-07)

Es donde la trazabilidad de la ERS se rompe antes de empezar. Los seis códigos que la ERS reconoce —`RD-01`, `RD-03`, `RD-04`, `RD-05`, `RD-06`, `RD-07`— **aparecen exclusivamente en el apartado 3.5 (priorización MoSCoW)**. Ninguno tiene nombre, descripción, criterios de aceptación ni sección propia en ningún apartado del documento. **No existe `RD-02`**: la numeración salta de `RD-01` a `RD-03` sin explicación.

Es notable porque el apartado 1.3.3 de la ERS declara "de dominio" como una de las tres clasificaciones de requisito del documento (junto a RF y RNF) — una categoría entera sin una sola sección que la desarrolle.

## La única pista que existe, y de dónde sale

El documento de constitución (`constitutionDocument.tex`, apartado 5.1, "Requisitos funcionales mínimos"), **no la ERS**, asocia `RD-03` y `RD-04` a "Registro de equipos". Es la única pista de contenido en todo el repositorio. La [[matriz-de-trazabilidad]] la usa marcándola explícitamente como **inferida**, no como un hecho que la ERS declare.

## Tabla completa

| Código | Nombre/descripción | MoSCoW | Objetivo/criterio | Estado |
|---|---|---|---|---|
| RD-01 | Sin nombre ni descripción en ningún documento | Should Have | No verificable | No verificable |
| RD-03 | Sin nombre en la ERS. El charter lo asocia (junto a RD-04) a "Registro de equipos" | Must Have | OBJ-03 / CE-03 (inferido) | Implementado (inferido) |
| RD-04 | Sin nombre en la ERS. Misma asociación inferida que RD-03; la ERS no distingue qué aporta cada código por separado | Must Have | OBJ-03 / CE-03 (inferido) | Implementado (inferido) |
| RD-05 | Sin nombre ni descripción en ningún documento | Should Have | No verificable | No verificable |
| RD-06 | Sin nombre ni descripción en ningún documento | Could Have | No verificable | No verificable |
| RD-07 | Sin nombre ni descripción en ningún documento | Won't Have | No verificable | No verificable |

## Por qué RD-03/RD-04 se marcan "implementado (inferido)" y no "implementado" a secas

Si "registro de equipos" es la lectura correcta de lo que pedían, la cadena del catálogo ya existe y es la misma evidencia que sostiene RF-22: `equipo_cliente`, `tipo_equipo`, `fabricante`, `marca`, `modelo`. Pero **la ERS nunca confirma esa lectura con sus propias palabras**. El estado se ofrece con la salvedad explícita de que el requisito evaluado es una suposición razonable sobre qué pedía el código, no un enunciado verificado — es la diferencia entre `estado: inferido` y `estado: vigente` que usa el frontmatter de esta wiki (ver `wiki/CLAUDE.md`).

## Los cuatro sin ninguna pista

`RD-01`, `RD-05`, `RD-06`, `RD-07` no tienen ni siquiera esa aproximación. Contrastarlos contra el código exigiría inventar qué se les está pidiendo, y ni la ERS ni la matriz de trazabilidad lo hacen: se marcan `\noVerificable{}` en vez de forzar una de las otras dos marcas. Ver [[regla-implementado-vs-previsto]].

## Notas relacionadas

[[regla-implementado-vs-previsto]] · [[priorizacion-moscow]] · [[defectos-conocidos-de-la-ers]] · [[documento-constitucion]] · [[rf-hojas-vida]]
