---
name: objetivos-y-criterios-de-exito
description: OBJ-01 a OBJ-07 y CE-01 a CE-10 del documento de constitución, con qué objetivo mide cada criterio y qué requisitos los sostienen
tags: [proyecto, objetivos, constitucion, trazabilidad]
fuente: Documentation/constitutionDocument/constitutionDocument.tex
estado: vigente
updated: 2026-09-05
---

# Objetivos y criterios de éxito

## Objetivo general

Desarrollar MalphasOS, plataforma web integrada de gestión de mantenimiento de equipos biomédicos para BolívarBioingeniería LTDA, digitalizando procesos operativos y mejorando documentación, trazabilidad y cumplimiento regulatorio.

## Objetivos específicos (OBJ-01 a OBJ-07)

| Código | Objetivo |
|---|---|
| OBJ-01 | Reducir ≥40 % el tiempo de elaboración de reportes mediante automatización |
| OBJ-02 | Disminuir ≥30 % los errores en diligenciamiento mediante validación y autocompletado |
| OBJ-03 | Asegurar 100 % de cobertura digital de equipos con hojas de vida actualizadas |
| OBJ-04 | Centralizar 100 % de las órdenes de trabajo en la plataforma, eliminando Excel y WhatsApp |
| OBJ-05 | Garantizar cumplimiento regulatorio de la Secretaría de Salud en la documentación |
| OBJ-06 | Entregar el MVP en 1,5 meses y el sistema completo en 6 meses, dentro de COP $15.000.000 |
| OBJ-07 | Asegurar consultas en menos de 3 segundos y disponibilidad ≥99 % en horario operativo |

## Criterios de éxito (CE-01 a CE-10)

| Código | Criterio | Indicador | Objetivo que mide |
|---|---|---|---|
| CE-01 | Tiempo de reportes | Reducción ≥40 % vs. proceso manual | OBJ-01 |
| CE-02 | Errores de diligenciamiento | Reducción ≥30 % vs. proceso manual | OBJ-02 |
| CE-03 | Cobertura digital | 100 % de equipos con hoja de vida | OBJ-03 |
| CE-04 | Centralización de OT | 100 % de órdenes en la plataforma | OBJ-04 |
| CE-05 | Cumplimiento regulatorio | Reportes cumplen formato de la Secretaría de Salud | OBJ-05 |
| CE-06 | Plazo | MVP ≤1,5 meses; completo ≤6 meses | OBJ-06 |
| CE-07 | Presupuesto | Gasto total ≤ COP $15.000.000 | OBJ-06 |
| CE-08 | Rendimiento | Consultas ≤3 segundos | OBJ-07 |
| CE-09 | Disponibilidad | ≥99 % de uptime en horario operativo | OBJ-07 |
| CE-10 | Usabilidad | Reporte por equipo ≤3 min sin capacitación | OBJ-01 |

Cada criterio declara su objetivo en la propia tabla del charter (columna "Obj."); esa correspondencia es la única que la constitución ofrece. La cadena hacia los requisitos de la ERS —qué `RF`/`RNF` mide cada criterio— no la traza ningún documento hasta que la [[matriz-de-trazabilidad]] la reconstruye el 5 de septiembre de 2026.

## Lo que la matriz de trazabilidad descubrió al cruzar esto con los requisitos

**CE-06 (Plazo) y CE-07 (Presupuesto) no los mide ningún requisito de la ERS**, y **OBJ-06 no tiene ningún requisito que lo mida** — ni funcional, ni no funcional, ni de dominio. La razón es coherente con la naturaleza de ambos: plazo y presupuesto se gestionan y verifican a nivel de proyecto, no de producto, y ningún requisito de software podría medirlos por sí solo. Es la única ausencia de este tipo que no cuenta como defecto de la especificación.

Los ocho criterios restantes sí tienen al menos un requisito que los mide: CE-01 y CE-10 en la generación de reportes (RF-09, RF-15, RNF-20); CE-02 en el autocompletado (RF-11, RNF-02, RNF-05, RNF-07); CE-03 en la hoja de vida (RF-22, RF-24, RF-26, RF-27); CE-04 en la orden de trabajo (RF-01); CE-05 en la exportación de documentos (RF-17, RNF-08); CE-08 en el bloque de rendimiento de 2 segundos (RNF-03, RNF-04, RNF-09, RNF-18, RNF-22); CE-09 en la disponibilidad (RNF-21).

En la dirección contraria, **15 requisitos funcionales y 11 no funcionales no tienen ningún objetivo que los justifique**. El caso más señalado: los cinco requisitos de usuarios y seguridad (RF-49 a RF-53) son el bloque con más implementación de toda la ERS —los cinco lo están— y ninguno de los siete objetivos de la constitución los menciona. Ver [[defectos-conocidos-de-la-ers]] para el detalle completo.

## Notas relacionadas

[[datos-del-proyecto]] · [[estado-de-implementacion]] · [[matriz-de-trazabilidad]] · [[defectos-conocidos-de-la-ers]]
