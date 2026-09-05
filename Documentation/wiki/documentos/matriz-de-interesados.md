---
name: matriz-de-interesados
description: Qué es la Matriz de Interesados, cómo está construida como standalone y qué revela el cruce de actores
tags: [documento, interesados, stakeholders, charter]
fuente: Documentation/StakeholdersMatrix/MatrizDeInteresados.tex
estado: vigente
updated: 2026-09-05
---

# Matriz de Interesados del Proyecto (Stakeholders)

- **Archivo**: `StakeholdersMatrix/MatrizDeInteresados.tex` → `.pdf`
- **Fecha**: 5 de septiembre de 2026
- **Formato**: clase `standalone` (variante `varwidth`, ancho 170mm), idéntica a [[matriz-de-trazabilidad]]. Diseñada para lectura continua con zoom o plotter, sin paginación A4 ni `longtable`.

## Por qué existe

La Sección 9 del Documento de Constitución lista a los 8 principales interesados en una tabla resumida (rol, relación e influencia), pero no analiza sus expectativas operativas, el cruce con los objetivos (`OBJ-01` a `OBJ-07`), la trazabilidad con los requisitos de la ERS ni el encadenamiento con los 10 riesgos del charter (`R-01` a `R-10`). Este documento formaliza el análisis sistemático de interesados bajo la metodología Poder vs. Interés (PMBOK / IEEE).

## Qué encadena

Tres matrices articuladas:
1. **Identificación y categorización**: Cruza a los 8 interesados (STK-01 a STK-08) con su tipo (interno/externo), rol formal y posición en el cuadrante de Poder vs. Interés (*Gestionar de cerca*, *Mantener satisfecho*, *Mantener informado*, *Monitorear*).
2. **Expectativas y trazabilidad de requisitos**: Vincula las expectativas concretas de cada actor con los objetivos estratégicos y el estado real de implementación de los requisitos asociados (funcionales y no funcionales).
3. **Estrategia de comunicación y riesgos**: Define la estrategia de relacionamiento, la frecuencia y canales de contacto, y los riesgos del charter que mitiga cada interacción.

## La sección 4, Hallazgos y análisis crítico

Registra cuatro anomalías estructurales del proyecto derivadas del cruce de interesados:
1. **Unipersonalidad y autogestión de control**: Sean Sebastián Bolívar Calderón concentra dirección, análisis, arquitectura y desarrollo, sin equipo colegiado ni contraparte externa que firme actas periódicas de aceptación tras cada incremento.
2. **Discrepancia de seguridad técnica en Keycloak**: El SuperUsuario carece de endpoints y permisos específicos en Spring Boot, y las 83 operaciones REST del backend exigen `admin.full`, dejando a los ingenieros técnicos (campo) y clientes sin acceso operativo real a la API (403 Forbidden).
3. **Desfase en la entrega de valor a los ingenieros de campo**: El usuario que concentra los riesgos de adopción más altos (`R-02`, `R-04`, `R-08`) tiene todas sus herramientas (órdenes, reportes, firma móvil) en estado `[PREVISTO]`.
4. **Tensión regulatoria**: La exclusión de las alertas de calibración en la ERS (*Won't Have*) vulnera la expectativa de cumplimiento integral exigida por la Secretaría de Salud e INVIMA.

## Notas relacionadas

[[interesados-riesgos-hitos]] · [[documento-constitucion]] · [[matriz-de-trazabilidad]] · [[plan-gestion-alcance]] · [[regla-implementado-vs-previsto]]
