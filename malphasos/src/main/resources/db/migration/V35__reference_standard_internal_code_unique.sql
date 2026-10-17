-- V35: el código interno de un equipo patrón no se repite entre los vigentes (2026-10-17).
--
-- Decidido por el usuario con el diseño único del informe: el código interno es con lo que la empresa
-- nombra al patrón en su inventario, y el informe lo imprime. Dos patrones vigentes con el mismo código
-- harían ambigua la trazabilidad de una lectura. Es la misma regla que la serie (V25): parcial, para que
-- un patrón retirado libere su código, y sin tocar los nulos, porque el código es opcional.
--
-- Si ya hubiera dos vigentes con el mismo código, el índice no se crea y la migración falla diciéndolo:
-- elegir cuál de los dos se queda con él no se puede deducir.

CREATE UNIQUE INDEX "UQ_equipo_patron_codigo_interno_activo"
    ON equipo_patron (k_codigo_interno)
    WHERE b_estado_activo AND k_codigo_interno IS NOT NULL;
