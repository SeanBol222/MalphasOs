---
name: estado-de-la-ers-caducado
description: Las anotaciones «Estado en la implementacion actual» de la ERS afirman seis cosas que el codigo ya desmiente, y dos requisitos se cuentan como implementados contra su propio texto
tags: [defectos, requisitos, trazabilidad]
fuente: Documentation/IEEE830/IEEE830.tex
estado: defecto-conocido
updated: 2026-10-04
---

# El estado que la ERS declara de sí misma está caducado

La ERS no se limita a enunciar requisitos: **cada uno lleva un bloque «Estado en la implementación
actual»**, treinta y tres en total, añadidos por este proyecto para que el documento diga qué hay
construido. Son la parte del documento que **puede caducar**, y ha caducado.

Encontrado el **2026-10-04**, contrastando el `.tex` contra el código y contra la base de datos en
marcha. Es la primera vez que esta wiki compara la ERS con el sistema en esa dirección: hasta ahora se
comprobaba que no se documentara como existente algo que no estaba, y **esto es lo contrario** — la ERS
documenta como inexistente algo que ya existe.

## Seis afirmaciones que el código desmiente

| Línea | Requisito | Dice | Es |
|---|---|---|---|
| 584 | (alcance) | «las cinco migraciones del esquema» | **once** |
| 683 | RF-01 | «las cinco migraciones del esquema» | **once** |
| 771 | RF-05 | «las cinco migraciones» | **once** |
| 1138 | RF-26 | el oyente irá ahí «**cuando el reporte exista**» | el reporte existe desde el **2026-09-27** |
| 1160 | RF-27 | «las cinco migraciones del esquema» | **once** |
| 1256 | RF-40 | «las cinco migraciones» | **once** |

Las cinco de «cinco migraciones» se escribieron cuando el esquema iba por `V5`. Hoy va por `V11`, y la
afirmación que sostienen —que no hay tabla de historial, ni de inventario, ni de calibración— **sigue
siendo cierta**: lo que ha caducado es el número con el que se justifica, no la conclusión. Conviene
decirlo así para no exagerar el defecto.

**La de RF-26 es distinta y sí cambia el sentido.** Dice que el oyente del cierre de un reporte irá en
`equipment/infrastructure/input/listeners/` «cuando el reporte exista». El reporte existe, con sus
cinco eventos de dominio, desde el 2026-09-27. Lo que falta no es el reporte: es el oyente. Un lector
del documento concluiría que RF-26 está bloqueado por una pieza que ya está construida.

## Y dos requisitos contados como implementados contra el texto de la propia ERS

**RF-22, «Crear hoja de vida»**, figura como **Implementado** en [[estado-de-implementacion]] y en
[[rf-hojas-vida]]. Su segundo criterio de aceptación exige que «el formulario incluye todas las
secciones requeridas (identificación, técnica, fabricante, **servicio técnico**)», y el bloque de estado
de la propia ERS dice, literalmente:

> La sección de *servicio técnico* es la única de las cuatro que enumera el criterio de aceptación que
> falta, y **falta entera**: presupone el historial de intervenciones de RF-26 y RF-27, ambos previstos.

Además el primer criterio pide **un formulario**, y no hay ninguno: los datos están repartidos por la
cadena del catálogo, con un recurso por eslabón —`equipo_cliente`, `equipment-types`, `manufacturers`,
`brands`, `models`—. El propio bloque de estado lo dice: «no existe una entidad llamada hoja de vida».

**RF-24, «Modificar/eliminar hoja de vida»**, declara `RF-22` como su única dependencia y figura
también como Implementado. Su criterio es «existe opción de edición **de la hoja de vida**». Hay cinco
`PATCH`, uno por eslabón; no hay una hoja de vida que editar.

### Por qué esto importa y no es una discusión de palabras

**Es exactamente la misma situación que RF-04, y se está juzgando al revés.** RF-04 pide elegir áreas
antes de ver sus equipos; la pantalla muestra todas a la vez, llega al mismo sitio con un paso menos, y
está marcado como **desviación consciente** y no como implementado, «para poder discutirlo». RF-22 y
RF-24 están satisfechos **en sustancia y no en la forma que el requisito describe**, igual que RF-04, y
cuentan como implementados.

Y el criterio que este proyecto se fijó por escrito en [[estado-de-implementacion]] es más estricto
todavía: «cuenta como implementado **lo que el backend satisface por completo**; dar por hecho lo demás
inflaría la cifra y haría desaparecer de la cuenta trabajo que no se ha hecho». Tres de cuatro secciones
no es por completo.

**Consecuencia sobre la cifra de portada**: si se aplica el criterio escrito, el marcador no es 17 de 31
sino **15 de 31 con tres desviaciones** —RF-04, RF-22 y RF-24—. **No se ha cambiado**, porque cómo contar
estos dos es la misma decisión que quedó abierta con RF-04 y le corresponde a quien lleva el proyecto.
Queda aquí registrado para que se tome una vez y valga para los tres.

## Lo que sí resultó correcto al comprobarlo

Conviene registrarlo, porque es la mayor parte:

- **El 31 del denominador es exacto**, y por dos vías independientes que coinciden: 31 declaraciones
  `\textbf{RF-xx ...}` y 31 filas de tabla, con la **misma** lista.
- **RF-27 está bien marcado como previsto.** Existe una pantalla de historial de un equipo desde el
  2026-09-28, y podría parecer que lo cubre; no lo cubre, porque RF-27 declara `RF-26` como dependencia
  y RF-26 pide el registro **automático** al cerrar un reporte, que no existe. El javadoc de
  `historial-equipo.ts` ya hace esa distinción por su cuenta.
- **Los 18 códigos `RF-` que la ERS cita sin declarar** —y que la priorización MoSCoW prioriza, con
  `RF-10` entre los *Must Have*— ya estaban registrados en [[priorizacion-moscow]]. Se volvieron a
  encontrar por una vía distinta y coincidieron.

## Notas relacionadas

[[estado-de-implementacion]] · [[rf-hojas-vida]] · [[priorizacion-moscow]] · [[rf-alertas-calibracion]] · [[regla-implementado-vs-previsto]]
