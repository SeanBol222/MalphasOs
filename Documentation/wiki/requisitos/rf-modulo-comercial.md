---
name: rf-modulo-comercial
description: RF-45 y RF-47, Modulo Comercial (ERS 3.2.8). Sin asignar en MoSCoW, previstos. Dependen circularmente entre si
tags: [requisitos, rf, comercial, cotizaciones]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.2.8"
estado: vigente
updated: 2026-09-05
---

# 3.2.8. Módulo Comercial (RF-45, RF-47)

| Código | Requisito | MoSCoW | Estado | Evidencia |
|---|---|---|---|---|
| RF-45 | Crear cotización (cliente, fecha, validez, moneda, descuentos, líneas de precio/cantidad) | Sin asignar | Previsto | No hay tabla ni agregado de cotización. El único dato económico del sistema es el valor unitario de mantenimiento en `tipo_equipo` — la entrada que una cotización usaría para calcular su total |
| RF-47 | Generar orden de trabajo desde una cotización aprobada | Sin asignar | Previsto | Depende de la cotización y de la orden de trabajo; ninguna de las dos existe. El control de stock de su tercer criterio depende además de RF-36 (inventario), también previsto |

RF-45 depende de **RF-46, código que no existe**. RF-47 depende de RF-45.

Es el caso más literal de un dominio construido sobre requisitos ausentes: ninguno de los dos existentes tiene una dependencia resoluble dentro del propio apartado 3.2. Ver [[defectos-conocidos-de-la-ers]].

## Notas relacionadas

[[rf-ordenes-trabajo]] · [[rf-inventario]] · [[defectos-conocidos-de-la-ers]] · [[estado-de-implementacion]]
