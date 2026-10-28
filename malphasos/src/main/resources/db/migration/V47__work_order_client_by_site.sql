-- V47: la orden llega a su cliente por la sede (2026-10-28).
--
-- Grupo A3 del análisis del esquema (Documentation/DatabaseDesign/analisis-2026-10-27.md), decidido por
-- el usuario. orden_trabajo guardaba el cliente y la sede, y la sede ya dice su cliente: una dependencia
-- transitiva, lo que la tercera forma normal prohíbe. V6 lo sostenía con una foránea compuesta contra
-- (sede, cliente), que impedía que se contradijeran, pero el dato estaba dos veces y hacía falta una
-- restricción única en sede solo para eso.
--
-- EL CLIENTE NO DESAPARECE DE LA ORDEN, solo de la tabla: una orden sigue siendo de un cliente, y el
-- código lo lee a través de su sede. Y como una sede no cambia de cliente —no hay operación que lo haga—,
-- el cliente de una orden tampoco cambia.
--
-- El índice de la sede ya existe desde V6 (IX_orden_trabajo_sede), así que la foránea simple que
-- sustituye a la compuesta nace indexada.

ALTER TABLE orden_trabajo DROP CONSTRAINT "FK_orden_trabajo_sede_del_cliente";
ALTER TABLE orden_trabajo DROP CONSTRAINT "FK_orden_trabajo_cliente";
DROP INDEX "IX_orden_trabajo_cliente";
ALTER TABLE orden_trabajo DROP COLUMN k_id_cliente;

ALTER TABLE orden_trabajo
    ADD CONSTRAINT "FK_orden_trabajo_sede" FOREIGN KEY (k_id_sede) REFERENCES sede (k_id_sede);

-- Existía solo para que la foránea compuesta pudiera apuntar al par.
ALTER TABLE sede DROP CONSTRAINT "UQ_sede_identidad_con_cliente";
