---
name: rf-alertas-calibracion
description: RF-40 y RF-41, Alertas y calibracion (ERS 3.2.7). Won't Have, previstos. Los equipos patron y su vencimiento existen desde el 2026-10-14; RF-40 cumple uno de sus tres criterios
tags: [requisitos, rf, calibracion, metrologia]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.2.7"
estado: vigente
updated: 2026-10-16
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

> **Corregido el 16 de octubre de 2026.** Aquí decía que faltaba «exactamente una columna de
> vencimiento» y que ni los equipos patrón ni el certificado estaban modelados. **Las dos cosas existen
> desde el 14 de octubre**: el catálogo de equipos patrón —migración `V25`— guarda de cada patrón su
> serie, su **certificado** y la **fecha de vencimiento** de ese certificado, y cada reporte con lecturas
> **copia** el patrón que se usó tal como estaba ese día —migración `V27`—. Lo que sigue sin existir es
> **quien vigile la fecha**.
>
> **Matizado el mismo día, por la noche**: desde `V30` un reporte no copia *un* patrón sino **varios por
> cada verificación medida**, y desde `V29` cada patrón guarda también su **fecha de calibración** y la
> empresa que lo calibró. No cambia el estado de RF-40 ni el de RF-41.

## Cómo queda cada criterio de aceptación de RF-40

| Criterio | Estado |
|---|---|
| Comparar a diario la fecha contra hoy | **No existe.** El backend no tiene tareas programadas (`@EnableScheduling` no aparece en el código). Además, la ERS habla de «fecha de última calibración» y «frecuencia»; el sistema guarda directamente la **fecha de vencimiento** del certificado, que es lo que esas dos dan |
| Indicador visual en el listado | **Cumplido.** Catálogo › Patrones marca cada patrón «Vencido», «Por vencer» —30 días o menos— o «Vigente», distinguidos por peso tipográfico, y los ordena por lo que vence antes |
| Advertir al asociar un patrón próximo a vencer, o vencido, a un reporte | **A medias.** Uno vencido no se ofrece al elegir y **no deja cerrar** el reporte; uno próximo a vencer no avisa |

**RF-40 sigue previsto**: el criterio de este proyecto cuenta lo que se satisface por completo. **RF-41
también**: no hay alerta, ni estado «Alerta de Calibración», ni nada que reinicie el umbral al renovar
el certificado —renovarlo es editar el patrón—.

## Notas relacionadas

[[glosario-dominio]] (verificación metrológica) · [[rf-inventario]] · [[estado-de-implementacion]] · [[requisitos-no-funcionales]] (RNF-19, frecuencia de monitoreo)
