---
name: rf-firma-digital
description: RF-18 y RF-21, Firma Digital (ERS 3.2.4). Los dos implementados el 2026-10-17, con la versión 1.0.0
tags: [requisitos, rf, firma-digital]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.2.4"
estado: vigente
updated: 2026-10-30
---

# 3.2.4. Firma Digital (RF-18, RF-21)

| Código | Requisito | MoSCoW | Estado | Evidencia |
|---|---|---|---|---|
| RF-18 | Captura de firma digital táctil del cliente/responsable | **Sin asignar** | **Implementado** (2026-10-17) | Quien recibe firma en la pantalla del reporte, antes o después de cerrarlo, con el dedo, un lápiz o el ratón; trazo en vivo de grosor constante y «Limpiar»; sin trazo no se envía nada y se pide la firma. `V39`, `PATCH /v1/api/reports/{id}/receipt-signature`. Es una aplicación web en el navegador del móvil, no una nativa |
| RF-21 | Replicar la firma a todos los reportes de la misma OT | **Sin asignar** | **Implementado** (2026-10-17), con un matiz | Desde el 2026-10-30, tres opciones en vez de la casilla «Aplicar esta firma a los demás reportes de la orden que aún no la tengan»: solo este reporte, los del área de este equipo en la orden, o todos los de la orden. Replica imagen y datos de quien firma a los reportes **de esa orden** —el servidor los busca por el identificador de la orden—, abiertos o cerrados. **No** a los que ya tienen firma, que no cambia, ni a los retirados: decisión del usuario |

> **Corregido el 2026-10-17.** Esta nota daba los dos por previstos, «sin soporte en ninguna capa». Era
> cierto hasta ese día. La firma del ingeniero, que la ERS no pide, se construyó también: vive en su
> cuenta y cada informe que cierra la copia (`V38`).

Dependencias: RF-18 depende de RF-20 (**código que no existe** — ver [[defectos-conocidos-de-la-ers]]). RF-21 depende de RF-18.

## La contradicción de prioridad más citada de la ERS

El párrafo introductorio de la categoría Won't Have del apartado 3.5, en prosa, dice explícitamente: "entre ellas se encuentran la firma digital unificada". **Pero ni RF-18 ni RF-21 figuran en la lista de códigos de esa categoría** — de hecho no figuran en ninguna de las cuatro listas MoSCoW. La narrativa dice Won't Have; la lista de códigos no los clasifica en absoluto. Ver [[priorizacion-moscow]].

## Notas relacionadas

[[priorizacion-moscow]] · [[rf-reportes-mantenimiento]] · [[defectos-conocidos-de-la-ers]] · [[diagramas-de-casos-de-uso]]
