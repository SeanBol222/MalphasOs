-- Modulo de ordenes de trabajo: el mantenimiento que se va a prestar, a que cliente,
-- en que sede, con que periodicidad y sobre que equipos.
--
--   cliente ─┐
--            ├──> orden_trabajo ──> orden_trabajo_equipo ──> equipo_cliente
--   sede ────┘         │                     └───────────────> area_servicio
--                      └──> persona (ingeniero asignado, anulable)
--
-- La orden dice QUE se va a hacer. Lo que se hizo sera el reporte de servicio, que
-- todavia no existe y colgara de aqui.
--
-- Tres decisiones que se apartan del esquema original, donde orden_trabajo no tenia
-- cliente, ni sede, ni tipo de servicio, ni vinculo alguno con los equipos -- de hecho
-- ninguna restriccion de aquel esquema referenciaba a orden_trabajo, y el unico intento
-- de hacerlo, reporte_servicio.k_id_orden_trabajo, era un varchar(10) contra un uuid:
--
--   1. La orden guarda cliente y sede propios, aunque ambos sean deducibles por los
--      equipos. Es redundancia deliberada: un equipo puede trasladarse a otra sede del
--      mismo cliente y, sin estas columnas, una orden ya ejecutada cambiaria de sede
--      retroactivamente. Ademas hace comprobable "una orden pertenece a un solo
--      cliente" y evita que la consulta mas frecuente sea un join de cuatro tablas.
--   2. Cada equipo de la orden guarda el area de servicio en la que estaba al
--      seleccionarlo, por la misma razon: un traslado posterior no debe reescribir
--      donde se presto el servicio.
--   3. No se guardan las areas seleccionadas en el formulario. Quedan implicitas en los
--      equipos elegidos, y mantener dos listas que han de concordar es trabajo sin
--      ganancia.

-- Necesaria para la clave foranea compuesta de orden_trabajo. k_id_sede ya es unica por
-- ser llave primaria; esta restriccion no anade una regla nueva sobre sede, solo expone
-- el par (sede, cliente) como destino referenciable. Se anade aqui y no en V4 porque una
-- migracion ya aplicada no se edita.
ALTER TABLE sede
    ADD CONSTRAINT "UQ_sede_identidad_con_cliente" UNIQUE (k_id_sede, k_id_cliente);

CREATE TABLE orden_trabajo
(
    k_id_orden_trabajo    uuid        NOT NULL,
    k_id_cliente          uuid        NOT NULL,
    k_id_sede             uuid        NOT NULL,
    -- Dia para el que se programa el mantenimiento. El original la declaraba timestamp
    -- y la documentaba como "la fecha en la que se creo la orden": dos cosas distintas.
    -- Aqui es una fecha, sin hora, y es la del servicio, no la del registro.
    f_fecha_mantenimiento date        NOT NULL,
    n_periodicidad        varchar(10) NOT NULL,
    t_tipo_servicio       varchar(11) NOT NULL,
    t_estado_ejecucion    varchar(12) NOT NULL DEFAULT 'CREADA',
    -- Ingeniero asignado. Anulable a proposito: una orden se crea antes de asignarse, y
    -- por eso asignar es una autoridad aparte de crear (work-order.assign).
    k_identificador       uuid        NULL,
    b_estado_activo       boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_orden_trabajo" PRIMARY KEY (k_id_orden_trabajo),
    CONSTRAINT "FK_orden_trabajo_cliente"
        FOREIGN KEY (k_id_cliente) REFERENCES cliente (k_id_cliente),
    -- Compuesta contra "UQ_sede_identidad_con_cliente": no basta con que la sede exista,
    -- tiene que ser una sede de ESE cliente. Es la unica de las reglas cruzadas de este
    -- modulo que el esquema puede sostener por si solo.
    CONSTRAINT "FK_orden_trabajo_sede_del_cliente"
        FOREIGN KEY (k_id_sede, k_id_cliente) REFERENCES sede (k_id_sede, k_id_cliente),
    CONSTRAINT "FK_orden_trabajo_persona"
        FOREIGN KEY (k_identificador) REFERENCES persona (k_identificador),

    -- Las tres enumeraciones van en espanol, como el resto del vocabulario propio del
    -- dominio (NIT_juridico, patron_constante). El original tradujo "semestral" a
    -- BIANNUAL, palabra que en ingles significa a la vez "dos veces al ano" y "cada dos
    -- anos"; dejarlas en espanol evita repetir el problema.
    CONSTRAINT "CHK_orden_trabajo_periodicidad"
        CHECK (n_periodicidad IN ('MENSUAL', 'TRIMESTRAL', 'SEMESTRAL', 'ANUAL')),
    -- El tipo de servicio no existia en el original. Sale del vocabulario de la ERS:
    -- mantenimiento preventivo, mantenimiento correctivo y calibracion.
    CONSTRAINT "CHK_orden_trabajo_tipo_servicio"
        CHECK (t_tipo_servicio IN ('PREVENTIVO', 'CORRECTIVO', 'CALIBRACION')),
    CONSTRAINT "CHK_orden_trabajo_estado_ejecucion"
        CHECK (t_estado_ejecucion IN ('CREADA', 'EN_EJECUCION', 'EJECUTADA'))
);

