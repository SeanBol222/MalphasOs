-- V28: el número de un reporte son los seis primeros caracteres de su identificador (2026-10-16).
--
-- Decidido por el usuario: «como los commits de git, los primeros 6 dígitos». Son hexadecimales
-- —0-9 y a-f— y se muestran en mayúsculas: 1DC6F5. Seis caracteres son 16,7 millones de valores, y con
-- miles de reportes dos podrían compartir prefijo. Git lo resuelve alargando el prefijo, pero un número
-- impreso en un documento firmado no puede cambiar después; aquí se resuelve al abrir el reporte, que
-- elige otro identificador si el prefijo ya está tomado, y este índice lo garantiza.
--
-- No es parcial: un reporte retirado conserva su número, y otro no puede heredarlo.

CREATE UNIQUE INDEX "UQ_reporte_servicio_numero"
    ON reporte_servicio ((left(k_id_reporte_servicio::text, 6)));
