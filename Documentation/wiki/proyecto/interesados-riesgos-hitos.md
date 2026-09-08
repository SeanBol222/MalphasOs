---
name: interesados-riesgos-hitos
description: Los 8 interesados, 10 riesgos (R-01 a R-10) y 5 hitos (H-00 a H-04) del documento de constitución
tags: [proyecto, riesgos, hitos, constitucion]
fuente: Documentation/constitutionDocument/constitutionDocument.tex
estado: vigente
updated: 2026-09-05
---

# Interesados, riesgos e hitos

## Interesados

| Interesado | Rol | Relación | Influencia |
|---|---|---|---|
| BolívarBioingeniería LTDA | Cliente | Define requisitos, aprueba y financia | Alta |
| Gerente (Sandra Bolívar) | Patrocinador | Decisiones, alcance, presupuesto | Alta |
| Personal administrativo | Usuario principal | Gestión diaria, desde escritorio | Media |
| Ingenieros técnicos | Usuario en campo | Registro móvil de intervenciones | Media |
| SuperUsuario | Admin técnico | Configuración inicial y accesos | Media |
| Clientes (IPS, hospitales) | Receptor de documentos | Reciben reportes y hojas de vida | Baja |
| Secretaría de Salud | Regulatoria | Define requisitos normativos | Media |
| Equipo de desarrollo | Ejecutor | Diseño, desarrollo, implementación | Alta |

## Riesgos (R-01 a R-10)

| Código | Riesgo | Tipo | Impacto | Mitigación |
|---|---|---|---|---|
| R-01 | Cambio de requisitos regulatorios de la Secretaría de Salud | Externo | Alto | Diseño modular de reportes; monitoreo de normativas |
| R-02 | Resistencia al cambio del personal | Organizacional | Medio | Involucrar usuarios temprano; capacitación |
| R-03 | Conectividad limitada en clientes | Técnico | Medio | Optimizar consumo de datos; documentar limitaciones |
| R-04 | Incompatibilidad de la firma digital en móvil | Técnico | Alto | Validar compatibilidad Android/iOS antes del MVP |
| R-05 | Migración de datos incompleta | Técnico | Alto | Protocolo de migración; validar integridad con el cliente |
| R-06 | Incumplimiento del plazo del MVP (1,5 meses) | Cronograma | Alto | Priorización Must Have; seguimiento semanal |
| R-07 | Superar el presupuesto de $15M | Presupuesto | Alto | Control de alcance por MoSCoW; seguimiento de costos |
| R-08 | Baja adopción de los ingenieros técnicos | Usuarios | Alto | Interfaz intuitiva (máximo 6 pasos); capacitación en el diseño |
| R-09 | Cambios en la estructura organizacional | Organizacional | Medio | Roles configurables por el SuperUsuario |
| R-10 | Scope creep no controlado | Alcance | Medio | Gestión formal de cambios; la ERS como línea base |

Casi todos los riesgos de mayor impacto (R-04 a R-08) apuntan a piezas que la ERS marca como `[PREVISTO]` o `Won't Have` —firma digital, migración de datos, orden de trabajo, adopción de los ingenieros—: el charter identificó como riesgo justo lo que terminó sin construirse. No es una coincidencia que se pueda cerrar sin evidencia adicional, pero conviene señalarla en cualquier análisis de por qué el alcance real quedó tan por debajo del planteado.

## Hitos (H-00 a H-04)

| Fase | Hito | Descripción | Plazo |
|---|---|---|---|
| Fase 0 | H-00 | Inicio formal, aprobación del Project Charter y del plan de proyecto | Mes 0 |
| Fase 1 | H-01 | Entrega del MVP: autenticación, clientes, órdenes de trabajo, reportes, equipos | 1,5 meses |
| Fase 2 | H-02 | Sistema completo: firma, hojas de vida, inventario, alertas, comercial, PDF/Excel | 6 meses |
| Fase 2 | H-03 | Capacitación de usuarios | Mes 6 |
| Fase 2 | H-04 | Cierre: aceptación formal, documentación técnica, manuales | Mes 6 |

## El cronograma de 26 semanas

El Gantt de `constitutionDocument.pdf` (construido con `pgfgantt`, sin imagen externa) es **teórico** — el propio documento no afirma que se haya seguido en la práctica. Se divide en:

| Período | Duración | Contenido |
|---|---|---|
| Semana 1 | 1 semana | Landing page de la empresa |
| Semanas 2-7 | 6 semanas | Fase 1 (MVP): autenticación, clientes, órdenes de trabajo, reportes, equipos |
| Semanas 8-26 | 19 semanas | Fase 2: firma digital, hojas de vida, inventario, alertas, módulo comercial, exportación PDF/Excel, capacitación |

El color de cada barra sigue la paleta ampliada del proyecto, en rojos progresivamente más oscuros por módulo — ver [[paleta-de-colores]].

## Notas relacionadas

[[datos-del-proyecto]] · [[objetivos-y-criterios-de-exito]] · [[documento-constitucion]] · [[paleta-de-colores]]
