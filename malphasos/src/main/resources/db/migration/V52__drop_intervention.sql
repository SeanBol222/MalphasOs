-- V52: sin intervencion; el historial de la hoja de vida sale de los reportes cerrados (2026-10-28).
--
-- Decidido por el usuario con el análisis del esquema (Documentation/DatabaseDesign/analisis-2026-10-27.md,
-- B5). intervencion copiaba de cada reporte cerrado todo lo que una línea del historial dice —equipo,
-- fecha, tipo de servicio, resultado, qué se hizo y quién—, y desde V51 el reporte cerrado lo guarda todo en
-- sus filas y su documento. Era además el único ciclo dirigido del esquema —k_id_reemplazada_por— y una de
-- sus foráneas redundantes, la del equipo, que ya está en el reporte.
--
-- LA REGLA DE V14 PASA DE DATO A FILTRO. Un sustituto, al cerrarse, reemplazaba en la tabla a la línea del
-- sustituido. Sin tabla, de cada par (orden, equipo) cuenta su último reporte cerrado, esté retirado o no:
-- es el mismo conjunto de líneas que la tabla tenía vigentes, porque cada cierre reemplazaba a los
-- anteriores del par y nada más.
--
-- No se rellena nada: lo que se quita es una copia. Solo hace falta el índice con que el listado de equipos
-- pide el último cierre de cada uno, que era IX_intervencion_equipo_fecha.

DROP TABLE intervencion;

CREATE INDEX "IX_reporte_servicio_equipo_cierre"
    ON reporte_servicio (k_id_equipo_cliente, f_finalizado DESC)
    WHERE t_estado_reporte = 'FINALIZADO';
