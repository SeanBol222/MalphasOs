---
name: rf-reportes-mantenimiento
description: RF-09 a RF-17, Reportes de Mantenimiento (ERS 3.2.3). RF-09 y RF-15 implementados el 2026-09-27, RF-11 el 2026-09-28, RF-13 y RF-14 el 2026-10-10; RF-17 espera la firma
tags: [requisitos, rf, reportes]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.2.3"
estado: vigente
updated: 2026-10-16
---

# 3.2.3. Reportes de Mantenimiento (RF-09, 11, 13, 14, 15, 17)

Seis requisitos, ninguno consecutivo (RF-10, RF-12, RF-16 no existen — ver [[defectos-conocidos-de-la-ers]]). **Tres implementados**: dos desde el 2026-09-27 y **RF-11 desde el 2026-09-28**. El reporte de mantenimiento existe como entidad del sistema —con su tabla, su agregado y su API— y **se puede llenar desde un navegador**.

> ⚠️ **Corregido el 2026-09-27.** Esta nota decía «ninguno implementado: el reporte de mantenimiento no existe como entidad del sistema», y la tabla marcaba los seis como previstos. Era cierto hasta ese día. El módulo `report` se construyó en cuatro tandas —esquema `V9`, agregado, aplicación con persistencia probada y REST— y cierra **RF-09** y **RF-15**. El detalle técnico está en `SecondBrain/wiki/dominio/dominio-reporte-servicio.md`.

| Código | Requisito | MoSCoW | Estado | Evidencia |
|---|---|---|---|---|
| RF-09 | Generar reporte individual por equipo de la orden | Must Have | **Implementado** (2026-09-27) | Tabla `reporte_servicio` y agregado `ServiceReport`. **Uno por equipo**, con llave foránea **compuesta** contra `orden_trabajo_equipo`: el esquema impide por sí solo un reporte de un equipo que la orden no incluyó. `GET /v1/api/reports?idOrdenTrabajo=` es el «acceder al reporte desde la orden» que pide su tercer criterio |
| RF-11 | Autocompletar cliente/sede/responsables/servicio desde la OT | Should Have | **Implementado** (2026-09-28) | La ficha del reporte trae un bloque de «datos del servicio» con cliente, sede, tipo de servicio, fecha programada, ingeniero y encargado de la sede. Los tres criterios se cumplen y por construcción: **se leen de la orden y no se copian** —el reporte no tiene columna para ellos—, de modo que no pueden discrepar, y **no hay ningún control que editar**, que es además lo que pide RNF-07. Hay una prueba que cuenta los controles del formulario y falla si uno de esos datos aparece como campo |
| RF-13 | Asignación automática de protocolo por tipo de equipo y servicio | Should Have | **Implementado** el 2026-10-10 | Al abrir el reporte de una orden **preventiva** se copian como pasos, solos y en su orden, los protocolos asignados al tipo del equipo; en el reporte se marca cada uno hecho o no aplica. `V22`, `paso_reporte`. (Esta fila decía «No hay protocolos»: cierto hasta el **2026-10-10**; y antes, que el tipo de servicio no estaba modelado: cierto hasta el **2026-09-12**) |
| RF-14 | Configuración previa de protocolos por tipo de equipo/servicio | Sin asignar | **Implementado** el 2026-10-10, con una interpretación | Un protocolo es una actividad reutilizable que se **asigna** a los tipos de equipo con una prioridad, `V21`. **Solo el preventivo lleva protocolo**, decidido por el usuario: el tipo de servicio no se elige por protocolo. Ver [[estado-de-implementacion]]. (Esta fila decía que la palabra «protocolo» no aparecía en el código: cierto hasta el **2026-10-10**) |
| RF-15 | Registro de información técnica (falla, diagnóstico, procedimientos, observaciones, resultado) | Must Have | **Implementado** (2026-09-27) | Los cinco campos son columnas de `reporte_servicio`. Son **nulos en borrador** —el reporte se llena en campo— y al cerrarlo se exigen **dos**: procedimientos y resultado. Cada reporte se guarda por separado y se consulta después por su identificador, por su orden o por su equipo |
| RF-17 | Exportar reporte a PDF | Could Have | Previsto | **Desde el 2026-10-16 el reporte se imprime o se guarda en PDF desde el navegador**, con el formato que la empresa ya entrega. **Sigue previsto**: el requisito depende de RF-21 y pide el PDF del reporte **firmado**, y no hay firma ni PDF generado por el sistema. (Decía «no hay código que produzca documentos»: cierto hasta ese día.) |

## Dependencias declaradas

RF-09 depende de RF-05, RF-07. RF-11 de RF-05, RF-08, RF-09. RF-13 de RF-09, RF-14. RF-14 de RF-22. RF-15 de RF-09, RF-11, RF-13. RF-17 de RF-15, **RF-21** (dependencia cruzada hacia firma digital).

> **Corregido el 2026-09-28.** La fila de RF-11 lo daba por previsto «porque mostrar es una pantalla y no hay pantalla de reportes». Fue cierto exactamente un día.

## Lo que la implementación añadió y la ERS no pedía

Tres cosas que el módulo construido tiene y ningún requisito de esta categoría nombra. Se anotan porque la ERS tendrá que recogerlas o descartarlas:

- **Un estado del reporte**: `BORRADOR` y `FINALIZADO`. La ERS habla de llenar campos y no de cerrar nada, pero sin un cierre no hay forma de distinguir un reporte a medias de uno entregado — y RF-26, el historial de la hoja de vida, necesita esa distinción.
- **Un catálogo cerrado para «resultado»**: `OPERATIVO`, `OPERATIVO_CON_RESTRICCIONES`, `FUERA_DE_SERVICIO`. **RF-15 no enumera valores**, al contrario de lo que la ERS hace con las periodicidades y los tipos de servicio en RF-03. Los tres son una propuesta del código y están marcados como tal.
- **Las lecturas de la verificación metrológica**: tabla `dato_verificacion`, con el valor del patrón, el del equipo y su unidad. La ERS las menciona en la nota de RF-15 solo para decir que **no** son ese requisito —«no confundir con la verificación técnica de un equipo»— y no les dedica ninguno propio. Existen porque un reporte de calibración sin lecturas no se puede entregar.

## Un desajuste con el apartado 2.2

El apartado 2.2 (Funciones del producto) promete exportación tanto en PDF como en Excel. **RF-16, el requisito de Excel, no existe** en el apartado 3.2 pese a citarse en 3.1.3 junto a RF-17. RF-17 solo cubre PDF.

## Notas relacionadas

[[rf-hojas-vida]] · [[rf-firma-digital]] · [[estado-de-implementacion]] · [[defectos-conocidos-de-la-ers]] · [[glosario-dominio]]
