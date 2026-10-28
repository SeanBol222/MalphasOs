-- V48: la verificación llega a su magnitud por la unidad (2026-10-28).
--
-- Grupo A4 del análisis del esquema (Documentation/DatabaseDesign/analisis-2026-10-27.md), decidido por
-- el usuario. verificacion_tipo_equipo guardaba la magnitud y la unidad, y la unidad ya dice su magnitud:
-- una dependencia transitiva, como la del cliente de la orden que quitó V47. V10 la sostenía con una
-- foránea compuesta contra (magnitud, unidad), y con una restricción única en unidad_medida que solo
-- existía para ella.
--
-- LO QUE EL ESQUEMA DEJA DE COMPROBAR, y dónde queda. La magnitud estaba en la tabla para poder declarar
-- el índice único «un tipo no se verifica dos veces en la misma magnitud», y ese índice sale con ella.
-- La regla ya la imponía el agregado (EquipmentType.exigirMagnitudesDistintas), y queda solo ahí: un tipo
-- se guarda con todas sus verificaciones a la vez, de modo que no hay un camino que las escriba de una en
-- una y se salte la comprobación. «La unidad es de esa magnitud» deja de poder romperse: ya no hay una
-- magnitud aparte con la que la unidad pueda contradecirse.
--
-- No mueve datos: la magnitud de cada verificación es la de su unidad, y la foránea compuesta garantizaba
-- que coincidieran.

ALTER TABLE verificacion_tipo_equipo DROP CONSTRAINT "FK_verificacion_tipo_equipo_unidad_de_su_magnitud";
DROP INDEX "UQ_verificacion_magnitud_activa_por_tipo";
ALTER TABLE verificacion_tipo_equipo DROP COLUMN k_id_magnitud;

ALTER TABLE verificacion_tipo_equipo
    ADD CONSTRAINT "FK_verificacion_tipo_equipo_unidad"
        FOREIGN KEY (k_id_unidad_medida) REFERENCES unidad_medida (k_id_unidad_medida);

CREATE INDEX "IX_verificacion_tipo_equipo_unidad" ON verificacion_tipo_equipo (k_id_unidad_medida);

-- Existía solo para que la foránea compuesta pudiera apuntar al par.
ALTER TABLE unidad_medida DROP CONSTRAINT "UQ_unidad_medida_por_magnitud";
