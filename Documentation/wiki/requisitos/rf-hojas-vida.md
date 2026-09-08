---
name: rf-hojas-vida
description: RF-22, 24, 26, 27, Hojas de Vida de los Equipos (ERS 3.2.5). Dos implementados sin que exista una entidad "hoja de vida", dos previstos
tags: [requisitos, rf, hojas-de-vida, equipos]
fuente: "Documentation/IEEE830/IEEE830.tex, apartado 3.2.5"
estado: vigente
updated: 2026-09-05
---

# 3.2.5. Hojas de Vida de los Equipos (RF-22, 24, 26, 27)

| Código | Requisito | MoSCoW | Estado |
|---|---|---|---|
| RF-22 | Crear hoja de vida | Could Have | **Implementado** |
| RF-24 | Modificar/eliminar hoja de vida | Could Have | **Implementado** |
| RF-26 | Registro automático de intervenciones | Could Have | Previsto |
| RF-27 | Historial de intervenciones | Could Have | Previsto |

## No existe una entidad llamada "hoja de vida"

Es la precisión más importante de esta categoría: los datos que RF-22 pide están repartidos por la cadena del catálogo de equipos, y **cada eslabón tiene su propio recurso REST**:

| Sección de la hoja de vida (según el criterio de aceptación) | Dónde vive |
|---|---|
| Identificación de la unidad (número de serie, número de inventario del cliente, fecha y valor de compra, área de servicio) | Tabla `equipo_cliente`, agregado `ClientEquipment`. `POST /v1/api/service-areas/{idAreaServicio}/equipments` |
| Características técnicas (definición técnica, recomendaciones de cuidado, tecnología, voltaje, amperaje, valor unitario de mantenimiento) | Tipo de equipo. `/v1/api/equipment-types` |
| Fabricante y país de origen | `/v1/api/manufacturers` |
| Marca | `/v1/api/brands` |
| Modelo, incluido su registro INVIMA | `/v1/api/models` |
| Servicio técnico (historial de intervenciones) | **No existe.** Presupone RF-26 y RF-27, ambos previstos |

Ver [[glosario-dominio]] para el término "equipo" desdoblado en tipo/marca/modelo/unidad, y [[correspondencia-terminologica]] para por qué "hoja de vida" no tiene equivalente de código.

## RF-24: modificación, con dos inmutabilidades deliberadas

`PATCH /v1/api/client-equipments/{id}` modifica número de inventario, fecha y valor de compra. El traslado de área es una operación aparte, `PATCH /v1/api/client-equipments/{id}/service-area/{idAreaServicio}`, porque cambiar de área cambia quién responde por el equipo. La baja es `DELETE /v1/api/client-equipments/{id}` (204, desactiva sin borrar).

**Dos datos no se pueden cambiar, y es intencional**: el número de serie y el modelo de una unidad son inmutables en el agregado `ClientEquipment` — una unidad no se convierte en otra cosa —; y la asociación entre una marca y un tipo de equipo no admite modificación en absoluto, fijada por una prueba, porque cambiarla volvería mentira todos los modelos que cuelgan de ella.

## RF-26 y RF-27: previstos, con el mecanismo de soporte ya construido

`equipment/infrastructure/input/listeners/` está creado y vacío — es donde iría el oyente que reaccione al cierre de un reporte. El despachador de eventos de dominio que compartirían todos los módulos (`shared/domain/events`) **sí está construido y en uso** por los agregados existentes; falta el evento del reporte que no existe todavía, no el mecanismo de despacho.

## Dependencias declaradas

RF-22 depende de RF-08, RF-49, RF-50. RF-24 depende de RF-22. RF-26 depende de RF-09, RF-15, RF-22. RF-27 depende de RF-26.

## Notas relacionadas

[[glosario-dominio]] · [[correspondencia-terminologica]] · [[rf-reportes-mantenimiento]] · [[estado-de-implementacion]]
