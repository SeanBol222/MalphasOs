-- V34: la garantía y el número de una orden de trabajo (2026-10-16).
--
-- Decidido por el usuario con el diseño único del informe:
--
--   1. Un correctivo puede estar «en garantía»: el informe lo marca en su casilla. Es una marca del
--      correctivo y no un cuarto tipo de servicio, de modo que solo un correctivo la lleva. Se decide
--      al programar la orden, que no se edita. Falso para las que ya existen: ninguna se registró así.
--   2. La orden lleva número, como el reporte (V28): los seis primeros caracteres de su
--      identificador, y el informe lo imprime. Quien la programa elige otro identificador si el
--      prefijo está tomado, y este índice lo garantiza. No es parcial: una orden cancelada conserva su
--      número. Con dos órdenes ya registradas que compartieran prefijo, esta migración fallaría en vez
--      de renumerar una en silencio; en la base de desarrollo no hay ninguna.

ALTER TABLE orden_trabajo
    ADD COLUMN b_garantia boolean NOT NULL DEFAULT false,
    ADD CONSTRAINT "CHK_orden_trabajo_garantia" CHECK (NOT b_garantia OR t_tipo_servicio = 'CORRECTIVO');

CREATE UNIQUE INDEX "UQ_orden_trabajo_numero"
    ON orden_trabajo ((left(k_id_orden_trabajo::text, 6)));

COMMENT ON COLUMN orden_trabajo.b_garantia IS
    'Un correctivo en garantía. Solo un correctivo puede llevarla.';