COMMENT ON TABLE orden_trabajo IS
    'Mantenimiento programado para una sede de un cliente, sobre un conjunto de equipos. '
    'Dice que se va a hacer; lo que se hizo sera el reporte de servicio.';

COMMENT ON COLUMN orden_trabajo.k_id_cliente IS
    'Cliente al que se presta el servicio. Redundante frente a los equipos de la orden, '
    'y a proposito: congela el destinatario aunque los equipos se trasladen despues.';

COMMENT ON COLUMN orden_trabajo.k_id_sede IS
    'Sede donde se presta el servicio, congelada al crear la orden por la misma razon.';

COMMENT ON COLUMN orden_trabajo.k_identificador IS
    'Ingeniero asignado. Nulo mientras la orden esta creada pero sin asignar.';

CREATE INDEX "IX_orden_trabajo_cliente" ON orden_trabajo (k_id_cliente);
CREATE INDEX "IX_orden_trabajo_sede" ON orden_trabajo (k_id_sede);
CREATE INDEX "IX_orden_trabajo_persona" ON orden_trabajo (k_identificador);
CREATE INDEX "IX_orden_trabajo_fecha" ON orden_trabajo (f_fecha_mantenimiento);

CREATE TABLE orden_trabajo_equipo
(
    k_id_orden_trabajo  uuid    NOT NULL,
    k_id_equipo_cliente uuid    NOT NULL,
    -- Area en la que estaba el equipo cuando se selecciono. No es la actual: si manana
    -- el equipo se traslada, el reporte de esta orden debe seguir senalando el area
    -- donde se presto el servicio. Por eso NO hay clave foranea compuesta contra
    -- equipo_cliente (k_id_equipo_cliente, k_id_area_servicio): la habria comprobado al
    -- insertar, pero tambien al trasladar el equipo, y el traslado -- que es legitimo --
    -- fallaria por culpa de las ordenes viejas. Que el area sea la del equipo en el
    -- momento de anadirlo lo comprueba el servicio.
    k_id_area_servicio  uuid    NOT NULL,
    b_estado_activo     boolean NOT NULL DEFAULT true,

    -- Llave compuesta: el mismo equipo no se lista dos veces en la misma orden. Retirarlo
    -- es apagar b_estado_activo, y volver a anadirlo reactiva la misma fila.
    CONSTRAINT "PK_orden_trabajo_equipo" PRIMARY KEY (k_id_orden_trabajo, k_id_equipo_cliente),
    CONSTRAINT "FK_orden_trabajo_equipo_orden"
        FOREIGN KEY (k_id_orden_trabajo) REFERENCES orden_trabajo (k_id_orden_trabajo),
    CONSTRAINT "FK_orden_trabajo_equipo_equipo_cliente"
        FOREIGN KEY (k_id_equipo_cliente) REFERENCES equipo_cliente (k_id_equipo_cliente),
    CONSTRAINT "FK_orden_trabajo_equipo_area_servicio"
        FOREIGN KEY (k_id_area_servicio) REFERENCES area_servicio (k_id_area_servicio)
);

COMMENT ON TABLE orden_trabajo_equipo IS
    'Equipos incluidos en una orden de trabajo, con el area de servicio en la que estaban '
    'al seleccionarlos.';

COMMENT ON COLUMN orden_trabajo_equipo.k_id_area_servicio IS
    'Area donde estaba el equipo al anadirlo a la orden, no donde esta hoy.';

-- El historial de un equipo -- que ordenes lo han incluido -- es la consulta que
-- justifica este indice; la del sentido contrario la resuelve la llave primaria.
CREATE INDEX "IX_orden_trabajo_equipo_equipo_cliente" ON orden_trabajo_equipo (k_id_equipo_cliente);
CREATE INDEX "IX_orden_trabajo_equipo_area_servicio" ON orden_trabajo_equipo (k_id_area_servicio);
