---
name: rf-firma-digital
description: RF-18 y RF-21, Firma Digital (ERS 3.2.4). Ambos previstos, sin soporte en ninguna capa
tags: [requisitos, rf, firma-digital]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.2.4"
estado: vigente
updated: 2026-09-05
---

# 3.2.4. Firma Digital (RF-18, RF-21)

| Código | Requisito | MoSCoW | Estado | Evidencia |
|---|---|---|---|---|
| RF-18 | Captura de firma digital táctil del cliente/responsable | **Sin asignar** | Previsto | Ninguna tabla ni columna del esquema contempla una firma; no hay interfaz de usuario en el repositorio (solo API REST) |
| RF-21 | Replicar la firma a todos los reportes de la misma OT | **Sin asignar** | Previsto | Depende de RF-18 y de la orden de trabajo; ninguno de los dos existe |

Dependencias: RF-18 depende de RF-20 (**código que no existe** — ver [[defectos-conocidos-de-la-ers]]). RF-21 depende de RF-18.

## La contradicción de prioridad más citada de la ERS

El párrafo introductorio de la categoría Won't Have del apartado 3.5, en prosa, dice explícitamente: "entre ellas se encuentran la firma digital unificada". **Pero ni RF-18 ni RF-21 figuran en la lista de códigos de esa categoría** — de hecho no figuran en ninguna de las cuatro listas MoSCoW. La narrativa dice Won't Have; la lista de códigos no los clasifica en absoluto. Ver [[priorizacion-moscow]].

## Notas relacionadas

[[priorizacion-moscow]] · [[rf-reportes-mantenimiento]] · [[defectos-conocidos-de-la-ers]] · [[diagramas-de-casos-de-uso]]
