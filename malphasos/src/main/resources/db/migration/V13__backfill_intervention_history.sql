-- V13: rellena el historial de intervenciones con los reportes que ya estaban cerrados antes de V12.
--
-- EL HUECO QUE CIERRA. V12 creo la tabla y desde entonces un oyente anota cada reporte al cerrarse.
-- Pero un reporte que ya estaba FINALIZADO antes de V12 no tiene su linea, y nada lo avisa: la hoja de
-- vida de ese equipo diria «sin intervenciones» aunque tenga mantenimientos hechos. Se encontro al
-- arrancar V12 sobre la base de desarrollo, no leyendo el codigo.
--
-- POR QUE NO SE CORRIGIO EN V12. Porque V12 ya estaba aplicada en la base de desarrollo: cambiarla
-- rompe la suma de comprobacion de Flyway en el siguiente arranque. Una migracion aplicada no se toca.
--
-- QUE ESCRIBE. Exactamente lo que el oyente habria escrito si hubiera existido cuando cada reporte se
-- cerro, y nada mas:
--   - una linea por reporte FINALIZADO, este hoy activo o retirado: la decision tomada para V12 es que
--     la intervencion sobrevive a su reporte, y el relleno no puede contradecirla;
--   - la fecha de cierre del reporte, no la programada de la orden;
--   - el tipo de servicio de la orden y el resultado del reporte, como copia congelada.
--
-- Y UNA LIMITACION QUE CONVIENE DECIR. El oyente copia el tipo de servicio de la orden en el momento del
-- cierre; aqui se copia el que la orden tiene HOY. Si alguna orden cambio de tipo despues de cerrarse
-- uno de sus reportes, la linea rellenada lleva el tipo nuevo. Es el mejor dato que queda: el viejo no
-- se guardo en ninguna parte.
--
-- ES IDEMPOTENTE. ON CONFLICT sobre la restriccion de una linea por reporte hace que ejecutarla dos
-- veces, o sobre una base donde el oyente ya anoto algunos reportes, no duplique nada.

INSERT INTO intervencion (k_id_intervencion, k_id_equipo_cliente, k_id_reporte_servicio,
                          f_fecha_servicio, t_tipo_servicio, t_resultado)
SELECT gen_random_uuid(),
       r.k_id_equipo_cliente,
       r.k_id_reporte_servicio,
       r.t_finalizado,
       o.t_tipo_servicio,
       r.t_resultado
FROM reporte_servicio r
         JOIN orden_trabajo o ON o.k_id_orden_trabajo = r.k_id_orden_trabajo
WHERE r.t_estado_reporte = 'FINALIZADO'
ON CONFLICT ON CONSTRAINT "UQ_intervencion_por_reporte" DO NOTHING;
