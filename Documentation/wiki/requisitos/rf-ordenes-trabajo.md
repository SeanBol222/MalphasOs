---
name: rf-ordenes-trabajo
description: RF-01 a RF-07, Gestion de Ordenes de Trabajo (ERS 3.2.1). Los siete previstos, sin implementacion
tags: [requisitos, rf, ordenes-trabajo]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.2.1"
estado: vigente
updated: 2026-09-05
---

# 3.2.1. Gestión de Órdenes de Trabajo (RF-01 a RF-07)

Ninguno de los siete requisitos tiene implementación. El dominio entero está `[PREVISTO]` y se levantaría sobre piezas que sí existen: cliente, sede, área de servicio e inventario de equipos del área.

| Código | Requisito | MoSCoW | Estado | Evidencia |
|---|---|---|---|---|
| RF-01 | Crear orden de trabajo directa, sin solicitud previa | Must Have | Previsto | Ninguna de las cinco migraciones (`V1` a `V5`) crea tabla de orden de trabajo; no hay agregado de dominio ni ruta |
| RF-02 | Eliminación de dependencia de solicitud | Must Have | Previsto | La palabra "solicitud" no aparece en el esquema ni en el código: no hay nada que retirar |
| RF-03 | Formulario de orden de trabajo (Cliente, Sede, Áreas, Tipo de servicio, Periodicidad) | Must Have | Previsto | No existe el formulario. *Tipo de servicio* y *periodicidad* ni siquiera tienen columna en el esquema; Cliente, Sede y Área sí (tablas `cliente`, `sede`, `area_servicio`) |
| RF-04 | Selección múltiple de áreas de servicio de la sede | Must Have | Previsto | El filtrado ya existe — `GET /v1/api/headquarters/{idSede}/service-areas` — pero no hay orden a la que asociar la selección |
| RF-05 | Identificador único (UUID) de la orden | Must Have | Previsto | No hay orden que identificar; la convención UUID del esquema se aplica por construcción en cuanto exista |
| RF-06 | Visualización de equipos por área seleccionada | Should Have | Previsto | La consulta ya existe — `GET /v1/api/service-areas/{idAreaServicio}/equipments` — pero falta el formulario y la interfaz que agrupe |
| RF-07 | Selección múltiple de equipos de las áreas elegidas | Must Have | Previsto | Las unidades a seleccionar existen — tabla `equipo_cliente`, `/v1/api/client-equipments` — no hay a qué asociarlas |

## Dependencias declaradas

RF-01 depende de RF-49, RF-50 (autenticación e identificación de rol). RF-02 depende de RF-01. RF-03 depende de RF-01, RF-08. RF-04 depende de RF-03, RF-08. RF-05 depende de RF-03, RF-04, RF-07. RF-06 depende de RF-05, RF-09. RF-07 depende de RF-06, RF-04.

## Diagrama de casos de uso

`use_cases/ordenes_trabajo/ordenes_trabajo.puml` — incluido en la ERS, precedido del resto de maquetación `[DIAGRAMA DE CASOS DE USO - GESTIÓN DE ÓRDENES DE TRABAJO]`. Ver [[diagramas-de-casos-de-uso]].

## Notas relacionadas

[[estado-de-implementacion]] · [[priorizacion-moscow]] · [[glosario-dominio]] · [[defectos-conocidos-de-la-ers]]
