-- V39: la firma de quien recibe el servicio (2026-10-17).
--
-- Decidido por el usuario con la versión 1.0.0: el cliente o su encargado firma el recibido en la
-- pantalla (RF-18), antes o después de cerrar el reporte —muchas veces el equipo se entrega después—, y
-- con una casilla la misma firma vale para los reportes de la orden que aún no tengan (RF-21).
--
-- LA IMAGEN NO VA EN EL REPORTE: se guarda en imagen_documento, una vez por versión y por su huella, como
-- el logo (V37) y la firma del ingeniero (V38). Replicada a los veinte reportes de una orden, se guarda
-- una sola vez. Aquí la huella, con su foránea, y cuándo se firmó.
--
-- Una firma sin nombre no dice quién firmó: exige n_recibe_nombre (V31). Con CASE y no con OR, por la
-- trampa de V9: un CHECK se satisface con NULL.

ALTER TABLE reporte_servicio
    ADD COLUMN k_huella_firma_recibe varchar(64) NULL,
    ADD COLUMN f_firma_recibe        timestamp   NULL,
    ADD CONSTRAINT "FK_reporte_servicio_firma_recibe"
        FOREIGN KEY (k_huella_firma_recibe) REFERENCES imagen_documento (k_huella),
    ADD CONSTRAINT "CHK_reporte_servicio_firma_recibe"
        CHECK (CASE
                   WHEN k_huella_firma_recibe IS NULL THEN f_firma_recibe IS NULL
                   ELSE f_firma_recibe IS NOT NULL AND n_recibe_nombre IS NOT NULL
               END);

COMMENT ON COLUMN reporte_servicio.k_huella_firma_recibe IS
    'La firma de quien recibió el servicio, por su huella en imagen_documento. Nula: sin firmar todavía.';
COMMENT ON COLUMN reporte_servicio.f_firma_recibe IS
    'Cuándo firmó quien recibe. Puede ser posterior al cierre: la firma es lo único que se añade a un reporte cerrado.';

COMMENT ON TABLE imagen_documento IS
    'Cada versión de una imagen que algún informe congeló —el logo de la empresa, las firmas del ingeniero y de quien recibe—, una sola vez, por la huella SHA-256 de sus bytes.';
