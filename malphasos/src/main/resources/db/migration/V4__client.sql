-- Modulo de clientes: la organizacion que contrata el mantenimiento, sus sedes, las
-- areas de servicio dentro de cada sede, y las personas que la representan.
--
--   cliente ─┬─ correo_cliente / telefono_cliente
--            ├─ representante_legal ── persona     (N:M)
--            └─ sede ── area_servicio ─┬─ encargado ── persona  (1:1 por identidad compartida)
--                                      └─ (equipo_cliente, aplazado)
--
-- equipo_cliente no entra aqui: su FK apunta a modelo, que pertenece al modulo de
-- equipos y arrastra tambien a equipo y fabricante. Llegara con esa migracion.
--
-- Se conserva la convencion de prefijos: k_ llaves, n_ nombres, t_ texto libre, b_ booleanos.

CREATE TABLE cliente
(
    k_id_cliente         uuid        NOT NULL,
    -- NIT o documento con el que se identifica al cliente. En el esquema original era
    -- la llave primaria, un varchar(11) al que apuntaban sede, correo_cliente,
    -- telefono_cliente y representante_legal. Aqui la llave es un UUID, por la
    -- convencion de V1__baseline, y el documento se conserva como llave natural:
    -- sigue siendo unico y consultable sin ser el destino de media docena de FKs.
    k_documento          varchar(11) NOT NULL,
    n_tipo_identificacion varchar(12) NOT NULL,
    n_razon_social       varchar(50) NOT NULL,
    k_id_pais            uuid        NULL,                     -- Pais de origen
    b_estado_activo      boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_cliente" PRIMARY KEY (k_id_cliente),
    CONSTRAINT "UQ_cliente_documento" UNIQUE (k_documento),
    CONSTRAINT "FK_cliente_pais" FOREIGN KEY (k_id_pais) REFERENCES pais (k_id_pais),
    -- El original lo llamaba "CHK_tipo_identifiacion", con la erre traspuesta.
    CONSTRAINT "CHK_cliente_tipo_identificacion"
        CHECK (n_tipo_identificacion IN ('NIT_juridico', 'NIT_natural', 'CC', 'CE'))
);

COMMENT ON TABLE cliente IS
    'Organizacion que contrata el mantenimiento de sus equipos.';

CREATE TABLE correo_cliente
(
    k_id_correo_cliente uuid        NOT NULL,
    n_correo_cliente    varchar(50) NOT NULL,
    -- NOT NULL, a diferencia del original: un correo sin cliente no es de nadie y no
    -- hay forma de llegar a el ni de saber si sobra.
    k_id_cliente        uuid        NOT NULL,
    b_estado_activo     boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_correo_cliente" PRIMARY KEY (k_id_correo_cliente),
    CONSTRAINT "FK_correo_cliente_cliente"
        FOREIGN KEY (k_id_cliente) REFERENCES cliente (k_id_cliente)
);

CREATE TABLE telefono_cliente
(
    k_id_telefono_cliente uuid        NOT NULL,
    n_telefono_cliente    varchar(50) NOT NULL,
    k_id_cliente          uuid        NOT NULL,
    b_estado_activo       boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_telefono_cliente" PRIMARY KEY (k_id_telefono_cliente),
    CONSTRAINT "FK_telefono_cliente_cliente"
        FOREIGN KEY (k_id_cliente) REFERENCES cliente (k_id_cliente)
);

CREATE INDEX "IX_correo_cliente_cliente" ON correo_cliente (k_id_cliente);
CREATE INDEX "IX_telefono_cliente_cliente" ON telefono_cliente (k_id_cliente);

CREATE TABLE representante_legal
(
    k_identificador uuid    NOT NULL,                          -- Persona que representa
    k_id_cliente    uuid    NOT NULL,                          -- Cliente al que representa
    b_estado_activo boolean NOT NULL DEFAULT true,

    -- Llave compuesta: la relacion es de muchos a muchos. Una persona puede representar
    -- a varios clientes y un cliente tener varios representantes. Se diferencia asi de
    -- encargado, donde la llave primaria es la de la persona y la relacion es uno a uno.
    CONSTRAINT "PK_representante_legal" PRIMARY KEY (k_identificador, k_id_cliente),
    CONSTRAINT "FK_representante_legal_persona"
        FOREIGN KEY (k_identificador) REFERENCES persona (k_identificador),
    CONSTRAINT "FK_representante_legal_cliente"
        FOREIGN KEY (k_id_cliente) REFERENCES cliente (k_id_cliente)
);

