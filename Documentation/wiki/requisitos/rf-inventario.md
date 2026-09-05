---
name: rf-inventario
description: RF-36 y RF-37, Inventario (ERS 3.2.6). Won't Have, previstos. No confundir con el inventario de equipos del cliente
tags: [requisitos, rf, inventario]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.2.6"
estado: vigente
updated: 2026-09-05
---

# 3.2.6. Inventario (RF-36, RF-37)

| Código | Requisito | MoSCoW | Estado |
|---|---|---|---|
| RF-36 | Módulo de inventario: registrar ítems (nombre, tipo, cantidad, estado) | Won't Have | Previsto |
| RF-37 | Entrada/salida de ítems durante el mantenimiento | Won't Have | Previsto |

Ambos dependen de **RF-35, que no existe como requisito** — ver [[defectos-conocidos-de-la-ers]].

## La confusión que este par de requisitos invita a cometer

El código **sí** tiene una tabla que el propio código llama "inventario de un área": `equipo_cliente`. **No es lo mismo que este requisito pide.** `equipo_cliente` es el censo de las unidades físicas que posee un cliente —una fila por equipo, identificado por su número de serie, sin cantidad—; RF-36/RF-37 piden el inventario de existencias de **BolívarBioingeniería** —herramientas, repuestos y equipos con cantidad disponible que sube y baja con cada movimiento—. La ERS lo aclara explícitamente en la evidencia de RF-36 precisamente para prevenir esta confusión.

Ver [[glosario-dominio]] para la distinción "equipo (unidad del cliente)" vs. "inventario de existencias (herramientas y repuestos de la empresa)".

## Notas relacionadas

[[glosario-dominio]] · [[rf-hojas-vida]] · [[estado-de-implementacion]] · [[priorizacion-moscow]]
