-- V59: lo que el reporte dice vive en un solo documento, t_contenido (2026-11-19).
--
-- Decidido por el usuario al revisar el esquema completo: el reporte es un documento que se lee y se escribe
-- entero, y lo tenían que reconstruir cuatro tablas y quince columnas que nadie consulta campo a campo. Con
-- esta migración salen dato_verificacion, patron_verificacion_reporte, paso_reporte y accesorio_reporte, y
-- reporte_servicio pasa de 25 columnas a 13.
--
-- QUÉ SE QUEDA COMO COLUMNA, y por qué cada una. Solo lo que la base usa:
--   k_id_reporte_servicio   su prefijo es el número del reporte (UQ_reporte_servicio_numero, V28)
--   k_id_orden_trabajo      foránea, y con el equipo, un solo reporte activo por equipo de la orden (V9)
--   k_id_equipo_cliente     foránea, y el índice con que la hoja de vida pide el último cierre (V52)
--   t_estado_reporte        CHK_reporte_servicio_cierre y CHK_reporte_servicio_documento
--   t_resultado             el listado de equipos filtra en SQL los FUERA_DE_SERVICIO
--   f_finalizado            el mismo índice de V52 y el CHECK del cierre
--   b_estado_activo         el índice único de V9
--   k_id_recibe             foránea a persona, en cascada desde V56
--   k_huella_firma_recibe   foránea a imagen_documento: por ella el almacén sabe qué firma está en uso
--   f_firma_recibe          va con la huella: CHK_reporte_servicio_firma_recibe
--   t_documento             el informe congelado al cerrar (V37, V51): no cambia
--   b_documento_tardio      va con el documento: CHK_reporte_servicio_documento
--   t_contenido             esto
--
-- LO QUE GUARDA t_contenido, con su versión dentro como t_documento:
--
--   { "version": 1,
--     "falla", "diagnostico", "causa", "repuestos", "procedimientos", "observaciones":  texto o null,
--     "ingreso":  { "fisico": {estado, observacion} | null, "operativo": {estado, observacion} | null },
--     "recibe":   { nombre, cedula, cargo } | null,       -- quien recibe sin ser persona registrada
--     "pasos":    [ { protocolo, estado, nota } ],
--     "accesorios":     [ { nombre, conforme, observacion } ],           -- en su orden: el del arreglo
--     "verificaciones": [ { id, patrones: [ { id, desdeOrden } ],
--                               lecturas: [ { punto, secuencia, patron, equipo } ] } ] }
--
-- Lo que el reporte dice y nada más: el nombre de un paso, la unidad, la tolerancia, el valor de un punto y la
-- ficha de un patrón siguen leyéndose del catálogo mientras es borrador y de t_documento al cerrarse. La misma
-- regla de V51, «una sola fuente para cada momento», sin ningún dato dos veces.
--
-- SOLO LO VIGENTE. Las filas retiradas —una lectura corregida, un accesorio quitado— no pasan: en el documento
-- quitar algo es quitarlo. Decidido por el usuario, y es una excepción consciente a «aquí nada se borra»: lo
-- retirado lo era dentro de un borrador, el informe nunca lo imprimió, y un reporte cerrado no se edita, se
-- sustituye, y el sustituido se queda entero. En desarrollo son 58 lecturas, ningún patrón, paso ni accesorio.
--
-- LO QUE LA BASE DEJA DE GARANTIZAR y pasa al agregado ServiceReport, que ya valida casi todo:
--   que una lectura esté en un punto de su verificación (la foránea compuesta de V10), que la verificación,
--   el patrón y el protocolo existan, que no se repita una lectura (punto, secuencia) ni un paso por protocolo,
--   que los textos no estén en blanco y los valores del ingreso sean los suyos (siete CHECK de V9 y V31).
-- Dos CHECK que mezclaban columnas que se quedan con lo que se va se rehacen leyendo el documento, porque un
-- CHECK puede: quien recibe es una persona registrada o un nombre escrito, no las dos cosas; y una firma lleva
-- su fecha y a alguien que la haya dado.
--
-- «¿EN QUÉ REPORTES SE USÓ ESTE PATRÓN?», que V51 quiso poder contestar si un certificado resulta malo, se
-- contesta con @> sobre el índice GIN de abajo:
--   t_contenido @> '{"verificaciones": [{"patrones": [{"id": "<patrón>"}]}]}'

