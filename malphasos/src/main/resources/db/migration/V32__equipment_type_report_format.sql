-- V32: con qué formato se imprime el reporte de un tipo de equipo (2026-10-16).
--
-- Decidido por el usuario: el formato lo elige el tipo de equipo. Los informes que la empresa entrega
-- hoy usan tres —BB-ING-MP0, MP1 y MP2, versión 1 de noviembre de 2019—, y MP0 es el de los equipos
-- que no se verifican: una sola página, sin tabla de lecturas ni gráfica.
--
-- Obligatorio, y los tipos que ya existen se rellenan con la misma regla que el dominio aplica al
-- crear uno: MP2 si se verifica en algo, MP0 si no. Que MP0 vaya solo con un tipo sin verificaciones, y
-- MP1 y MP2 solo con uno que las tenga, no se puede expresar aquí —las verificaciones están en otra
-- tabla— y vive en el agregado.
--
-- Con DEFAULT 'MP0', y no es una comodidad: un tipo recién insertado todavía no tiene verificaciones,
-- que viven en otra tabla y se insertan después, así que MP0 es exactamente lo que le toca a quien lo
-- inserte sin pasar por el dominio. Lo destaparon ocho clases de prueba que insertan tipos con SQL.

ALTER TABLE tipo_equipo
    ADD COLUMN t_formato_reporte varchar(3) NULL;

UPDATE tipo_equipo t
SET t_formato_reporte = CASE
                            WHEN EXISTS (SELECT 1
                                         FROM verificacion_tipo_equipo v
                                         WHERE v.k_id_tipo_equipo = t.k_id_tipo_equipo
                                           AND v.b_estado_activo)
                                THEN 'MP2'
                            ELSE 'MP0'
                        END;

ALTER TABLE tipo_equipo
    ALTER COLUMN t_formato_reporte SET NOT NULL,
    ALTER COLUMN t_formato_reporte SET DEFAULT 'MP0',
    ADD CONSTRAINT "CHK_tipo_equipo_formato_reporte" CHECK (t_formato_reporte IN ('MP0', 'MP1', 'MP2'));

COMMENT ON COLUMN tipo_equipo.t_formato_reporte IS
    'Formato del reporte impreso: MP0 sin verificación, MP1 o MP2 con ella. Se imprime como BB-ING-<formato>.';
