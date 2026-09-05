---
name: ers-ieee830
description: Qué es la ERS de MalphasOS, cómo está estructurada y qué apartado contiene qué
tags: [documento, ers, ieee830]
fuente: Documentation/IEEE830/IEEE830.tex
estado: vigente
updated: 2026-09-05
---

# Especificación de Requisitos de Software (ERS) — IEEE 830

- **Archivo**: `IEEE830/IEEE830.tex` (1862 líneas) → `IEEE830/IEEE830.pdf` (44 páginas)
- **Fecha**: 11 de agosto de 2026
- **Es el documento central**: todos los demás documentos de `Documentation/` toman su alcance de aquí.

## Estructura

| Apartado | Contenido |
|---|---|
| 1. Introducción | Propósito, ámbito del sistema (1.2), definiciones/acrónimos/abreviaturas (1.3), visión general |
| 1.2.2 | **Exclusiones explícitas del MVP**: integración con terceros, gestión contable/financiera, facturación, alertas automáticas de calibración, inventario de herramientas y equipos patrón |
| 2. Descripción general | Perspectiva del producto (2.1, con diagrama de bloques `tikz`), funciones del producto (2.2), usuarios (2.3), restricciones (2.4), suposiciones y dependencias (2.5), requisitos futuros (2.6) |
| 3. Requisitos específicos | Interfaces externas (3.1), funciones/RF (3.2), rendimiento/RNF (3.3), restricciones de diseño (3.4), MoSCoW (3.5), matriz de trazabilidad (3.6), atributos del sistema (3.7), otros RNF (3.8) |

## El apartado 3.2, requisito por requisito

Es el corazón del documento y el único apartado que la propia ERS evalúa contra el código, con `\estadoImplementado`/`\estadoPrevisto` — ver [[regla-implementado-vs-previsto]]. Nueve subapartados, cada uno correspondiente a una categoría funcional:

| Subapartado | Categoría | Rango de códigos |
|---|---|---|
| 3.2.1 | Gestión de Órdenes de Trabajo | RF-01 a RF-07 |
| 3.2.2 | Gestión de Clientes | RF-08 |
| 3.2.3 | Reportes de Mantenimiento | RF-09 a RF-17 (no consecutivos) |
| 3.2.4 | Firma Digital | RF-18, RF-21 |
| 3.2.5 | Hojas de Vida de los Equipos | RF-22 a RF-27 (no consecutivos) |
| 3.2.6 | Inventario | RF-36, RF-37 |
| 3.2.7 | Alertas y calibración | RF-40, RF-41 |
| 3.2.8 | Módulo Comercial | RF-45, RF-47 |
| 3.2.9 | Usuarios y seguridad | RF-49 a RF-53 |

Detalle de cada categoría en sus notas propias: [[rf-ordenes-trabajo]], [[rf-clientes]], [[rf-reportes-mantenimiento]], [[rf-firma-digital]], [[rf-hojas-vida]], [[rf-inventario]], [[rf-alertas-calibracion]], [[rf-modulo-comercial]], [[rf-usuarios-seguridad]].

Cada requisito lleva: nombre y enunciado, nivel de complejidad, criterios de aceptación, dependencias (otros códigos `RF-`) y, desde la revisión del 2 de septiembre de 2026, un punto *Estado en la implementación actual*.

## Los otros dos tipos de requisito

- **RNF** (no funcionales): 16 tienen bloque propio con nivel de complejidad — 10 en el apartado 3.3 (RNF-01, 03, 04, 09, 10, 18, 19, 20, 21, 22) y 6 en el apartado 3.8, "Otros requisitos" (RNF-02, 05, 06, 07, 08, 11). Los 7 restantes (RNF-12 a RNF-17 y RNF-23) se citan por su código dentro de 3.1.1, 3.4 y 3.7, sin bloque propio pero con enunciado suficiente para identificarlos. 16 + 7 = 23. Ver [[requisitos-no-funcionales]].
- **RD** (de dominio): 6 códigos citados exclusivamente en el apartado 3.5 (MoSCoW), sin enunciado en ningún otro lugar del documento. Ver [[requisitos-de-dominio]].

## Un dato que solo aparece aquí y no se repite en el charter

El apartado 2.2 (Funciones del producto) describe diez funciones con letras (a) a (j), incluida "exportación en PDF y Excel" — pero **RF-16, el requisito de exportación a Excel, no existe** en el apartado 3.2 pese a citarse en 3.1.3. Ver [[defectos-conocidos-de-la-ers]].

## Diagramas incluidos

Ocho de los diez diagramas de casos de uso tienen `\includegraphics` en el `.tex`, cada uno precedido de un resto de maquetación `[DIAGRAMA DE CASOS DE USO - ...]` sin borrar del borrador. `clientes` y `malphasos_general` están compilados pero no incluidos. Ver [[diagramas-de-casos-de-uso]].

## Notas relacionadas

[[regla-implementado-vs-previsto]] · [[estado-de-implementacion]] · [[matriz-de-trazabilidad]] · [[defectos-conocidos-de-la-ers]] · [[convenciones-latex]]
