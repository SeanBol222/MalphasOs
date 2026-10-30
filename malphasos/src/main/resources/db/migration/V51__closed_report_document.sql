-- V51: el reporte cerrado vive en su documento, sin copias del catálogo (2026-10-28).
--
-- Decidido por el usuario con el análisis del esquema (Documentation/DatabaseDesign/analisis-2026-10-27.md,
-- «El documento congelado sustituye a las copias», B1, B2 y B3). La regla: UNA SOLA FUENTE PARA CADA
-- MOMENTO. Mientras un reporte es borrador, el texto de sus pasos, la unidad y la tolerancia de sus
-- verificaciones y la ficha de sus patrones se leen en vivo del catálogo; al cerrarse, de su documento. Las
-- filas del reporte se quedan con lo que es suyo: qué paso se marcó y con qué nota, qué se midió, qué
-- patrón se eligió.
--
-- Hasta aquí había dos fuentes a la vez: el documento de V37 y las copias que el reporte hacía en sus filas
-- —el patrón desde V27 y V30, la unidad desde V10 y la tolerancia desde V27, el texto de los pasos desde
-- V22—. Salen las copias:
--
--   B1 patron_verificacion_reporte: las 11 columnas de la ficha. Se queda k_id_equipo_patron, para poder
--      contestar con un join «¿en qué reportes se usó este patrón?» si un certificado resulta malo.
--   B2 dato_verificacion: n_unidad y d_tolerancia.
--   B3 paso_reporte: n_nombre, t_descripcion, t_indicacion y i_orden. i_orden también era copia: la
--      prioridad del protocolo en el tipo el día que se abrió el reporte. Lo que de verdad importa es que un
--      reporte no repita protocolo, y eso pasa a declararlo UQ_paso_reporte_protocolo.
--
-- LO QUE SE RELLENA, en SQL y dentro de esta misma migración, porque Flyway aplica todas las pendientes
-- antes de que arranque la aplicación y para entonces las columnas ya no existen: los documentos de los
-- reportes cerrados pasan a la versión 2 LEYENDO LAS PROPIAS COPIAS, que son la verdad de aquel día.
--
-- EL DOCUMENTO TIENE QUE SER EL MISMO QUE COMPONE JAVA (ReportDocumentComposer), clave por clave: lo lee el
-- mismo código. Una prueba aplica esta migración y lo compara. Los patrones van por verificación y por
-- patrón comparando sus identificadores como texto, que es como los ordena Java.

-- 1. Un reporte cerrado sin documento no tiene dónde guardar sus copias. Lo congelaba la aplicación al
--    arrancar desde V37, y si alguno queda es que esta base no ha arrancado con la versión que lo hace: se
--    detiene diciéndolo, en vez de borrar lo que solo está en las copias.
DO
$$
    DECLARE
        sin_documento integer;
    BEGIN
        SELECT count(*) INTO sin_documento
        FROM reporte_servicio
        WHERE t_estado_reporte = 'FINALIZADO' AND t_documento IS NULL;

        IF sin_documento > 0 THEN
            RAISE EXCEPTION 'V51 necesita el documento de todo reporte cerrado, y % no lo tienen. Arranque una vez la '
                'versión anterior, que los congela al arrancar, y vuelva a aplicar esta.', sin_documento;
        END IF;
    END
$$;

