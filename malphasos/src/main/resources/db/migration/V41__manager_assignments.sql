-- V41: un encargado responde por varias sedes y áreas (2026-10-27).
--
-- Decidido por el usuario, sobre los diagramas de Documentation/DatabaseDesign/V41-encargados/. Hasta
-- aquí la llave primaria de encargado era la de la persona, de modo que cada persona tenía UNA fila y
-- por eso una sola sede o una sola área, y el usuario confirmó que en sus clientes un encargado cubre
-- varias. Se eligieron dos tablas puente con llave compuesta en lugar de una fila por asignación con
-- identificador propio.
--
-- LOS DOS CHECK DE V4 SOBRAN, Y NO SE PIERDE NADA: CHK_encargado_tipo y CHK_encargado_asignacion
-- ataban el tipo a la columna llena. Ahora el tipo es la tabla, y no hay columna que pueda contradecirlo.
--
-- LA LLAVE COMPUESTA IMPIDE REPETIR una misma sede o área para una persona. Quitar una asignación apaga
-- b_estado_activo, y volver a darla lo enciende en la misma fila: nada se borra, como en el resto del
-- esquema.
--
-- LO QUE EL ESQUEMA NO COMPRUEBA, y vive en el servicio: que todas las asignaciones activas de una
-- persona sean del mismo cliente. La sede apunta a su cliente y el área llega a él por su sede, en dos
-- tablas distintas; atarlo aquí exigiría una tabla más por encima, y no es el modelo elegido. Estar a
-- cargo de una sede y de un área de esa misma sede a la vez SÍ se permite, decidido por el usuario. Y
-- no hay fecha de inicio: nada la pide ni la muestra.

CREATE TABLE encargado_sede
(
    k_identificador uuid    NOT NULL,
    k_id_sede       uuid    NOT NULL,
    b_estado_activo boolean NOT NULL DEFAULT true,

    CONSTRAINT "PK_encargado_sede" PRIMARY KEY (k_identificador, k_id_sede),
    CONSTRAINT "FK_encargado_sede_persona"
        FOREIGN KEY (k_identificador) REFERENCES persona (k_identificador),
    CONSTRAINT "FK_encargado_sede_sede" FOREIGN KEY (k_id_sede) REFERENCES sede (k_id_sede)
);

COMMENT ON TABLE encargado_sede IS
    'Una persona a cargo de una sede de un cliente. Una persona puede estarlo de varias sedes, todas del '
    'mismo cliente, y esa regla la comprueba el servicio.';

-- La llave ya indexa por persona; esta es la consulta al revés, «quién responde por esta sede».
CREATE INDEX "IX_encargado_sede_sede" ON encargado_sede (k_id_sede);

CREATE TABLE encargado_area
(
    k_identificador    uuid    NOT NULL,
    k_id_area_servicio uuid    NOT NULL,
    b_estado_activo    boolean NOT NULL DEFAULT true,

    CONSTRAINT "PK_encargado_area" PRIMARY KEY (k_identificador, k_id_area_servicio),
    CONSTRAINT "FK_encargado_area_persona"
        FOREIGN KEY (k_identificador) REFERENCES persona (k_identificador),
    CONSTRAINT "FK_encargado_area_area_servicio"
        FOREIGN KEY (k_id_area_servicio) REFERENCES area_servicio (k_id_area_servicio)
);

COMMENT ON TABLE encargado_area IS
    'Una persona a cargo de un área de servicio. Una persona puede estarlo de varias áreas, todas del mismo '
    'cliente, y esa regla la comprueba el servicio.';

CREATE INDEX "IX_encargado_area_area_servicio" ON encargado_area (k_id_area_servicio);

-- Los datos: cada fila de encargado era una asignación, y su tipo dice a qué tabla va. El CHECK de V4
-- garantiza que la columna que se lee está llena.
INSERT INTO encargado_sede (k_identificador, k_id_sede, b_estado_activo)
SELECT k_identificador, k_id_sede, b_estado_activo
FROM encargado
WHERE t_tipo_encargado = 'HEADQUARTER';

INSERT INTO encargado_area (k_identificador, k_id_area_servicio, b_estado_activo)
SELECT k_identificador, k_id_area_servicio, b_estado_activo
FROM encargado
WHERE t_tipo_encargado = 'SERVICE_AREA';

DROP TABLE encargado;