-- 1. La columna, vacía pero con su forma: un reporte nuevo nace así.
ALTER TABLE reporte_servicio
    ADD COLUMN t_contenido jsonb NOT NULL DEFAULT jsonb_build_object(
            'version', 1,
            'falla', NULL, 'diagnostico', NULL, 'causa', NULL, 'repuestos', NULL,
            'procedimientos', NULL, 'observaciones', NULL,
            'ingreso', jsonb_build_object('fisico', NULL, 'operativo', NULL),
            'recibe', NULL,
            'pasos', '[]'::jsonb, 'accesorios', '[]'::jsonb, 'verificaciones', '[]'::jsonb);

-- 2. Cada reporte, retirados incluidos, con lo vigente de sus filas. Los arreglos van en un orden fijo —los
--    identificadores comparados como texto, los accesorios por su i_orden— para que dos ejecuciones den el
--    mismo documento.
UPDATE reporte_servicio r
SET t_contenido = jsonb_build_object(
        'version', 1,
        'falla', r.t_falla_reportada,
        'diagnostico', r.t_diagnostico,
        'causa', r.t_causa,
        'repuestos', r.t_repuestos,
        'procedimientos', r.t_procedimientos,
        'observaciones', r.t_observaciones,
        'ingreso', jsonb_build_object(
                'fisico', CASE WHEN r.t_ingreso_fisico IS NULL THEN NULL
                               ELSE jsonb_build_object('estado', r.t_ingreso_fisico,
                                                       'observacion', r.t_ingreso_fisico_observacion) END,
                'operativo', CASE WHEN r.t_ingreso_operativo IS NULL THEN NULL
                                  ELSE jsonb_build_object('estado', r.t_ingreso_operativo,
                                                          'observacion', r.t_ingreso_operativo_observacion) END),
        'recibe', CASE WHEN r.n_recibe_nombre IS NULL THEN NULL
                       ELSE jsonb_build_object('nombre', r.n_recibe_nombre, 'cedula', r.k_recibe_cedula,
                                               'cargo', r.n_recibe_cargo) END,
        'pasos', coalesce((SELECT jsonb_agg(jsonb_build_object('protocolo', p.k_id_protocolo,
                                                               'estado', p.t_estado_paso,
                                                               'nota', p.t_nota)
                                            ORDER BY p.k_id_protocolo::text)
                           FROM paso_reporte p
                           WHERE p.k_id_reporte_servicio = r.k_id_reporte_servicio
                             AND p.b_estado_activo), '[]'::jsonb),
        'accesorios', coalesce((SELECT jsonb_agg(jsonb_build_object('nombre', a.n_nombre,
                                                                    'conforme', a.b_conforme,
                                                                    'observacion', a.t_observacion)
                                                 ORDER BY a.i_orden, a.k_id_accesorio_reporte::text)
                                FROM accesorio_reporte a
                                WHERE a.k_id_reporte_servicio = r.k_id_reporte_servicio
                                  AND a.b_estado_activo), '[]'::jsonb),
        'verificaciones', coalesce((
            SELECT jsonb_agg(jsonb_build_object(
                    'id', v.k_id_verificacion,
                    'patrones', coalesce((SELECT jsonb_agg(jsonb_build_object('id', s.k_id_equipo_patron,
                                                                              'desdeOrden', false)
                                                           ORDER BY s.k_id_equipo_patron::text)
                                          FROM patron_verificacion_reporte s
                                          WHERE s.k_id_reporte_servicio = r.k_id_reporte_servicio
                                            AND s.k_id_verificacion = v.k_id_verificacion
                                            AND s.b_estado_activo), '[]'::jsonb),
                    'lecturas', coalesce((SELECT jsonb_agg(jsonb_build_object('punto', d.k_id_punto_verificacion,
                                                                              'secuencia', d.i_secuencia,
                                                                              'patron', d.d_valor_patron,
                                                                              'equipo', d.d_valor_equipo)
                                                           ORDER BY d.k_id_punto_verificacion::text NULLS FIRST,
                                                                    d.i_secuencia)
                                          FROM dato_verificacion d
                                          WHERE d.k_id_reporte_servicio = r.k_id_reporte_servicio
                                            AND d.k_id_verificacion = v.k_id_verificacion
                                            AND d.b_estado_activo), '[]'::jsonb))
                             ORDER BY v.k_id_verificacion::text)
            FROM (SELECT d.k_id_verificacion
                  FROM dato_verificacion d
                  WHERE d.k_id_reporte_servicio = r.k_id_reporte_servicio AND d.b_estado_activo
                  UNION
                  SELECT s.k_id_verificacion
                  FROM patron_verificacion_reporte s
                  WHERE s.k_id_reporte_servicio = r.k_id_reporte_servicio AND s.b_estado_activo) v),
            '[]'::jsonb));

