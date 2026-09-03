-- Catalogo de equipos: quien los fabrica, de que tipo son, que modelos existen, y
-- que unidades concretas tiene cada cliente.
--
--   fabricante ──┐
--                ├──> modelo ──> equipo_cliente ──> area_servicio
--   tipo_equipo ─┤
--   marca ───────┘  (via equipo)
--
-- La tabla "equipo" no guarda un equipo: no tiene un solo atributo propio mas alla
-- de sus dos claves foraneas. Es la asociacion "esta marca fabrica este tipo de
-- equipo". El modelo concreto, con su registro INVIMA, es "modelo"; la unidad
-- fisica que posee un cliente es "equipo_cliente". Se conserva el nombre del
-- esquema original para no divergir del sistema del que se migra.
--
-- Con equipo_cliente se cierra la unica pieza que V4__client.sql dejo pendiente.

CREATE TABLE fabricante
(
    k_id_fabricante     uuid        NOT NULL,
    n_nombre_fabricante varchar(50) NOT NULL,
    k_id_pais           uuid        NULL,                      -- Pais de origen
    b_estado_activo     boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_fabricante" PRIMARY KEY (k_id_fabricante),
    CONSTRAINT "FK_fabricante_pais" FOREIGN KEY (k_id_pais) REFERENCES pais (k_id_pais),
    -- El original no impedia dos fabricantes con el mismo nombre.
    CONSTRAINT "UQ_fabricante_nombre" UNIQUE (n_nombre_fabricante)
);

COMMENT ON TABLE fabricante IS 'Empresa que produce modelos de equipos.';

CREATE TABLE marca
(
    k_id_marca      uuid        NOT NULL,
    -- NOT NULL, a diferencia del original: alli la columna era anulable y cabia una
    -- marca sin nombre, que es lo unico que una marca tiene.
    n_nombre_marca  varchar(50) NOT NULL,
    b_estado_activo boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_marca" PRIMARY KEY (k_id_marca),
    CONSTRAINT "UQ_marca_nombre" UNIQUE (n_nombre_marca)
);

CREATE TABLE tipo_equipo
(
    k_id_tipo_equipo            uuid          NOT NULL,
    n_nombre_tipo_equipo        varchar(50)   NOT NULL,
    t_definicion_tecnica        varchar(250)  NOT NULL,
    t_recomendaciones_cuidado   varchar(250)  NOT NULL,
    t_tecnologia_predominante   varchar(50)   NOT NULL,
    i_voltage                   integer       NULL,
    -- numeric(8,2) y no numeric(2). En el original la escala era cero y la precision
    -- dos, de modo que el maximo era 99 y un amperaje de 2.5 A se redondeaba a 3.
    d_amperaje                  numeric(8, 2) NULL,
    -- Si a este tipo de equipo se le hace verificacion metrologica.
    b_verificable               boolean       NOT NULL DEFAULT false,
    n_tipo_verificacion         varchar(50)   NULL,
    m_valor_unitario_mantenimiento bigint     NOT NULL,
    b_estado_activo             boolean       NOT NULL DEFAULT true,

    CONSTRAINT "PK_tipo_equipo" PRIMARY KEY (k_id_tipo_equipo),
    CONSTRAINT "UQ_tipo_equipo_nombre" UNIQUE (n_nombre_tipo_equipo),

    CONSTRAINT "CHK_tipo_equipo_verificacion"
        CHECK (n_tipo_verificacion IS NULL
            OR n_tipo_verificacion IN ('patron_constante', 'equipo_constante', 'patron_equipo_variable')),

    -- El original dejaba las dos columnas sueltas: cabia un tipo marcado como
    -- verificable sin decir como se verifica, y uno no verificable con modalidad.
    CONSTRAINT "CHK_tipo_equipo_modalidad_segun_verificable"
        CHECK ((b_verificable AND n_tipo_verificacion IS NOT NULL)
            OR (NOT b_verificable AND n_tipo_verificacion IS NULL)),

    -- Un mantenimiento no cuesta menos que nada.
    CONSTRAINT "CHK_tipo_equipo_valor_mantenimiento" CHECK (m_valor_unitario_mantenimiento >= 0),
    CONSTRAINT "CHK_tipo_equipo_voltage" CHECK (i_voltage IS NULL OR i_voltage > 0),
    CONSTRAINT "CHK_tipo_equipo_amperaje" CHECK (d_amperaje IS NULL OR d_amperaje > 0)
);

