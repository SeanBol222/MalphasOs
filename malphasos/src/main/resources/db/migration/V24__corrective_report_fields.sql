-- V24: lo que un correctivo registra además de los cinco campos de RF-15 (2026-10-14).
--
-- Decidido por el usuario: un correctivo cuenta la falla reportada, el diagnóstico, la causa, el
-- trabajo realizado y los repuestos cambiados. Falla, diagnóstico y trabajo ya existían desde V9 —son
-- tres de los cinco campos de RF-15—; faltaban la causa y los repuestos.
--
-- Que en un correctivo la falla y el diagnóstico sean obligatorios para cerrar NO va aquí: esta tabla
-- no sabe de qué tipo de servicio es el reporte —lo dice la orden, y el reporte no copia nada de ella,
-- que es lo que RF-11 pide—. Vive en el servicio, como las demás reglas que el esquema no puede ver.

ALTER TABLE reporte_servicio
    ADD COLUMN t_causa     varchar(500) NULL,
    ADD COLUMN t_repuestos varchar(500) NULL;

COMMENT ON COLUMN reporte_servicio.t_causa IS
    'Por qué falló el equipo, según el ingeniero. Sobre todo de un correctivo; opcional.';
COMMENT ON COLUMN reporte_servicio.t_repuestos IS
    'Repuestos cambiados, en texto libre. Opcional: no hay inventario que los descuente (RF-36 es Won''t Have).';
