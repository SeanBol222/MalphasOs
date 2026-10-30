-- V50: sin tabla puente entre la orden y el equipo (2026-10-28).
--
-- Propuesto por el usuario con el análisis del esquema (Documentation/DatabaseDesign/analisis-2026-10-27.md,
-- «Sin tabla puente entre la orden y el equipo»). orden_trabajo_equipo y reporte_servicio guardaban el
-- mismo hecho —«este equipo está en esta orden»— y podían contradecirse: en desarrollo, cinco equipos
-- estaban en una orden sin reporte. Desde aquí UN EQUIPO ESTÁ EN UNA ORDEN SI Y SOLO SI TIENE EN ELLA UN
-- REPORTE ACTIVO, y el reporte apunta a la orden y al equipo con dos foráneas simples.
--
-- Con la tabla sale B6, el área de cada equipo al seleccionarlo: mientras la orden vive el equipo no se
-- traslada —regla del servicio— y al ejecutarla el área queda escrita en su documento (V49).
--
-- LO QUE SE RELLENA, con las cuatro reglas que aceptó el usuario:
--
--   1. Cada equipo del puente sin reporte activo recibe su borrador, como si se añadiera hoy: así lo hará
--      el servicio desde ahora. Con los pasos del protocolo de su tipo si la orden es preventiva, copiados
--      como los copia Java (las asignaciones activas, por prioridad). Las órdenes ejecutadas no reciben
--      ninguno: ya están congeladas, y un borrador en una orden ejecutada es lo que la regla de ejecutar
--      prohíbe. Las canceladas sí, para que sigan diciendo qué equipos tenían.
--   2. Un borrador de un equipo que ya se había sacado de la orden se retira: sacar un equipo es retirar
--      su reporte. Uno cerrado se queda, y con él el equipo vuelve a la orden: un reporte cerrado cuenta
--      un servicio que se prestó, y no se toca.
--
-- El número de cada borrador —los seis primeros caracteres del identificador, V28— se elige libre, igual
-- que hace el servicio: se prueba otro identificador si el prefijo está tomado.

DO
$$
    DECLARE
        pendiente record;
        nuevo     uuid;
    BEGIN
        FOR pendiente IN
            SELECT ote.k_id_orden_trabajo, ote.k_id_equipo_cliente, o.t_tipo_servicio, e.k_id_tipo_equipo
            FROM orden_trabajo_equipo ote
                     JOIN orden_trabajo o ON o.k_id_orden_trabajo = ote.k_id_orden_trabajo
                     JOIN equipo_cliente u ON u.k_id_equipo_cliente = ote.k_id_equipo_cliente
                     JOIN modelo m ON m.k_id_modelo = u.k_id_modelo
                     JOIN equipo e ON e.k_id_equipo = m.k_id_equipo
            WHERE ote.b_estado_activo
              AND o.t_estado_ejecucion <> 'EJECUTADA'
              AND NOT EXISTS (SELECT 1
                              FROM reporte_servicio r
                              WHERE r.k_id_orden_trabajo = ote.k_id_orden_trabajo
                                AND r.k_id_equipo_cliente = ote.k_id_equipo_cliente
                                AND r.b_estado_activo)
            LOOP
                LOOP
                    nuevo := gen_random_uuid();
                    EXIT WHEN NOT EXISTS (SELECT 1
                                          FROM reporte_servicio
                                          WHERE left(k_id_reporte_servicio::text, 6) = left(nuevo::text, 6));
                END LOOP;

                INSERT INTO reporte_servicio (k_id_reporte_servicio, k_id_orden_trabajo, k_id_equipo_cliente)
                VALUES (nuevo, pendiente.k_id_orden_trabajo, pendiente.k_id_equipo_cliente);

                IF pendiente.t_tipo_servicio = 'PREVENTIVO' THEN
                    INSERT INTO paso_reporte (k_id_paso_reporte, k_id_reporte_servicio, i_orden, k_id_protocolo,
                                              n_nombre, t_descripcion, t_indicacion)
                    SELECT gen_random_uuid(), nuevo, a.i_prioridad, p.k_id_protocolo, p.n_nombre, p.t_descripcion,
                           a.t_indicacion
                    FROM protocolo_tipo_equipo a
                             JOIN protocolo p ON p.k_id_protocolo = a.k_id_protocolo
                    WHERE a.k_id_tipo_equipo = pendiente.k_id_tipo_equipo
                      AND a.b_estado_activo;
                END IF;
            END LOOP;
    END
$$;

UPDATE reporte_servicio r
SET b_estado_activo = false
FROM orden_trabajo_equipo ote
WHERE ote.k_id_orden_trabajo = r.k_id_orden_trabajo
  AND ote.k_id_equipo_cliente = r.k_id_equipo_cliente
  AND NOT ote.b_estado_activo
  AND r.b_estado_activo
  AND r.t_estado_reporte = 'BORRADOR';

-- Las dos foráneas simples. Sus índices ya existen desde V9: IX_reporte_servicio_orden e
-- IX_reporte_servicio_equipo_cliente.
ALTER TABLE reporte_servicio
    DROP CONSTRAINT "FK_reporte_servicio_equipo_de_la_orden",
    ADD CONSTRAINT "FK_reporte_servicio_orden"
        FOREIGN KEY (k_id_orden_trabajo) REFERENCES orden_trabajo (k_id_orden_trabajo),
    ADD CONSTRAINT "FK_reporte_servicio_equipo_cliente"
        FOREIGN KEY (k_id_equipo_cliente) REFERENCES equipo_cliente (k_id_equipo_cliente);

DROP TABLE orden_trabajo_equipo;

COMMENT ON TABLE reporte_servicio IS
    'Lo que se le hizo a un equipo en una orden. Desde V50 es también lo que pone el equipo en la orden: '
    'un equipo está en una orden si y solo si tiene en ella un reporte activo.';
