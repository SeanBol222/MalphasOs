-- V49: la orden se congela al ejecutarse (2026-10-28).
--
-- Decidido por el usuario con el análisis del esquema (Documentation/DatabaseDesign/analisis-2026-10-27.md,
-- «El documento congelado sustituye a las copias»). Mientras una orden vive, todo lo que muestra se lee en
-- vivo; al ejecutarla se compone un documento con lo que era ese día —la cabecera, el cliente, la sede con
-- su dirección, el ingeniero y cada equipo con su área y el número de su reporte— y desde ahí la orden se
-- lee de él. Es lo que V37 hizo con el informe de cada reporte, llevado a la orden.
--
-- ES LO QUE PERMITE QUITAR LAS COPIAS. El área de cada equipo en orden_trabajo_equipo (B6) estaba para
-- que una orden ejecutada no cambiara de área si el equipo se trasladaba después: con el documento eso ya
-- no hace falta, y V50 la quita junto con la tabla.
--
-- LAS QUE YA ESTABAN EJECUTADAS se congelan aquí, en SQL, y no al arrancar como hizo V37 con los
-- reportes: la próxima migración quita la tabla de la que sale el área de cada equipo, y Flyway aplica
-- todas las pendientes antes de que arranque la aplicación. Se congelan con los datos de hoy —salvo el
-- área, que es la que se guardó al seleccionar el equipo— y quedan marcadas con b_documento_tardio, como
-- decidió el usuario con los reportes: que se sepa que su copia no es la del día en que se ejecutaron.
-- No llevan fecha de ejecución, porque ninguna tabla la guardaba.
--
-- EL DOCUMENTO TIENE QUE SER EL MISMO QUE COMPONE JAVA (WorkOrderDocumentComposer), clave por clave: lo
-- lee el mismo código. Una prueba compone los dos para la misma orden y los compara. El orden de los
-- equipos es por área, número de hoja de vida, serie e identificador, comparando por bytes (COLLATE "C"),
-- que es como compara Java.
--
-- Y A DIFERENCIA DE V37, el CHECK exige el documento a toda orden ejecutada: aquí nadie espera a un
-- arranque, y una ejecutada sin documento sería una que se lee en vivo sin que nada lo avise.

ALTER TABLE orden_trabajo
    ADD COLUMN t_documento        jsonb   NULL,
    ADD COLUMN b_documento_tardio boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN orden_trabajo.t_documento IS
    'Lo que la orden era al ejecutarse: cabecera, cliente, sede, ingeniero y cada equipo con su área y su reporte (V49).';
COMMENT ON COLUMN orden_trabajo.b_documento_tardio IS
    'El documento se compuso después de ejecutarla, con los datos de ese día: órdenes ejecutadas antes de V49.';

UPDATE orden_trabajo o
SET b_documento_tardio = true,
    t_documento        = jsonb_build_object(
            'version', 1,
            'numero', upper(left(o.k_id_orden_trabajo::text, 6)),
            'tipoServicio', o.t_tipo_servicio,
            'garantia', o.b_garantia,
            'periodicidad', o.n_periodicidad,
            'fechaMantenimiento', o.f_fecha_mantenimiento::text,
            'fechaEjecucion', NULL,
            'cliente', (SELECT jsonb_build_object('nombre', c.n_razon_social, 'documento', c.k_documento)
                        FROM sede s
                                 JOIN cliente c ON c.k_id_cliente = s.k_id_cliente
                        WHERE s.k_id_sede = o.k_id_sede),
            'sede', (SELECT jsonb_build_object(
                                    'nombre', s.n_nombre_sede,
                                    'direccion', 'Calle ' || btrim(s.t_calle) || ' # ' || btrim(s.t_carrera)
                                                     || ' - ' || btrim(s.t_numero),
                                    'ciudad', ci.n_nombre_ciudad)
                     FROM sede s
                              JOIN ciudad ci ON ci.k_id_ciudad = s.k_id_ciudad
                     WHERE s.k_id_sede = o.k_id_sede),
            -- El nombre completo como lo compone PersonCommunicationResponse: las partes que haya, sin
            -- huecos. concat_ws se salta los nulos, y NULLIF convierte en nulo lo que solo eran espacios.
            'ingeniero', (SELECT concat_ws(' ', NULLIF(btrim(p.n_primer_nombre), ''),
                                           NULLIF(btrim(p.n_segundo_nombre), ''),
                                           NULLIF(btrim(p.n_primer_apellido), ''),
                                           NULLIF(btrim(p.n_segundo_apellido), ''))
                          FROM persona p
                          WHERE p.k_identificador = o.k_id_ingeniero),
            'equipos', (SELECT coalesce(jsonb_agg(
                                                jsonb_build_object(
                                                        'id', u.k_id_equipo_cliente,
                                                        'area', a.n_nombre_area,
                                                        'tipo', t.n_nombre_tipo_equipo,
                                                        'marca', b.n_nombre_marca,
                                                        'modelo', m.n_nombre_modelo,
                                                        'serie', u.k_serie,
                                                        'numeroHojaVida', u.n_numero_hoja_vida,
                                                        'numeroReporte', (SELECT upper(left(r.k_id_reporte_servicio::text, 6))
                                                                          FROM reporte_servicio r
                                                                          WHERE r.k_id_orden_trabajo = o.k_id_orden_trabajo
                                                                            AND r.k_id_equipo_cliente = u.k_id_equipo_cliente
                                                                            AND r.b_estado_activo))
                                                ORDER BY a.n_nombre_area COLLATE "C" NULLS LAST,
                                                    u.n_numero_hoja_vida COLLATE "C" NULLS LAST,
                                                    u.k_serie COLLATE "C" NULLS LAST,
                                                    u.k_id_equipo_cliente::text),
                                        '[]'::jsonb)
                        FROM orden_trabajo_equipo ote
                                 JOIN equipo_cliente u ON u.k_id_equipo_cliente = ote.k_id_equipo_cliente
                                 JOIN area_servicio a ON a.k_id_area_servicio = ote.k_id_area_servicio
                                 JOIN modelo m ON m.k_id_modelo = u.k_id_modelo
                                 JOIN equipo e ON e.k_id_equipo = m.k_id_equipo
                                 JOIN tipo_equipo t ON t.k_id_tipo_equipo = e.k_id_tipo_equipo
                                 JOIN marca b ON b.k_id_marca = e.k_id_marca
                        WHERE ote.k_id_orden_trabajo = o.k_id_orden_trabajo
                          AND ote.b_estado_activo))
WHERE o.t_estado_ejecucion = 'EJECUTADA';

ALTER TABLE orden_trabajo
    ADD CONSTRAINT "CHK_orden_trabajo_documento"
        CHECK (CASE
                   WHEN t_estado_ejecucion = 'EJECUTADA' THEN t_documento IS NOT NULL
                   ELSE t_documento IS NULL AND NOT b_documento_tardio
               END);
