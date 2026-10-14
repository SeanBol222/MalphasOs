-- V26: la tolerancia de cada verificación de un tipo de equipo (2026-10-14).
--
-- Decidido por el usuario: la tolerancia se expresa en la unidad de la verificación —±0,5 °C, ±2 mmHg—
-- y una lectura es conforme si |equipo − patrón| ≤ tolerancia. Con la misma escala que los puntos y las
-- lecturas, numeric(12,4), porque se compara con su diferencia.
--
-- Nula se admite, y es a propósito: los tipos ya creados no la tienen, y sin ella la lectura se registra
-- igual y la conformidad dice «sin tolerancia» en lugar de inventarse una. Cuando la hay, es positiva:
-- una tolerancia cero exigiría una igualdad que ningún instrumento da.

ALTER TABLE verificacion_tipo_equipo
    ADD COLUMN d_tolerancia numeric(12, 4) NULL,
    ADD CONSTRAINT "CHK_verificacion_tipo_equipo_tolerancia" CHECK (d_tolerancia IS NULL OR d_tolerancia > 0);

COMMENT ON COLUMN verificacion_tipo_equipo.d_tolerancia IS
    'Error admitido, en la unidad de la verificación. Conforme si |equipo − patrón| ≤ tolerancia. Nula: sin tolerancia declarada.';
