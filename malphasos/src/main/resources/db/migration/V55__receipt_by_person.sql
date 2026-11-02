-- Quien recibe puede ser una persona registrada (2026-11-01): el supervisor del cliente que recibe y
-- aprueba a la vez, como el doctor de un consultorio. Entonces el reporte apunta a su ficha y no copia su
-- nombre, su cédula ni su cargo, que se leen de persona y de supervisor_cliente al pintar el documento.
--
-- Quien recibe es una persona registrada o un nombre escrito, nunca las dos cosas: el escrito sigue para
-- quien no está registrado, como la jefa de enfermería de turno (V31).

ALTER TABLE reporte_servicio
    ADD COLUMN k_id_recibe uuid NULL,
    ADD CONSTRAINT "FK_reporte_servicio_recibe"
        FOREIGN KEY (k_id_recibe) REFERENCES persona (k_identificador),
    DROP CONSTRAINT "CHK_reporte_servicio_recibe",
    ADD CONSTRAINT "CHK_reporte_servicio_recibe"
        CHECK (CASE
                   WHEN k_id_recibe IS NOT NULL
                       THEN n_recibe_nombre IS NULL AND k_recibe_cedula IS NULL AND n_recibe_cargo IS NULL
                   WHEN n_recibe_nombre IS NULL
                       THEN k_recibe_cedula IS NULL AND n_recibe_cargo IS NULL
                   ELSE btrim(n_recibe_nombre) <> ''
                       AND (k_recibe_cedula IS NULL OR btrim(k_recibe_cedula) <> '')
                       AND (n_recibe_cargo IS NULL OR btrim(n_recibe_cargo) <> '')
               END),
    DROP CONSTRAINT "CHK_reporte_servicio_firma_recibe",
    ADD CONSTRAINT "CHK_reporte_servicio_firma_recibe"
        CHECK (CASE
                   WHEN k_huella_firma_recibe IS NULL THEN f_firma_recibe IS NULL
                   ELSE f_firma_recibe IS NOT NULL
                       AND (n_recibe_nombre IS NOT NULL OR k_id_recibe IS NOT NULL)
               END);

CREATE INDEX "IX_reporte_servicio_recibe" ON reporte_servicio (k_id_recibe);

COMMENT ON COLUMN reporte_servicio.k_id_recibe IS
    'Quien recibió, si es una persona registrada (V55). Con ella, los tres campos de texto van nulos.';