-- 3. Los dos CHECK que cruzan columna y documento. Se quitan antes que las columnas, que se los llevarían
--    enteros, y se vuelven a poner leyendo el documento.
ALTER TABLE reporte_servicio
    DROP CONSTRAINT "CHK_reporte_servicio_recibe",
    DROP CONSTRAINT "CHK_reporte_servicio_firma_recibe";

-- 4. Lo que se mudó.
ALTER TABLE reporte_servicio
    DROP COLUMN t_falla_reportada,
    DROP COLUMN t_diagnostico,
    DROP COLUMN t_causa,
    DROP COLUMN t_repuestos,
    DROP COLUMN t_procedimientos,
    DROP COLUMN t_observaciones,
    DROP COLUMN t_ingreso_fisico,
    DROP COLUMN t_ingreso_fisico_observacion,
    DROP COLUMN t_ingreso_operativo,
    DROP COLUMN t_ingreso_operativo_observacion,
    DROP COLUMN n_recibe_nombre,
    DROP COLUMN k_recibe_cedula,
    DROP COLUMN n_recibe_cargo;

DROP TABLE dato_verificacion;
DROP TABLE patron_verificacion_reporte;
DROP TABLE paso_reporte;
DROP TABLE accesorio_reporte;

-- 5. Lo que la base sí puede seguir mirando del documento: su forma de arriba, y los dos CHECK del paso 3.
--    Con IS NOT DISTINCT FROM y no con =: jsonb_typeof de una clave que falta es NULL, y un CHECK que da NULL
--    pasa. Con = un documento sin «pasos» entraba; lo encontró el ensayo de esta migración.
ALTER TABLE reporte_servicio
    ADD CONSTRAINT "CHK_reporte_servicio_contenido"
        CHECK (jsonb_typeof(t_contenido) = 'object'
            AND jsonb_typeof(t_contenido -> 'version') IS NOT DISTINCT FROM 'number'
            AND jsonb_typeof(t_contenido -> 'pasos') IS NOT DISTINCT FROM 'array'
            AND jsonb_typeof(t_contenido -> 'accesorios') IS NOT DISTINCT FROM 'array'
            AND jsonb_typeof(t_contenido -> 'verificaciones') IS NOT DISTINCT FROM 'array'),
    ADD CONSTRAINT "CHK_reporte_servicio_recibe"
        CHECK (k_id_recibe IS NULL OR jsonb_typeof(t_contenido -> 'recibe') IS DISTINCT FROM 'object'),
    ADD CONSTRAINT "CHK_reporte_servicio_firma_recibe"
        CHECK (CASE
                   WHEN k_huella_firma_recibe IS NULL THEN f_firma_recibe IS NULL
                   ELSE f_firma_recibe IS NOT NULL
                       AND (k_id_recibe IS NOT NULL OR jsonb_typeof(t_contenido -> 'recibe') IS NOT DISTINCT FROM 'object')
               END);

CREATE INDEX "IX_reporte_servicio_contenido"
    ON reporte_servicio USING gin (t_contenido jsonb_path_ops);

COMMENT ON COLUMN reporte_servicio.t_contenido IS
    'Lo que el reporte dice: textos, ingreso, quien recibe sin cuenta, pasos, accesorios y, por verificación, '
        'sus patrones y sus lecturas. Solo lo vigente. Lo que es del catálogo no va aquí: va en t_documento al cerrar.';