COMMENT ON TABLE tipo_equipo IS
    'Categoria de equipo con sus caracteristicas tecnicas y el costo de su mantenimiento.';

CREATE TABLE equipo
(
    k_id_equipo      uuid    NOT NULL,
    k_id_tipo_equipo uuid    NOT NULL,
    k_id_marca       uuid    NOT NULL,
    b_estado_activo  boolean NOT NULL DEFAULT true,

    CONSTRAINT "PK_equipo" PRIMARY KEY (k_id_equipo),
    CONSTRAINT "FK_equipo_tipo_equipo"
        FOREIGN KEY (k_id_tipo_equipo) REFERENCES tipo_equipo (k_id_tipo_equipo),
    CONSTRAINT "FK_equipo_marca" FOREIGN KEY (k_id_marca) REFERENCES marca (k_id_marca),
    -- Es una asociacion: repetir el mismo par no significa nada. El original lo permitia.
    CONSTRAINT "UQ_equipo_tipo_marca" UNIQUE (k_id_tipo_equipo, k_id_marca)
);

COMMENT ON TABLE equipo IS
    'Asociacion entre una marca y un tipo de equipo: que tipos fabrica cada marca. '
    'No es un equipo fisico; eso es equipo_cliente.';

CREATE TABLE modelo
(
    k_id_modelo     uuid        NOT NULL,
    n_invima        varchar(50) NULL,                          -- Registro sanitario
    -- NOT NULL las dos, a diferencia del original: un modelo sin fabricante y sin
    -- equipo no pertenece a nada y no hay forma de saber que es.
    k_id_fabricante uuid        NOT NULL,
    k_id_equipo     uuid        NOT NULL,
    b_estado_activo boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_modelo" PRIMARY KEY (k_id_modelo),
    CONSTRAINT "FK_modelo_fabricante"
        FOREIGN KEY (k_id_fabricante) REFERENCES fabricante (k_id_fabricante),
    CONSTRAINT "FK_modelo_equipo" FOREIGN KEY (k_id_equipo) REFERENCES equipo (k_id_equipo),
    -- Un registro INVIMA identifica a un modelo: no se repite. Postgres admite varios
    -- nulos bajo una restriccion unica, de modo que los modelos sin registro conviven.
    CONSTRAINT "UQ_modelo_invima" UNIQUE (n_invima)
);

CREATE INDEX "IX_modelo_equipo" ON modelo (k_id_equipo);
CREATE INDEX "IX_modelo_fabricante" ON modelo (k_id_fabricante);

CREATE TABLE equipo_cliente
(
    k_id_equipo_cliente uuid        NOT NULL,
    k_serie             varchar(50) NOT NULL,                  -- Numero de serie
    n_no_inventario     varchar(50) NULL,                      -- Inventario del cliente
    f_fecha_compra      date        NULL,
    v_valor_compra      bigint      NULL,
    -- NOT NULL las dos, a diferencia del original: una unidad sin modelo no se sabe
    -- que es, y sin area de servicio no se sabe donde esta ni a quien pertenece.
    k_id_modelo         uuid        NOT NULL,
    k_id_area_servicio  uuid        NOT NULL,
    b_estado_activo     boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_equipo_cliente" PRIMARY KEY (k_id_equipo_cliente),
    CONSTRAINT "FK_equipo_cliente_modelo"
        FOREIGN KEY (k_id_modelo) REFERENCES modelo (k_id_modelo),
    CONSTRAINT "FK_equipo_cliente_area_servicio"
        FOREIGN KEY (k_id_area_servicio) REFERENCES area_servicio (k_id_area_servicio),
    -- Dos unidades del mismo modelo no comparten numero de serie.
    CONSTRAINT "UQ_equipo_cliente_serie_por_modelo" UNIQUE (k_id_modelo, k_serie),
    CONSTRAINT "CHK_equipo_cliente_valor_compra"
        CHECK (v_valor_compra IS NULL OR v_valor_compra >= 0)
);

COMMENT ON TABLE equipo_cliente IS
    'Unidad fisica que un cliente posee, situada en un area de servicio de una de sus sedes.';

CREATE INDEX "IX_equipo_cliente_area" ON equipo_cliente (k_id_area_servicio);
CREATE INDEX "IX_equipo_cliente_modelo" ON equipo_cliente (k_id_modelo);
