---
name: rf-alertas-calibracion
description: RF-40 y RF-41, Alertas y calibracion (ERS 3.2.7). Won't Have, previstos. El umbral de 30 dias no esta implementado
tags: [requisitos, rf, calibracion, metrologia]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.2.7"
estado: vigente
updated: 2026-10-03
---

# 3.2.7. Alertas y calibración (RF-40, RF-41)

| Código | Requisito | MoSCoW | Estado |
|---|---|---|---|
| RF-40 | Identificar equipos patrón próximos a vencer su calibración | Won't Have | Previsto |
| RF-41 | Generar alerta al entrar en el umbral de 30 días previos al vencimiento | Won't Have | Previsto |

RF-40 depende de RF-11. RF-41 depende de RF-40.

## Lo que sí existe, y lo que no hay que confundir con esto

> **Corregido el 3 de octubre de 2026.** Este apartado decía que `tipo_equipo` tiene las columnas
> `b_verificable` y `n_tipo_verificacion`, y que las verificaciones técnicas eran «la segunda tanda
> pendiente». **Las dos afirmaciones son falsas**: las columnas se fueron con la migración `V10` y la
> configuración de verificación está construida desde el 27 de septiembre. Lo que sigue sin existir
> —y es lo que estos dos requisitos necesitan— es **la fecha**.

Un tipo de equipo declara **qué** se le verifica, **en qué unidad**, **con qué modalidad**, **cuántas
lecturas por punto** y **en qué valores**: desde el 3 de octubre de 2026 eso vive en la tabla
`verificacion_tipo_equipo`, una fila por magnitud, con su catálogo de magnitudes y unidades. Y el
**resultado** de verificar existe también: `dato_verificacion`, colgando del reporte de servicio.

**Nada de eso es un registro de calibración.** No lleva fechas, no vence y no dispara nada: dice cómo se
verifica un equipo y qué salió al verificarlo, no cuándo caduca su calibración.

Lo que falta para RF-40 y RF-41 es exactamente **una columna de vencimiento** y quien la vigile. Está
registrado como lo único pendiente de la segunda tanda de `equipment` en
`SecondBrain/wiki/malphasos/hoja-de-ruta-producto.md`.

Ni los equipos patrón, ni las fechas de última calibración o de frecuencia, ni el estado "Alerta de Calibración", ni el certificado que reiniciaría el umbral: nada de eso está modelado. Tampoco hay tareas programadas en el backend (`@EnableScheduling` no aparece en el código) que pudieran comparar fechas diariamente, aunque las fechas existieran.

## Notas relacionadas

[[glosario-dominio]] (verificación metrológica) · [[rf-inventario]] · [[estado-de-implementacion]] · [[requisitos-no-funcionales]] (RNF-19, frecuencia de monitoreo)