COMMENT ON TABLE representante_legal IS
    'Personas que representan legalmente a un cliente. En bolivarbioingenieria-app esta '
    'tabla existia sin una sola linea de codigo que la leyera o escribiera, de modo que el '
    'tipo de persona CEO_CLIENT no podia asociarse a ningun cliente.';

CREATE INDEX "IX_representante_legal_cliente" ON representante_legal (k_id_cliente);

CREATE TABLE sede
(
    k_id_sede       uuid        NOT NULL,
    n_nombre_sede   varchar(50) NOT NULL,
    -- La direccion se guarda descompuesta, como en el original. Se renombran las columnas
    -- para respetar la convencion de prefijos: alli eran dir_calle, dir_carrera y dir_num,
    -- las unicas del esquema con un prefijo propio.
    t_calle         varchar(50) NOT NULL,
    t_carrera       varchar(50) NOT NULL,
    t_numero        varchar(50) NOT NULL,
    k_id_cliente    uuid        NOT NULL,
    k_id_ciudad     uuid        NOT NULL,
    b_estado_activo boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_sede" PRIMARY KEY (k_id_sede),
    CONSTRAINT "FK_sede_cliente" FOREIGN KEY (k_id_cliente) REFERENCES cliente (k_id_cliente),
    CONSTRAINT "FK_sede_ciudad" FOREIGN KEY (k_id_ciudad) REFERENCES ciudad (k_id_ciudad),
    -- Un cliente no tiene dos sedes con el mismo nombre. El original no lo impedia.
    CONSTRAINT "UQ_sede_nombre_por_cliente" UNIQUE (k_id_cliente, n_nombre_sede)
);

CREATE INDEX "IX_sede_ciudad" ON sede (k_id_ciudad);

CREATE TABLE area_servicio
(
    k_id_area_servicio uuid        NOT NULL,
    n_nombre_area      varchar(50) NOT NULL,
    k_id_sede          uuid        NOT NULL,
    b_estado_activo    boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_area_servicio" PRIMARY KEY (k_id_area_servicio),
    CONSTRAINT "FK_area_servicio_sede" FOREIGN KEY (k_id_sede) REFERENCES sede (k_id_sede),
    -- Una sede no tiene dos areas con el mismo nombre. Tampoco lo impedia el original.
    CONSTRAINT "UQ_area_nombre_por_sede" UNIQUE (k_id_sede, n_nombre_area)
);

CREATE TABLE encargado
(
    -- La llave primaria es la de la persona: un encargado ES una persona, no la
    -- referencia. Sin persona no hay encargado, y no puede haber dos encargados sobre
    -- la misma persona.
    k_identificador    uuid        NOT NULL,
    t_tipo_encargado   varchar(16) NOT NULL,
    k_id_sede          uuid        NULL,
    k_id_area_servicio uuid        NULL,
    b_estado_activo    boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_encargado" PRIMARY KEY (k_identificador),
    CONSTRAINT "FK_encargado_persona"
        FOREIGN KEY (k_identificador) REFERENCES persona (k_identificador),
    CONSTRAINT "FK_encargado_sede" FOREIGN KEY (k_id_sede) REFERENCES sede (k_id_sede),
    CONSTRAINT "FK_encargado_area_servicio"
        FOREIGN KEY (k_id_area_servicio) REFERENCES area_servicio (k_id_area_servicio),

    -- El original lo llamaba "CHK_tipo_encagado", sin la erre.
    CONSTRAINT "CHK_encargado_tipo"
        CHECK (t_tipo_encargado IN ('HEADQUARTER', 'SERVICE_AREA')),

    -- El tipo declara de que se encarga, y esta restriccion obliga a que el dato lo
    -- respalde. El original dejaba ambas columnas anulables sin nada que las atara al
    -- tipo, de modo que cabia un HEADQUARTER sin sede, con area, con las dos o con
    -- ninguna: el tipo decia una cosa y las FKs otra.
    CONSTRAINT "CHK_encargado_asignacion"
        CHECK ((t_tipo_encargado = 'HEADQUARTER'
                    AND k_id_sede IS NOT NULL AND k_id_area_servicio IS NULL)
            OR (t_tipo_encargado = 'SERVICE_AREA'
                    AND k_id_area_servicio IS NOT NULL AND k_id_sede IS NULL))
);

COMMENT ON TABLE encargado IS
    'Persona responsable de una sede o de un area de servicio de un cliente. Comparte '
    'llave primaria con persona: un encargado es una persona con ese rol.';

CREATE INDEX "IX_encargado_sede" ON encargado (k_id_sede);
CREATE INDEX "IX_encargado_area_servicio" ON encargado (k_id_area_servicio);
