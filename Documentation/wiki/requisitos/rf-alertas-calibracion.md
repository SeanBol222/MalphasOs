---
name: rf-alertas-calibracion
description: RF-40 y RF-41, Alertas y calibracion (ERS 3.2.7). Won't Have, previstos. El umbral de 30 dias no esta implementado
tags: [requisitos, rf, calibracion, metrologia]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.2.7"
estado: vigente
updated: 2026-09-05
---

# 3.2.7. Alertas y calibración (RF-40, RF-41)

| Código | Requisito | MoSCoW | Estado |
|---|---|---|---|
| RF-40 | Identificar equipos patrón próximos a vencer su calibración | Won't Have | Previsto |
| RF-41 | Generar alerta al entrar en el umbral de 30 días previos al vencimiento | Won't Have | Previsto |

RF-40 depende de RF-11. RF-41 depende de RF-40.

## Lo que sí existe, y lo que no hay que confundir con esto

La tabla `tipo_equipo` tiene las columnas `b_verificable` y `n_tipo_verificacion`, modeladas en el agregado `EquipmentType` y en el enumerado `VerificationMode`. Eso declara **si** un tipo de equipo se verifica metrológicamente y **con qué modalidad** de las tres previstas — nada más. No es un registro de calibración: no lleva fechas, no vence, no dispara nada.

Las **verificaciones técnicas y los datos metrológicos** que sí llevarían fechas de calibración son la segunda tanda pendiente del módulo `equipment` (ver `SecondBrain/wiki/malphasos/checklist-reutilizacion.md`, sección 4), y son la pieza sobre la que este par de requisitos podría construirse el día que se aborden.

Ni los equipos patrón, ni las fechas de última calibración o de frecuencia, ni el estado "Alerta de Calibración", ni el certificado que reiniciaría el umbral: nada de eso está modelado. Tampoco hay tareas programadas en el backend (`@EnableScheduling` no aparece en el código) que pudieran comparar fechas diariamente, aunque las fechas existieran.

## Notas relacionadas

[[glosario-dominio]] (verificación metrológica) · [[rf-inventario]] · [[estado-de-implementacion]] · [[requisitos-no-funcionales]] (RNF-19, frecuencia de monitoreo)
