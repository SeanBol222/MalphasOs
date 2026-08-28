-- Modulo de personas: personas fisicas del sistema (ingenieros, encargados de sede y
-- representantes legales de los clientes) con sus correos y telefonos.
--
-- Se conserva la convencion de prefijos del esquema original: k_ llaves, n_ nombres,
-- t_ texto libre, b_ booleanos.

CREATE TABLE persona
(
    k_identificador        uuid        NOT NULL,               -- Identificador interno de la persona
    k_cedula               varchar(10) NOT NULL,               -- Cedula, identificador unico frente al Estado
    n_primer_nombre        varchar(50) NOT NULL,
    n_segundo_nombre       varchar(50) NULL,
    n_primer_apellido      varchar(50) NOT NULL,
    n_segundo_apellido     varchar(50) NULL,
    t_tipo_persona         varchar(19) NOT NULL,               -- Rol principal
    t_segundo_tipo_persona varchar(19) NULL,                   -- Rol secundario, cuando cumple dos funciones
    -- Borrado logico: conserva el historial sin eliminar registros.
    -- En el esquema original esta columna estaba declarada como varchar(50) con DEFAULT true,
    -- pese a ser booleana y estar correctamente tipada en correo_persona y telefono_persona.
    b_estado_activo        boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_persona" PRIMARY KEY (k_identificador),
    CONSTRAINT "UQ_cedula" UNIQUE (k_cedula),

    -- El original combinaba ambas reglas en un unico CHECK unido por AND, de modo que al fallar
    -- no era posible saber cual de las dos se habia incumplido. Separadas, el mensaje de error
    -- identifica la causa.
    CONSTRAINT "CHK_tipo_persona"
        CHECK (t_tipo_persona IN ('ENGINEER', 'MANAGER', 'CEO_CLIENT', 'ADMIN', 'SUPER_ADMIN')),
    CONSTRAINT "CHK_segundo_tipo_persona"
        CHECK (t_segundo_tipo_persona IS NULL OR t_segundo_tipo_persona = 'MANAGER')
);

COMMENT ON TABLE persona IS
    'Personas fisicas del sistema. Las reglas de combinacion entre rol principal y secundario se '
    'validan en el dominio (Person.validarRoles), no aqui: el esquema original definia la funcion '
    'validar_roles_persona() pero nunca creo el trigger que la invocara, de modo que esas reglas '
    'jamas se ejecutaron.';

CREATE TABLE correo_persona
(
    k_id_correo_persona uuid        NOT NULL,
    n_correo_persona    varchar(50) NOT NULL,
    k_identificador     uuid        NULL,                      -- Persona a la que pertenece el correo
    b_estado_activo     boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_correo_persona" PRIMARY KEY (k_id_correo_persona),
    CONSTRAINT "FK_correo_persona" FOREIGN KEY (k_identificador)
        REFERENCES persona (k_identificador) ON DELETE NO ACTION ON UPDATE NO ACTION
);

CREATE INDEX "IXFK_correo_persona" ON correo_persona (k_identificador ASC);

CREATE TABLE telefono_persona
(
    k_id_telefono_persona uuid        NOT NULL,
    n_telefono_persona    varchar(10) NOT NULL,
    k_identificador       uuid        NULL,                    -- Persona a la que pertenece el telefono
    b_estado_activo       boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_telefono_persona" PRIMARY KEY (k_id_telefono_persona),
    CONSTRAINT "FK_telefono_persona" FOREIGN KEY (k_identificador)
        REFERENCES persona (k_identificador) ON DELETE NO ACTION ON UPDATE NO ACTION
);

CREATE INDEX "IXFK_telefono_persona" ON telefono_persona (k_identificador ASC);
