---
name: documento-constitucion
description: Qué es el Project Charter de MalphasOS, qué contiene y cómo está estructurado
tags: [documento, constitucion, charter]
fuente: Documentation/constitutionDocument/constitutionDocument.tex
estado: vigente
updated: 2026-09-05
---

# Documento de Constitución (Project Charter)

- **Archivo**: `constitutionDocument/constitutionDocument.tex` (482 líneas) → `constitutionDocument/constitutionDocument.pdf` (12 páginas, reducido de 21 en el borrador original)
- **Fecha**: 11 de agosto de 2026
- **Firmado por**: Sean Sebastián Bolívar Calderón (elaborado) y Alfredo López Hernández (revisado)

## Estructura, sección por sección

| Sección | Contenido | Nota de esta wiki |
|---|---|---|
| 1. Descripción del proyecto | Usuarios, fases, plazo y entorno | [[datos-del-proyecto]] |
| 2. Descripción del producto | Módulos por fase, características técnicas | [[datos-del-proyecto]] |
| 3. Objetivos | 1 general + 7 específicos (OBJ-01 a OBJ-07) | [[objetivos-y-criterios-de-exito]] |
| 4. Criterios de éxito | 10 criterios (CE-01 a CE-10) en tabla `longtable` | [[objetivos-y-criterios-de-exito]] |
| 5. Requisitos de aprobación | 5 categorías: funcionales mínimos, calidad, seguridad, regulatorios, entrega | ver más abajo |
| 6. Finalidad del proyecto | Beneficios para la empresa y para cada tipo de usuario | — |
| 7. Entregables principales | Del proyecto, de cada fase y de soporte | — |
| 8. Justificación | Problema identificado, necesidad de negocio, oportunidad estratégica | — |
| 9. Principales interesados | 8 interesados en tabla | [[interesados-riesgos-hitos]] |
| 10. Riesgos iniciales | 10 riesgos (R-01 a R-10) | [[interesados-riesgos-hitos]] |
| 11. Duración e hitos | 5 hitos (H-00 a H-04) + Gantt de 26 semanas con `pgfgantt` | [[interesados-riesgos-hitos]] |
| 12. Presupuesto | COP $15.000.000 | [[datos-del-proyecto]] |
| 13-14. Sponsor y Director | Roles y responsabilidades | [[datos-del-proyecto]] |

## Decisiones de diseño del documento

- Mantiene todas las secciones que un charter de este tipo suele llevar; ninguna se eliminó al recortarlo.
- Se redujo a la mitad de su extensión original (de 974 a 482 líneas LaTeX) sin quitar secciones, condensando la redacción.
- El Gantt está integrado con el paquete `pgfgantt`, no como imagen externa — se recompila junto con el resto del documento.
- Los colores del Gantt y de los acentos toman la paleta ampliada del design system, no solo los tres colores base de la ERS. Ver [[paleta-de-colores]].

## La única pista sobre los requisitos de dominio

El apartado 5.1 ("Requisitos funcionales mínimos") asocia `RD-03--04` a "Registro de equipos" — es la **única** referencia de contenido a esos códigos en todo el repositorio; ni siquiera la ERS, que los cita, los define. Ver [[requisitos-de-dominio]].

## Notas relacionadas

[[datos-del-proyecto]] · [[objetivos-y-criterios-de-exito]] · [[interesados-riesgos-hitos]] · [[requisitos-de-dominio]] · [[convenciones-latex]]
