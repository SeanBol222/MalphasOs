---
name: rf-reportes-mantenimiento
description: RF-09 a RF-17, Reportes de Mantenimiento (ERS 3.2.3). Los seis previstos, sin implementacion
tags: [requisitos, rf, reportes]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.2.3"
estado: vigente
updated: 2026-09-05
---

# 3.2.3. Reportes de Mantenimiento (RF-09, 11, 13, 14, 15, 17)

Seis requisitos, ninguno consecutivo (RF-10, RF-12, RF-16 no existen — ver [[defectos-conocidos-de-la-ers]]). Ninguno implementado: el reporte de mantenimiento no existe como entidad del sistema.

| Código | Requisito | MoSCoW | Estado | Evidencia |
|---|---|---|---|---|
| RF-09 | Generar reporte individual por equipo de la orden | Must Have | Previsto | No hay tabla ni agregado de reporte. `equipment/infrastructure/input/model/report/` existe creado y vacío: es el sitio reservado, no una implementación |
| RF-11 | Autocompletar cliente/sede/responsables/servicio desde la OT | Should Have | Previsto | Depende del reporte y de la OT, ninguno existe; los datos que se autocompletarían sí son consultables por identificador |
| RF-13 | Asignación automática de protocolo por tipo de equipo y servicio | Should Have | Previsto | No hay protocolos (RF-14); tampoco está modelado el "tipo de servicio", la otra mitad de la clave |
| RF-14 | Configuración previa de protocolos por tipo de equipo/servicio | Sin asignar | Previsto | La palabra "protocolo" no aparece en el esquema ni en el código |
| RF-15 | Registro de información técnica (falla, diagnóstico, procedimientos, observaciones, resultado) | Must Have | Previsto | Ninguno de esos cinco campos tiene columna en el esquema |
| RF-17 | Exportar reporte a PDF | Could Have | Previsto | No hay dependencia de generación de PDF declarada ni código que produzca documentos |

## Dependencias declaradas

RF-09 depende de RF-05, RF-07. RF-11 de RF-05, RF-08, RF-09. RF-13 de RF-09, RF-14. RF-14 de RF-22. RF-15 de RF-09, RF-11, RF-13. RF-17 de RF-15, **RF-21** (dependencia cruzada hacia firma digital).

## Un desajuste con el apartado 2.2

El apartado 2.2 (Funciones del producto) promete exportación tanto en PDF como en Excel. **RF-16, el requisito de Excel, no existe** en el apartado 3.2 pese a citarse en 3.1.3 junto a RF-17. RF-17 solo cubre PDF.

## Notas relacionadas

[[rf-hojas-vida]] · [[rf-firma-digital]] · [[estado-de-implementacion]] · [[defectos-conocidos-de-la-ers]] · [[glosario-dominio]]
