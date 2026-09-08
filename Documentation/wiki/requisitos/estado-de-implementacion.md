---
name: estado-de-implementacion
description: La cifra agregada de implementacion de MalphasOS -8 de 31 RF, 1 de 23 RNF- con como se verifico y donde esta el detalle
tags: [requisitos, estado, trazabilidad]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.2; Documentation/TraceabilityMatrix/MatrizDeTrazabilidad.tex"
estado: vigente
updated: 2026-09-05
---

# Estado de implementación agregado

## La cifra

| Tipo de requisito | Total | Implementado | Previsto | Fuente de la evaluación |
|---|---|---|---|---|
| Funcionales (RF) | 31 | **8** | 23 | ERS, apartado 3.2 (verificado de nuevo por la matriz) |
| No funcionales (RNF) | 23 | **1** (RNF-23, JWT) | 22 | Matriz de trazabilidad (primera evaluación; la ERS no marca RNF) |
| De dominio (RD) | 6 | 2 inferidos (RD-03, RD-04) | 4 no verificables | Matriz de trazabilidad, con salvedad — ver [[requisitos-de-dominio]] |

## Los 8 requisitos funcionales implementados

| Código | Categoría | Requisito |
|---|---|---|
| RF-08 | Gestión de Clientes | Crear cliente |
| RF-22 | Hojas de Vida | Crear hoja de vida |
| RF-24 | Hojas de Vida | Modificar/eliminar hoja de vida |
| RF-49 | Usuarios y seguridad | Login de usuario |
| RF-50 | Usuarios y seguridad | Identificación de rol |
| RF-51 | Usuarios y seguridad | Creación de usuarios |
| RF-52 | Usuarios y seguridad | Modificación de usuarios |
| RF-53 | Usuarios y seguridad | Eliminación de usuarios |

Tres categorías fundacionales concentran todo lo construido: gestión de clientes, catálogo de equipos (parte de hojas de vida) y usuarios/seguridad. Las seis categorías del flujo operativo —órdenes de trabajo, reportes, firma digital, inventario, alertas, módulo comercial— siguen `[PREVISTO]` en su totalidad. El [[plan-gestion-alcance]] explica el porqué del orden: "casi todos los requisitos dependen de RF-49 y RF-50, y las categorías operativas dependen de los datos maestros: no hay orden de trabajo sin cliente, sin sus sedes y áreas ni sin los equipos de la hoja de vida".

## Por categoría (apartado 3.2 de la ERS)

| Categoría | Rango | Total | Implementados | Nota de esta wiki |
|---|---|---|---|---|
| 3.2.1 Órdenes de Trabajo | RF-01 a RF-07 | 7 | 0 | [[rf-ordenes-trabajo]] |
| 3.2.2 Clientes | RF-08 | 1 | 1 | [[rf-clientes]] |
| 3.2.3 Reportes de Mantenimiento | RF-09 a RF-17 | 6 | 0 | [[rf-reportes-mantenimiento]] |
| 3.2.4 Firma Digital | RF-18, RF-21 | 2 | 0 | [[rf-firma-digital]] |
| 3.2.5 Hojas de Vida | RF-22 a RF-27 | 4 | 2 | [[rf-hojas-vida]] |
| 3.2.6 Inventario | RF-36, RF-37 | 2 | 0 | [[rf-inventario]] |
| 3.2.7 Alertas y calibración | RF-40, RF-41 | 2 | 0 | [[rf-alertas-calibracion]] |
| 3.2.8 Módulo Comercial | RF-45, RF-47 | 2 | 0 | [[rf-modulo-comercial]] |
| 3.2.9 Usuarios y seguridad | RF-49 a RF-53 | 5 | 5 | [[rf-usuarios-seguridad]] |
| **Total** | | **31** | **8** | |

## Cómo se verificó, y cuándo

Dos verificaciones independientes, tres días de diferencia, mismo resultado:

1. **2 de septiembre de 2026** — la ERS se revisó requisito por requisito contra el código y se añadió el punto "Estado en la implementación actual" a cada uno de los 31 RF.
2. **5 de septiembre de 2026** — la matriz de trazabilidad **repitió** la verificación en vez de copiar el estado ya declarado, contra los controladores REST, los agregados de dominio y las cinco migraciones (`V1` a `V5`). Coincidió con la primera. Es también la primera vez que se evalúa el estado de los 23 RNF y los 6 RD, que la ERS nunca marca.

Un efecto lateral que ambos documentos registran: **contrastar la especificación contra el código resultó ser también una técnica de detección de defectos**. Salieron seis fallos del código —dos de seguridad— que ninguna revisión del sistema por separado había revelado (ver `SecondBrain/wiki/malphasos/deuda-tecnica-y-riesgos.md` para el detalle de esos seis).

## Antes de citar una cifra de estado

Verificar contra `malphasos/` directamente si ha pasado tiempo desde la fecha de este documento — esta wiki registra el estado a una fecha concreta, no lo recalcula. Si el código cambió, esta nota queda desactualizada hasta que se corrija explícitamente (ver la regla en `wiki/CLAUDE.md`).

## Notas relacionadas

[[regla-implementado-vs-previsto]] ⭐ · [[matriz-de-trazabilidad]] · [[plan-gestion-alcance]] · [[requisitos-no-funcionales]] · [[requisitos-de-dominio]]
