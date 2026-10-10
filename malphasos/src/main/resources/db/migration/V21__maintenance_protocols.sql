-- V21: los protocolos de mantenimiento preventivo (RF-14), y su asignacion a los tipos de equipo.
--
-- Decisiones del usuario del 2026-10-10, que cambiaron el modelo que se tenia pensado: un protocolo NO
-- es la lista entera de un tipo de equipo, es cada actividad -«Limpieza y desinfeccion»,
-- «Verificacion del sistema electrico»-, que se repite en muchos tipos y por eso vive una sola vez y se
-- ASIGNA. Cada tipo fija su orden con una prioridad que decide el usuario, y puede anadir a la
-- actividad una indicacion propia.
--
--   protocolo ──< protocolo_tipo_equipo >── tipo_equipo
--
-- El original tenia una tabla protocolo_mantenimiento con un solo booleano, «si el protocolo se
-- ejecuto», colgando del reporte: era una casilla, no un protocolo. No se reutiliza nada de ella.

CREATE TABLE protocolo
(
    k_id_protocolo  uuid         NOT NULL,
    n_nombre        varchar(120) NOT NULL,
    -- El nombre sin mayusculas, sin tildes y con los espacios colapsados: «Limpieza y desinfección» y
    -- «limpieza  y DESINFECCION» son el mismo protocolo. La calcula la aplicacion (Protocol.clave) y
    -- no una funcion de la base, porque unaccent no es IMMUTABLE y no puede ir en un indice.
    n_clave         varchar(120) NOT NULL,
    t_descripcion   varchar(500) NOT NULL,
    b_estado_activo boolean      NOT NULL DEFAULT true,

    CONSTRAINT "PK_protocolo" PRIMARY KEY (k_id_protocolo),
    CONSTRAINT "CHK_protocolo_nombre" CHECK (btrim(n_nombre) <> ''),
    CONSTRAINT "CHK_protocolo_descripcion" CHECK (btrim(t_descripcion) <> ''),
    CONSTRAINT "CHK_protocolo_clave" CHECK (n_clave = lower(n_clave) AND btrim(n_clave) <> '')
);

-- Pedido expresamente: que no sea facil repetir protocolos. La pantalla avisa de los parecidos; esto es
-- lo que lo garantiza con dos altas a la vez. Solo entre los vigentes: uno retirado no reserva su nombre.
CREATE UNIQUE INDEX "UQ_protocolo_clave_activo"
    ON protocolo (n_clave)
    WHERE b_estado_activo;

COMMENT ON TABLE protocolo IS
    'Una actividad de mantenimiento preventivo, reutilizable entre tipos de equipo (RF-14).';

CREATE TABLE protocolo_tipo_equipo
(
    k_id_tipo_equipo uuid         NOT NULL,
    k_id_protocolo   uuid         NOT NULL,
    -- 1, 2, 3... seguidas y sin huecos entre las vigentes de un tipo. Lo que el esquema puede
    -- expresar es que no se repitan y que sean positivas; que no haya huecos lo garantiza la
    -- aplicacion, que deriva la prioridad de la posicion en la lista que se le manda.
    i_prioridad      smallint     NOT NULL,
    t_indicacion     varchar(300) NULL,
    b_estado_activo  boolean      NOT NULL DEFAULT true,

    CONSTRAINT "PK_protocolo_tipo_equipo" PRIMARY KEY (k_id_tipo_equipo, k_id_protocolo),
    CONSTRAINT "FK_protocolo_tipo_equipo_tipo" FOREIGN KEY (k_id_tipo_equipo)
        REFERENCES tipo_equipo (k_id_tipo_equipo),
    CONSTRAINT "FK_protocolo_tipo_equipo_protocolo" FOREIGN KEY (k_id_protocolo)
        REFERENCES protocolo (k_id_protocolo),
    CONSTRAINT "CHK_protocolo_tipo_equipo_prioridad" CHECK (i_prioridad > 0),
    -- Una indicacion en blanco no indica nada: o tiene texto, o es nula.
    CONSTRAINT "CHK_protocolo_tipo_equipo_indicacion" CHECK (t_indicacion IS NULL OR btrim(t_indicacion) <> '')
);

-- Parcial, y por eso no se puede declarar diferida: reordenar es retirar todas las filas del tipo y
-- volver a activarlas con su prioridad nueva, en dos pasos. Ver ProtocolAssignmentPersistenceAdapter.
CREATE UNIQUE INDEX "UQ_protocolo_tipo_equipo_prioridad_activa"
    ON protocolo_tipo_equipo (k_id_tipo_equipo, i_prioridad)
    WHERE b_estado_activo;

CREATE INDEX "IX_protocolo_tipo_equipo_protocolo" ON protocolo_tipo_equipo (k_id_protocolo);

COMMENT ON TABLE protocolo_tipo_equipo IS
    'Que protocolos lleva el preventivo de un tipo de equipo, en que orden y con que indicacion propia.';