-- 2. Los documentos de los cerrados, a la versión 2.
UPDATE reporte_servicio r
SET t_documento = r.t_documento
    || jsonb_build_object(
            'version', 2,
            -- La tolerancia de cada verificación: la que se copió en sus lecturas, que es la que juzgó cada
            -- una; sin lecturas, la del tipo, que es la que habría leído el cierre.
            'verificaciones', (SELECT coalesce(jsonb_agg(
                                                       v.valor || jsonb_build_object('tolerancia', coalesce(
                                                               (SELECT d.d_tolerancia
                                                                FROM dato_verificacion d
                                                                WHERE d.k_id_reporte_servicio = r.k_id_reporte_servicio
                                                                  AND d.k_id_verificacion = (v.valor ->> 'id')::uuid
                                                                  AND d.b_estado_activo
                                                                  AND d.d_tolerancia IS NOT NULL
                                                                LIMIT 1),
                                                               (SELECT t.d_tolerancia
                                                                FROM verificacion_tipo_equipo t
                                                                WHERE t.k_id_verificacion = (v.valor ->> 'id')::uuid)))
                                                       ORDER BY v.posicion),
                                               '[]'::jsonb)
                               FROM jsonb_array_elements(r.t_documento -> 'verificaciones')
                                        WITH ORDINALITY AS v(valor, posicion)),
            'pasos', (SELECT coalesce(jsonb_agg(
                                              jsonb_build_object(
                                                      'idProtocolo', p.k_id_protocolo,
                                                      'orden', p.i_orden,
                                                      'nombre', p.n_nombre,
                                                      'descripcion', p.t_descripcion,
                                                      'indicacion', p.t_indicacion)
                                              ORDER BY p.i_orden),
                                      '[]'::jsonb)
                      FROM paso_reporte p
                      WHERE p.k_id_reporte_servicio = r.k_id_reporte_servicio
                        AND p.b_estado_activo),
            'patrones', (SELECT coalesce(jsonb_agg(
                                                 jsonb_build_object(
                                                         'idVerificacion', u.k_id_verificacion,
                                                         'ficha', jsonb_build_object(
                                                                 'id', u.k_id_equipo_patron,
                                                                 'nombre', u.n_nombre,
                                                                 'marca', u.n_marca,
                                                                 'serie', u.k_serie,
                                                                 'certificado', u.n_certificado,
                                                                 'vencimiento', u.f_vencimiento::text,
                                                                 'modelo', u.n_modelo,
                                                                 'codigoInterno', u.k_codigo_interno,
                                                                 'rango', u.t_rango,
                                                                 'resolucion', u.t_resolucion,
                                                                 'fechaCalibracion', u.f_calibracion::text,
                                                                 'empresaCalibradora', u.n_empresa_calibradora))
                                                 ORDER BY u.k_id_verificacion::text, u.k_id_equipo_patron::text),
                                         '[]'::jsonb)
                         FROM patron_verificacion_reporte u
                         WHERE u.k_id_reporte_servicio = r.k_id_reporte_servicio
                           AND u.b_estado_activo))
WHERE r.t_estado_reporte = 'FINALIZADO';

-- 3. Fuera las copias.
ALTER TABLE patron_verificacion_reporte
    DROP CONSTRAINT "CHK_patron_verificacion_reporte_calibracion",
    DROP COLUMN n_nombre,
    DROP COLUMN n_marca,
    DROP COLUMN k_serie,
    DROP COLUMN n_certificado,
    DROP COLUMN f_vencimiento,
    DROP COLUMN n_modelo,
    DROP COLUMN k_codigo_interno,
    DROP COLUMN t_rango,
    DROP COLUMN t_resolucion,
    DROP COLUMN f_calibracion,
    DROP COLUMN n_empresa_calibradora;

ALTER TABLE dato_verificacion
    DROP CONSTRAINT "CHK_dato_verificacion_tolerancia",
    DROP CONSTRAINT "CHK_dato_verificacion_unidad",
    DROP COLUMN n_unidad,
    DROP COLUMN d_tolerancia;

ALTER TABLE paso_reporte
    DROP CONSTRAINT "UQ_paso_reporte_orden",
    DROP CONSTRAINT "CHK_paso_reporte_orden",
    DROP COLUMN i_orden,
    DROP COLUMN n_nombre,
    DROP COLUMN t_descripcion,
    DROP COLUMN t_indicacion;

-- Un reporte no repite protocolo, como un tipo tampoco: la llave de protocolo_tipo_equipo es (tipo,
-- protocolo). Parcial, como el resto de las del reporte: un paso retirado no ocupa el sitio.
CREATE UNIQUE INDEX "UQ_paso_reporte_protocolo"
    ON paso_reporte (k_id_reporte_servicio, k_id_protocolo)
    WHERE b_estado_activo;

COMMENT ON TABLE paso_reporte IS
    'Un paso del protocolo en un reporte: de qué protocolo, si se hizo y su nota. Su texto y su posición los '
    'dice el catálogo en un borrador y el documento en uno cerrado (V51).';
