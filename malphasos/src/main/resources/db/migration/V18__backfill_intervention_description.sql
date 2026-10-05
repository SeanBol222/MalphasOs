-- V18: rellena la descripcion y el responsable de las lineas del historial anteriores a V17.
--
-- Como hizo V13 con las lineas: sin esto, todo el historial anterior a V17 saldria con la raya.
-- Limitacion, la misma de V13: copia lo que el reporte y la orden dicen HOY, porque lo del momento del
-- cierre no se guardo en ninguna parte. Un reporte cerrado no se edita, de modo que
-- los procedimientos son los del cierre; el ingeniero de la orden si pudo cambiar despues.
--
-- Idempotente: volver a ejecutarla escribe lo mismo.
UPDATE intervencion i
SET t_descripcion = NULLIF(btrim(r.t_procedimientos), ''),
    n_responsable = NULLIF(btrim(concat_ws(' ',
                        NULLIF(btrim(p.n_primer_nombre), ''),
                        NULLIF(btrim(p.n_segundo_nombre), ''),
                        NULLIF(btrim(p.n_primer_apellido), ''),
                        NULLIF(btrim(p.n_segundo_apellido), ''))), '')
FROM reporte_servicio r
         JOIN orden_trabajo o ON o.k_id_orden_trabajo = r.k_id_orden_trabajo
         LEFT JOIN persona p ON p.k_identificador = o.k_identificador
WHERE r.k_id_reporte_servicio = i.k_id_reporte_servicio;
