-- V16: el codigo interno y el proveedor de cada equipo instalado.
--
-- Los pide la hoja de vida impresa que aprobo el usuario el 2026-10-05, y los dos son de la UNIDAD, no
-- del modelo: el codigo interno es el que el cliente le pone a SU maquina, como la placa de inventario,
-- y el proveedor es quien le vendio ESA maquina. Dos balanzas iguales pueden venir de proveedores
-- distintos.
--
-- Opcionales: los equipos que ya existen no los tienen, y un equipo se registra cuando llega, antes de
-- que alguien busque la factura. El proveedor es texto libre: un catalogo de proveedores no lo ha pedido
-- nadie, y montarlo ahora seria decidir por adelantado algo que el uso todavia no dice.

ALTER TABLE equipo_cliente
    ADD COLUMN n_codigo_interno varchar(50)  NULL,
    ADD COLUMN n_proveedor      varchar(100) NULL;
