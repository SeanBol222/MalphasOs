-- V33: el tipo de equipo deja de elegir formato de reporte (2026-10-16).
--
-- V32 lo añadió ese mismo día y el usuario lo retiró unas horas después: los formatos MP0, MP1 y MP2
-- eran los del sistema antiguo, y MalphasOS imprime un solo «Informe de mantenimiento» cuyas
-- secciones aparecen según los datos —sin lecturas no hay página de verificación—, que es lo que el
-- formato decía a mano. V32 no se toca: ya está aplicada, y cambiarla rompe la suma de Flyway.

ALTER TABLE tipo_equipo
    DROP CONSTRAINT "CHK_tipo_equipo_formato_reporte",
    DROP COLUMN t_formato_reporte;
