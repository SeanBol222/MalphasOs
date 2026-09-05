---
name: datos-del-proyecto
description: Identificación del proyecto MalphasOS - cliente, siglas, duración, presupuesto, fases, autoría
tags: [proyecto, constitucion]
fuente: Documentation/constitutionDocument/constitutionDocument.tex
estado: vigente
updated: 2026-09-05
---

# Datos del proyecto

| Campo | Valor |
|---|---|
| Proyecto | MalphasOS, versión 1.0 |
| Siglas | **MSO** (usadas en el Plan de Gestión del Alcance, no en la ERS ni en el charter) |
| Cliente / patrocinador | BolívarBioingeniería LTDA |
| Representante del cliente | Sandra Bolívar, gerente de BolívarBioingeniería LTDA |
| Duración total | 6 meses (26 semanas en el Gantt teórico) |
| Fase 1 (MVP) | 1,5 meses |
| Fase 2 (sistema completo) | 6 meses (incluye la Fase 1) |
| Presupuesto | COP $15.000.000, cubre ambas fases y 3 años de licencia |
| Autor / director de proyecto | Sean Sebastián Bolívar Calderón (20231020135) |
| Institución | Universidad Distrital Francisco José de Caldas |
| Asignatura | Calidad de Software |
| Revisor (constitución) | Alfredo López Hernández |
| Fecha de la ERS y de la constitución | 11 de agosto de 2026 |
| Fecha del Plan de Gestión del Alcance | 2 de septiembre de 2026 |
| Fecha de la Matriz de Trazabilidad | 5 de septiembre de 2026 |

## Todos los roles de gestión recaen en la misma persona

El Plan de Gestión del Alcance lo dice sin rodeos: Sean Sebastián Bolívar Calderón es a la vez patrocinador, gerente de proyecto, analista de requisitos y desarrollador. No hay equipo, ni comité de control de cambios, ni contraparte externa que apruebe entregables. El cliente (BolívarBioingeniería LTDA) participó en la elicitación de requisitos pero no vuelve a intervenir en los ciclos de verificación — no existe un acta de aceptación firmada por el cliente para ningún entregable.

Esto no es un detalle anecdótico: condiciona cómo se lee el control de cambios ([[plan-gestion-alcance]]) y por qué el proyecto se apoya tanto en criterios de aceptación escritos de antemano y en una batería de pruebas automatizada en vez de en revisión humana independiente.

## Sistema y nombre

El sistema se llama formalmente **MalphasOS: Gestión de Clientes** (apartado 1.2.1 de la ERS). Es una plataforma web para BolívarBioingeniería LTDA que reemplaza un entorno fragmentado: un programa parcial de reportes, hojas de vida en Excel y coordinación por WhatsApp.

## Único documento que registra cliente, duración y presupuesto

Estos tres datos —el nombre del cliente, la duración estimada y el presupuesto— solo constan en `Documentation/CLAUDE.md` (ahora migrado a esta wiki) y en el documento de constitución. Ningún otro artefacto de `malphasos/` ni de `SecondBrain/` los recoge.

## Notas relacionadas

[[objetivos-y-criterios-de-exito]] · [[interesados-riesgos-hitos]] · [[documento-constitucion]] · [[plan-gestion-alcance]]
