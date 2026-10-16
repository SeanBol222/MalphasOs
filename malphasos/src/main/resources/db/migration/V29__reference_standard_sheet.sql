-- V29: la ficha de un equipo patrón (2026-10-16).
--
-- Los informes que la empresa entrega hoy describen cada patrón en su tabla de trazabilidad con nueve
-- datos; V25 guardaba cuatro. Entran los que faltaban: modelo, código interno, rango, resolución, fecha
-- de calibración y empresa que lo calibró.
--
-- Todos opcionales, y a propósito: los patrones ya registrados no los tienen, y un patrón hecho en casa
-- no tiene modelo ni la calibra una empresa externa. Rango y resolución son texto y no números con
-- unidad, porque un mismo patrón cubre a veces dos magnitudes —un termohigrómetro: «0 a 50 °C; 10 a
-- 90 %HR»— y así se imprime.
--
-- La calibración no puede ser posterior al vencimiento del certificado que produjo. Con la fecha nula la
-- comparación da NULL y el CHECK se satisface, que es lo que se quiere: sin fecha no hay nada que
-- contrastar.

ALTER TABLE equipo_patron
    ADD COLUMN n_modelo              varchar(80)  NULL,
    ADD COLUMN k_codigo_interno      varchar(40)  NULL,
    ADD COLUMN t_rango               varchar(120) NULL,
    ADD COLUMN t_resolucion          varchar(80)  NULL,
    ADD COLUMN f_calibracion         date         NULL,
    ADD COLUMN n_empresa_calibradora varchar(120) NULL,
    ADD CONSTRAINT "CHK_equipo_patron_modelo" CHECK (n_modelo IS NULL OR btrim(n_modelo) <> ''),
    ADD CONSTRAINT "CHK_equipo_patron_codigo_interno"
        CHECK (k_codigo_interno IS NULL OR btrim(k_codigo_interno) <> ''),
    ADD CONSTRAINT "CHK_equipo_patron_rango" CHECK (t_rango IS NULL OR btrim(t_rango) <> ''),
    ADD CONSTRAINT "CHK_equipo_patron_resolucion" CHECK (t_resolucion IS NULL OR btrim(t_resolucion) <> ''),
    ADD CONSTRAINT "CHK_equipo_patron_empresa_calibradora"
        CHECK (n_empresa_calibradora IS NULL OR btrim(n_empresa_calibradora) <> ''),
    ADD CONSTRAINT "CHK_equipo_patron_calibracion" CHECK (f_calibracion <= f_vencimiento_certificado);

COMMENT ON COLUMN equipo_patron.k_codigo_interno IS
    'El código con el que la empresa marca el patrón en su inventario. No es la serie del fabricante.';
COMMENT ON COLUMN equipo_patron.f_calibracion IS
    'El día en que se calibró, que es el del certificado vigente. No puede ser posterior a su vencimiento.';
