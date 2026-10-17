-- V31: cómo llegó el equipo y a quién se entregó (2026-10-16).
--
-- Los informes que la empresa entrega hoy dicen en su primera página tres cosas que el reporte no
-- guardaba, y las tres las decidió el usuario así:
--
--   1. Las verificaciones al ingreso: dos filas fijas en todo reporte —estado de la estructura física y
--      estado operativo del equipo—, cada una «no aplica», conforme o no conforme, con su observación.
--      Nula mientras no se registra: un reporte recién abierto no ha mirado el equipo todavía.
--   2. Los accesorios con que llegó, una lista libre por reporte: nombre, conforme o no, observación.
--   3. Quién lo recibe al entregarlo: nombre, cédula y cargo, en texto, porque muchas veces no está
--      registrado en el sistema —la jefa de enfermería de turno—.
--
-- «No conforme» exige observación: sin ella el reporte dice que algo estaba mal y no qué. Con CASE y no
-- con OR, por la trampa de V9: un CHECK se satisface con NULL.

ALTER TABLE reporte_servicio
    ADD COLUMN t_ingreso_fisico                varchar(20)  NULL,
    ADD COLUMN t_ingreso_fisico_observacion    varchar(500) NULL,
    ADD COLUMN t_ingreso_operativo             varchar(20)  NULL,
    ADD COLUMN t_ingreso_operativo_observacion varchar(500) NULL,
    ADD COLUMN n_recibe_nombre                 varchar(120) NULL,
    ADD COLUMN k_recibe_cedula                 varchar(20)  NULL,
    ADD COLUMN n_recibe_cargo                  varchar(80)  NULL,
    ADD CONSTRAINT "CHK_reporte_servicio_ingreso_fisico"
        CHECK (CASE
                   WHEN t_ingreso_fisico IS NULL THEN t_ingreso_fisico_observacion IS NULL
                   WHEN t_ingreso_fisico = 'NO_CONFORME' THEN t_ingreso_fisico_observacion IS NOT NULL
                   ELSE t_ingreso_fisico IN ('CONFORME', 'NO_APLICA')
               END),
    ADD CONSTRAINT "CHK_reporte_servicio_ingreso_operativo"
        CHECK (CASE
                   WHEN t_ingreso_operativo IS NULL THEN t_ingreso_operativo_observacion IS NULL
                   WHEN t_ingreso_operativo = 'NO_CONFORME' THEN t_ingreso_operativo_observacion IS NOT NULL
                   ELSE t_ingreso_operativo IN ('CONFORME', 'NO_APLICA')
               END),
    ADD CONSTRAINT "CHK_reporte_servicio_ingreso_observaciones"
        CHECK ((t_ingreso_fisico_observacion IS NULL OR btrim(t_ingreso_fisico_observacion) <> '')
            AND (t_ingreso_operativo_observacion IS NULL OR btrim(t_ingreso_operativo_observacion) <> '')),
    -- Cédula y cargo sin nombre no dicen quién recibió.
    ADD CONSTRAINT "CHK_reporte_servicio_recibe"
        CHECK (CASE
                   WHEN n_recibe_nombre IS NULL THEN k_recibe_cedula IS NULL AND n_recibe_cargo IS NULL
                   ELSE btrim(n_recibe_nombre) <> ''
                       AND (k_recibe_cedula IS NULL OR btrim(k_recibe_cedula) <> '')
                       AND (n_recibe_cargo IS NULL OR btrim(n_recibe_cargo) <> '')
               END);

COMMENT ON COLUMN reporte_servicio.t_ingreso_fisico IS
    'Estado de la estructura física al recibir el equipo: CONFORME, NO_CONFORME o NO_APLICA. Nulo: sin registrar.';
COMMENT ON COLUMN reporte_servicio.t_ingreso_operativo IS
    'Estado operativo al recibir el equipo: CONFORME, NO_CONFORME o NO_APLICA. Nulo: sin registrar.';
COMMENT ON COLUMN reporte_servicio.n_recibe_nombre IS
    'Quién recibió el equipo al entregarlo. Texto: no tiene por qué estar registrado en el sistema.';

-- Los accesorios se sustituyen enteros al registrarlos, como las lecturas: los anteriores se retiran y
-- no se borran. Sin índice único sobre el orden, a propósito: retirar una lista e insertar la nueva en
-- el mismo vaciado chocaría con él —la trampa de Hibernate de V9—, y el orden lo garantiza el dominio.

CREATE TABLE accesorio_reporte
(
    k_id_accesorio_reporte uuid         NOT NULL,
    k_id_reporte_servicio  uuid         NOT NULL,
    i_orden                integer      NOT NULL,
    n_nombre               varchar(120) NOT NULL,
    b_conforme             boolean      NOT NULL,
    t_observacion          varchar(500) NULL,
    b_estado_activo        boolean      NOT NULL DEFAULT true,

    CONSTRAINT "PK_accesorio_reporte" PRIMARY KEY (k_id_accesorio_reporte),
    CONSTRAINT "FK_accesorio_reporte_reporte"
        FOREIGN KEY (k_id_reporte_servicio) REFERENCES reporte_servicio (k_id_reporte_servicio),
    CONSTRAINT "CHK_accesorio_reporte_nombre" CHECK (btrim(n_nombre) <> ''),
    CONSTRAINT "CHK_accesorio_reporte_orden" CHECK (i_orden > 0),
    CONSTRAINT "CHK_accesorio_reporte_observacion"
        CHECK (CASE
                   WHEN b_conforme THEN t_observacion IS NULL OR btrim(t_observacion) <> ''
                   ELSE t_observacion IS NOT NULL AND btrim(t_observacion) <> ''
               END)
);

COMMENT ON TABLE accesorio_reporte IS
    'Los accesorios con que llegó el equipo al servicio, en su orden. Uno no conforme dice por qué.';
