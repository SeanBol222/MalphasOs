---
name: rf-ordenes-trabajo
description: RF-01 a RF-07, Gestion de Ordenes de Trabajo (ERS 3.2.1). Seis implementados entre el 2026-09-13 y el 2026-09-27; RF-04 queda como desviacion consciente
tags: [requisitos, rf, ordenes-trabajo]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.2.1"
estado: vigente
updated: 2026-09-27
---

# 3.2.1. Gestión de Órdenes de Trabajo (RF-01 a RF-07)

**Seis de los siete están implementados**, y el séptimo es una desviación consciente.

> ⚠️ **Corregido el 2026-09-27, y la nota llevaba dos semanas caducada.** Decía «ninguno de los siete requisitos tiene implementación. El dominio entero está `[PREVISTO]`». Era cierto el 2026-09-05 y dejó de serlo el **2026-09-13**, cuando el módulo `work-order` se construyó entero —esquema `V6`, agregado, servicio con siete reglas cruzadas y nueve operaciones REST—, y otra vez el **2026-09-27** con sus cuatro pantallas. La evidencia de cada fila hablaba además de «las cinco migraciones (`V1` a `V5`)», que hoy son nueve. El detalle técnico está en `SecondBrain/wiki/dominio/dominio-orden-trabajo.md`.

| Código | Requisito | MoSCoW | Estado | Evidencia |
|---|---|---|---|---|
| RF-01 | Crear orden de trabajo directa, sin solicitud previa | Must Have | **Implementado** (2026-09-13) | `V6__work_order.sql`, agregado `WorkOrder` y `POST /v1/api/work-orders` |
| RF-02 | Eliminación de dependencia de solicitud | Must Have | **Implementado por construcción** | La palabra "solicitud" no aparece en el esquema ni en el código: no había nada que retirar |
| RF-03 | Formulario de orden de trabajo (Cliente, Sede, Áreas, Tipo de servicio, Periodicidad) | Must Have | **Implementado** (2026-09-27) | *Tipo de servicio* y *periodicidad* son columnas de `orden_trabajo` con `CHECK` propio desde `V6`. La pantalla lo hace **en dos pasos**: se programa la visita y los equipos se añaden después, que es lo que el API ofrece y lo que ocurre de verdad |
| RF-04 | Selección múltiple de áreas de servicio de la sede | Must Have | ⚠️ **Desviación consciente** (2026-09-27) | La pantalla **no** pide elegir áreas y luego mostrar sus equipos: muestra todas las áreas abiertas de la sede a la vez, agrupadas, con «marcar toda el área» como equivalente. Se llega al mismo sitio con un paso menos, pero **el paso intermedio que el requisito describe no existe**. Las áreas elegidas nunca se persistieron a propósito: quedan implícitas en los equipos |
| RF-05 | Identificador único (UUID) de la orden | Must Have | **Implementado** | `k_id_orden_trabajo uuid`, por la convención de llaves del esquema |
| RF-06 | Visualización de equipos por área seleccionada | Should Have | **Implementado** (2026-09-27) | La pantalla de alcance agrupa por área y ofrece **solo** equipos de áreas de la sede de la orden, que es una de las siete reglas que el esquema dejó al servicio |
| RF-07 | Selección múltiple de equipos de las áreas elegidas | Must Have | **Implementado** (2026-09-27) | Casillas por equipo y por área. El API suma **de uno en uno**, así que la pantalla manda las llamadas en serie y **dice cuántas entraron** si alguna falla: no hay transacción que las envuelva |

## Dependencias declaradas

RF-01 depende de RF-49, RF-50 (autenticación e identificación de rol). RF-02 depende de RF-01. RF-03 depende de RF-01, RF-08. RF-04 depende de RF-03, RF-08. RF-05 depende de RF-03, RF-04, RF-07. RF-06 depende de RF-05, RF-09. RF-07 depende de RF-06, RF-04.

> **Ese grafo contiene un ciclo, y el módulo se construyó ignorándolo sin ningún bloqueo** (comprobado el 2026-09-13). RF-05 depende de RF-07, RF-07 de RF-06, RF-06 de RF-05: leído al pie de la letra, ninguno de los tres puede empezar. Identificar la orden, elegir sus áreas y elegir sus equipos son partes de un mismo agregado y salieron juntas. Es un defecto de la ERS, no un bloqueo real — ver [[defectos-conocidos-de-la-ers]].

## Diagrama de casos de uso

`use_cases/ordenes_trabajo/ordenes_trabajo.puml` — incluido en la ERS, precedido del resto de maquetación `[DIAGRAMA DE CASOS DE USO - GESTIÓN DE ÓRDENES DE TRABAJO]`. Ver [[diagramas-de-casos-de-uso]].

## Notas relacionadas

[[estado-de-implementacion]] · [[priorizacion-moscow]] · [[glosario-dominio]] · [[defectos-conocidos-de-la-ers]]
