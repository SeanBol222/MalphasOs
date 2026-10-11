-- V23: paso_reporte recibe b_estado_activo, como todas las demas tablas.
--
-- V22 la creo sin ella, y no por decision: se escribio sin mirar la convencion, y lo destapo el recuento
-- del esquema el 2026-10-10, al ir a escribir «30 de 30». El usuario eligio anadirla antes que escribir
-- una excepcion. Hoy ningun paso se retira -solo cambia de estado, y vive y muere con su reporte-, asi
-- que todas las filas quedan en verdadero; pero el borrado logico es universal en este esquema, y una
-- excepcion es algo que el siguiente que lea la convencion tendria que descubrir.

ALTER TABLE paso_reporte
    ADD COLUMN b_estado_activo boolean NOT NULL DEFAULT true;
