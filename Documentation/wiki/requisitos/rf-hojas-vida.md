---
name: rf-hojas-vida
description: RF-22, 24, 26, 27, Hojas de Vida de los Equipos (ERS 3.2.5). Los cuatro implementados desde el 2026-10-04, con la hoja de vida como compilado de solo lectura y no como entidad
tags: [requisitos, rf, hojas-de-vida, equipos]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.2.5"
estado: vigente
updated: 2026-10-05
---

# 3.2.5. Hojas de Vida de los Equipos (RF-22, 24, 26, 27)

> **Resuelto el 2026-10-04 por la tarde: los cuatro están implementados.** La hoja de vida se
> construyó, y **no como entidad**: es un compilado de la cadena del catálogo y de la organización del
> cliente, con las cuatro secciones que pide RF-22 —identificación, técnica, fabricante y servicio
> técnico—, y existe desde que el equipo se registra con su historial en cero. Solo el historial tiene
> tabla; un oyente lo escribe al cerrarse cada reporte (RF-26) y se lee ordenado con fecha, tipo y
> resultado (RF-27). **RF-24 se satisface siendo de solo lectura**, por decisión del usuario: cada dato
> se corrige donde vive. El detalle técnico, en `SecondBrain/wiki/dominio/hoja-de-vida.md`. Lo que sigue
> es la duda de la mañana, que se conserva porque es lo que motivó construirlo.
>
> **⚠️ Revisado el 2026-10-04: los dos «implementado» de abajo están en duda, y la duda la plantea el
> propio texto de la ERS.** RF-22 exige un formulario con cuatro secciones, y el bloque de estado del
> `.tex` dice que la de **servicio técnico** «falta, y falta entera»; además no hay *un* formulario, sino
> cinco recursos, uno por eslabón del catálogo. RF-24 depende de RF-22 y pide editar «la hoja de vida»,
> que no existe como entidad.
>
> Es la misma situación que **RF-04**, que está marcado como **desviación** y no como implementado. Con
> el criterio que el proyecto se fijó por escrito —«cuenta como implementado lo que el backend satisface
> **por completo**»— estos dos tampoco deberían contar, y el marcador pasaría de 17 a **15 de 31 con
> tres desviaciones**. **No se ha cambiado**: es la misma decisión que quedó abierta con RF-04 y
> conviene tomarla una vez para los tres. Ver [[estado-de-la-ers-caducado]].

| Código | Requisito | MoSCoW | Estado |
|---|---|---|---|
| RF-22 | Crear hoja de vida | Could Have | **Implementado** |
| RF-24 | Modificar/eliminar hoja de vida | Could Have | **Implementado** (solo lectura: ver abajo) |
| RF-26 | Registro automático de intervenciones | Could Have | **Implementado** |
| RF-27 | Historial de intervenciones | Could Have | **Implementado** |

## No existe una entidad llamada "hoja de vida"

> **Corregido el 2026-10-05.** Esta sección y las dos siguientes describían el estado **anterior al
> 2026-10-04**, aunque el resumen de arriba ya estaba al día: el cuerpo no se actualizó con él. Desde
> ese día la hoja de vida **se sirve como un solo recurso**, `GET /v1/api/client-equipments/{id}/life-sheet`,
> con las cuatro secciones; el historial **existe** (tabla `intervencion`, `V12` a `V14`), y el oyente
> que lo escribe **no vive** en `equipment/infrastructure/input/listeners/` sino en `report`, porque
> allí habría creado un ciclo. La tabla de abajo sigue siendo cierta en lo que dice de **dónde vive cada
> dato**. Y un matiz que nadie había anotado: RF-22 pide el **valor unitario de mantenimiento** entre
> las características técnicas y **la hoja de vida no lo trae**; el diseño impreso aprobado el
> 2026-10-05 lo deja fuera a propósito, por ser un precio interno, pendiente de que el usuario lo
> confirme. Ver `SecondBrain/wiki/malphasos/hoja-de-vida-formato-impreso.md`.

Es la precisión más importante de esta categoría: los datos que RF-22 pide están repartidos por la cadena del catálogo de equipos, y **cada eslabón tiene su propio recurso REST**:

| Sección de la hoja de vida (según el criterio de aceptación) | Dónde vive |
|---|---|
| Identificación de la unidad (número de serie, número de inventario del cliente, fecha y valor de compra, área de servicio) | Tabla `equipo_cliente`, agregado `ClientEquipment`. `POST /v1/api/service-areas/{idAreaServicio}/equipments` |
| Características técnicas (definición técnica, recomendaciones de cuidado, tecnología, voltaje, amperaje, valor unitario de mantenimiento) | Tipo de equipo. `/v1/api/equipment-types` |
| Fabricante y país de origen | `/v1/api/manufacturers` |
| Marca | `/v1/api/brands` |
| Modelo, incluido su registro INVIMA | `/v1/api/models` |
| Servicio técnico (historial de intervenciones) | Tabla `intervencion` desde el 2026-10-04. (Decía «**No existe.** Presupone RF-26 y RF-27, ambos previstos»: cierto hasta ese día) |

Ver [[glosario-dominio]] para el término "equipo" desdoblado en tipo/marca/modelo/unidad, y [[correspondencia-terminologica]] para por qué "hoja de vida" no tiene equivalente de código.

## RF-24: modificación, con dos inmutabilidades deliberadas

`PATCH /v1/api/client-equipments/{id}` modifica número de inventario, fecha y valor de compra. El traslado de área es una operación aparte, `PATCH /v1/api/client-equipments/{id}/service-area/{idAreaServicio}`, porque cambiar de área cambia quién responde por el equipo. La baja es `DELETE /v1/api/client-equipments/{id}` (204, desactiva sin borrar).

**Dos datos no se pueden cambiar, y es intencional**: el número de serie y el modelo de una unidad son inmutables en el agregado `ClientEquipment` — una unidad no se convierte en otra cosa —; y la asociación entre una marca y un tipo de equipo no admite modificación en absoluto, fijada por una prueba, porque cambiarla volvería mentira todos los modelos que cuelgan de ella.

## RF-26 y RF-27: previstos, con el mecanismo de soporte ya construido — **histórico, ver la corrección de arriba**

`equipment/infrastructure/input/listeners/` está creado y vacío — es donde iría el oyente que reaccione al cierre de un reporte. El despachador de eventos de dominio que compartirían todos los módulos (`shared/domain/events`) **sí está construido y en uso** por los agregados existentes; falta el evento del reporte que no existe todavía, no el mecanismo de despacho.

## Dependencias declaradas

RF-22 depende de RF-08, RF-49, RF-50. RF-24 depende de RF-22. RF-26 depende de RF-09, RF-15, RF-22. RF-27 depende de RF-26.

## Notas relacionadas

[[glosario-dominio]] · [[correspondencia-terminologica]] · [[rf-reportes-mantenimiento]] · [[estado-de-implementacion]]
