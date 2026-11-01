-- La aprobación de una orden por el supervisor de su cliente (2026-11-01): lo que permite facturarla.
--
-- Una tabla aparte y no columnas en orden_trabajo porque una aprobación se puede retirar —sustituir un
-- reporte de una orden aprobada la retira, porque el supervisor aprobó un documento que ya no es el
-- mismo— y la retirada se queda como historial. Una sola activa por orden.
--
-- Nada se copia: el nombre de quien aprueba está en persona, su cargo en supervisor_cliente y el cliente
-- de la orden se llega por su sede. Tampoco hay columna de «forma»: aprobó desde su cuenta si quien
-- tenía la sesión es el propio supervisor, y en sitio si no. Una columna aparte podría contradecirlo.

CREATE TABLE aprobacion_orden
(
    k_id_aprobacion    uuid        NOT NULL,
    k_id_orden_trabajo uuid        NOT NULL,
    k_id_supervisor    uuid        NOT NULL,
    -- Quien tenía la sesión. Nulo si era el SuperUsuario, que no tiene ficha en persona.
    k_id_operador      uuid        NULL,
    k_huella_firma     varchar(64) NOT NULL,
    f_aprobada         timestamp   NOT NULL,
    b_estado_activo    boolean     NOT NULL DEFAULT true,
    CONSTRAINT "PK_aprobacion_orden" PRIMARY KEY (k_id_aprobacion),
    CONSTRAINT "FK_aprobacion_orden_orden"
        FOREIGN KEY (k_id_orden_trabajo) REFERENCES orden_trabajo (k_id_orden_trabajo),
    -- A persona y no a supervisor_cliente: la aprobación sigue valiendo si después lo relevan.
    CONSTRAINT "FK_aprobacion_orden_supervisor"
        FOREIGN KEY (k_id_supervisor) REFERENCES persona (k_identificador),
    CONSTRAINT "FK_aprobacion_orden_operador"
        FOREIGN KEY (k_id_operador) REFERENCES persona (k_identificador),
    CONSTRAINT "FK_aprobacion_orden_firma"
        FOREIGN KEY (k_huella_firma) REFERENCES imagen_documento (k_huella)
);

CREATE UNIQUE INDEX "UQ_aprobacion_orden_activa"
    ON aprobacion_orden (k_id_orden_trabajo) WHERE b_estado_activo;
CREATE INDEX "IX_aprobacion_orden_supervisor" ON aprobacion_orden (k_id_supervisor);
CREATE INDEX "IX_aprobacion_orden_operador" ON aprobacion_orden (k_id_operador);
CREATE INDEX "IX_aprobacion_orden_firma" ON aprobacion_orden (k_huella_firma);

COMMENT ON TABLE aprobacion_orden IS
    'La aprobación de una orden ejecutada por un supervisor de su cliente. Una activa por orden; las '
    'retiradas quedan como historial.';
COMMENT ON COLUMN aprobacion_orden.k_id_operador IS
    'Quién tenía la sesión al aprobar. Igual al supervisor: desde su cuenta. Distinto o nulo: en sitio.';
COMMENT ON COLUMN aprobacion_orden.k_huella_firma IS
    'La firma, en imagen_documento por su huella: esa versión, aunque el supervisor cambie la suya después.';
