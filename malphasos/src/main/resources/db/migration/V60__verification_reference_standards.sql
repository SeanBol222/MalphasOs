-- V60: con qué patrones se verifica cada tipo de equipo, y cuáles lleva cada orden a su visita (2026-11-19).
--
-- Escrita el 2026-11-18 como V59 y rehecha sobre el reporte de V59, que guarda lo que dice en t_contenido.
--
-- Decidido por el usuario: hasta aquí el reporte ofrecía todos los patrones en cada verificación, y el
-- ingeniero buscaba el suyo entre todos. Desde aquí:
--
--   1. EL CATÁLOGO DICE CON QUÉ PATRONES SE HACE CADA VERIFICACIÓN DE UN TIPO (patron_verificacion). La
--      masa de una pesa bebé, con las pesas de 5 kg y el juego de masas; el volumen de una pipeta, con la
--      balanza analítica y el termohigrómetro, que es de apoyo: la pesada se corrige por la temperatura y
--      la humedad del ambiente. Va a la verificación y no al tipo entero porque un monitor tiene tres, y
--      cada patrón cae en la suya; y no a la magnitud, porque dos tipos que miden masa no usan las
--      mismas pesas, y un patrón de apoyo no mide la magnitud que se verifica.
--   2. LA ORDEN ELIGE LOS PATRONES DE SU VISITA POR TIPO DE EQUIPO (patron_orden_trabajo): las pesas de
--      5 kg para las pesa bebé y las de 20 kg para las de piso, en la misma orden. Se cambian hasta
--      ejecutarla; al ejecutarla quedan escritos en su documento (V49), como los equipos. Si para un tipo
--      el catálogo permite un solo patrón vigente, el servicio lo pone solo.
--   3. CADA REPORTE DICE DE DÓNDE VINO CADA PATRÓN: desdeOrden, en cada patrón de t_contenido. No pide
--      nada aquí: V59 ya lo escribe, falso en todos. Cambiar los patrones de la orden pone y quita los
--      suyos en los reportes abiertos de ese tipo, en las verificaciones que el catálogo les permite, y
--      nunca toca los que el ingeniero puso a mano.
--
-- Lo que el esquema no expresa vive en el servicio: que el patrón de un reporte esté permitido para su
-- verificación y tenga el certificado vigente el día del servicio; que el de una orden esté permitido para
-- alguna verificación activa de ese tipo, esté activo, y que el tipo tenga equipos en la orden; y que una
-- orden ejecutada no los cambie. Una foránea no puede decir «alguna verificación de este tipo»: el tipo
-- está un salto por encima de la verificación.
--
-- Sin borrado lógico en las dos tablas nuevas, como foto_equipo_cliente (V58): quitar un patrón de una
-- verificación del catálogo o de una orden abierta no es historia de nada. La historia es lo que cada
-- reporte cerrado guarda del patrón en su documento (V51), y la de la orden ejecutada, en el suyo (V49).
--
-- QUÉ PASA CON LOS DATOS:
--   - Cada verificación recibe los patrones con que el historial la midió, leídos de t_contenido, y de
--     los reportes retirados también —un reporte retirado también se midió con ellos—. Sin eso, ningún
--     patrón ya registrado se ofrecería en ninguna verificación. En desarrollo son 83 pares. Lo que el historial tenga mal se queda aquí hasta que alguien lo quite en el
--     catálogo: la migración no distingue un error de un uso raro.
--   - Ninguna orden recibe patrones: las abiertas siguen como hasta hoy, con lo que cada reporte ya tiene.
--   - Los patrones que los reportes ya tienen quedan como puestos a mano: desdeOrden falso, como los dejó
--     V59.

CREATE TABLE patron_verificacion
(
    k_id_verificacion  uuid NOT NULL,
    k_id_equipo_patron uuid NOT NULL,

    CONSTRAINT "PK_patron_verificacion" PRIMARY KEY (k_id_verificacion, k_id_equipo_patron),
    CONSTRAINT "FK_patron_verificacion_verificacion"
        FOREIGN KEY (k_id_verificacion) REFERENCES verificacion_tipo_equipo (k_id_verificacion),
    CONSTRAINT "FK_patron_verificacion_patron"
        FOREIGN KEY (k_id_equipo_patron) REFERENCES equipo_patron (k_id_equipo_patron)
);

-- Para la ficha del patrón: «¿en qué verificaciones se usa?».
CREATE INDEX "IX_patron_verificacion_patron" ON patron_verificacion (k_id_equipo_patron);

COMMENT ON TABLE patron_verificacion IS
    'Con qué patrones se hace cada verificación de un tipo de equipo: los únicos que un reporte puede usar en ella.';

CREATE TABLE patron_orden_trabajo
(
    k_id_orden_trabajo uuid NOT NULL,
    k_id_tipo_equipo   uuid NOT NULL,
    k_id_equipo_patron uuid NOT NULL,

    CONSTRAINT "PK_patron_orden_trabajo" PRIMARY KEY (k_id_orden_trabajo, k_id_tipo_equipo, k_id_equipo_patron),
    CONSTRAINT "FK_patron_orden_trabajo_orden"
        FOREIGN KEY (k_id_orden_trabajo) REFERENCES orden_trabajo (k_id_orden_trabajo),
    CONSTRAINT "FK_patron_orden_trabajo_tipo"
        FOREIGN KEY (k_id_tipo_equipo) REFERENCES tipo_equipo (k_id_tipo_equipo),
    CONSTRAINT "FK_patron_orden_trabajo_patron"
        FOREIGN KEY (k_id_equipo_patron) REFERENCES equipo_patron (k_id_equipo_patron)
);

-- Para retirar un patrón del catálogo sabiendo en qué órdenes abiertas está.
CREATE INDEX "IX_patron_orden_trabajo_patron" ON patron_orden_trabajo (k_id_equipo_patron);
-- Y para la foránea al tipo, que no encabeza la llave: toda foránea lleva un índice que la encabece (V42),
-- y lo exige ForeignKeyIndexContractTest. La de la orden ya la encabeza la llave primaria.
CREATE INDEX "IX_patron_orden_trabajo_tipo" ON patron_orden_trabajo (k_id_tipo_equipo);

COMMENT ON TABLE patron_orden_trabajo IS
    'Los patrones que una orden lleva a su visita para cada tipo de equipo. Sus reportes de ese tipo los reciben.';

-- El historial. Las foráneas de patron_verificacion comprueban de paso que cada identificador del documento
-- exista: si alguno no, la migración se detiene en vez de dejar un par roto.
INSERT INTO patron_verificacion (k_id_verificacion, k_id_equipo_patron)
SELECT DISTINCT (v ->> 'id')::uuid, (p ->> 'id')::uuid
FROM reporte_servicio r,
     jsonb_array_elements(r.t_contenido -> 'verificaciones') v,
     jsonb_array_elements(v -> 'patrones') p;
