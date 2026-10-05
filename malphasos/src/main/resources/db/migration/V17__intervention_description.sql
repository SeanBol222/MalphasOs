-- V17: cada linea del historial dice que se hizo y quien lo hizo.
--
-- La hoja de vida impresa pide, en cada servicio, su descripcion y su responsable. Los dos estan en el
-- sistema -los procedimientos en el reporte, el ingeniero en la orden- y la linea del historial no los
-- traia. Decisiones del usuario del 2026-10-05: la descripcion son los PROCEDIMIENTOS del reporte, y el
-- responsable es el NOMBRE del ingeniero de la orden.
--
-- SON UNA COPIA CONGELADA, como la fecha, el tipo y el resultado (ver congelar-una-referencia-historica):
-- una hoja de vida es un documento con valor legal, y lo que dice de un servicio no debe cambiar porque
-- alguien corrija despues el nombre de una persona. Y equipment no puede leer report sin el ciclo que
-- ya obligo a poner el oyente en report: el oyente las pasa en el comando.
--
-- Opcionales: una orden puede no tener ingeniero asignado, y un reporte viejo puede no tener
-- procedimientos. La longitud de la descripcion es la de t_procedimientos; la del responsable cabe un
-- nombre completo de cuatro partes de 50.

ALTER TABLE intervencion
    ADD COLUMN t_descripcion varchar(500) NULL,
    ADD COLUMN n_responsable varchar(250) NULL;

COMMENT ON COLUMN intervencion.t_descripcion IS
    'Copia congelada de los procedimientos del reporte al cerrarse.';
COMMENT ON COLUMN intervencion.n_responsable IS
    'Copia congelada del nombre completo del ingeniero de la orden al cerrarse el reporte.';

-- El relleno de las lineas que ya existen va en V18, aparte, como V13 fue aparte de V12: asi se puede
-- volver a ejecutar sobre datos en una prueba, que es la unica forma de probarlo.
