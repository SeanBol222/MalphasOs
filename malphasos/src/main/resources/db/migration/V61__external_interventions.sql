-- Lo que se le hizo a un equipo fuera de MalphasOS (2026-11-23).
--
-- El historial de la hoja de vida sale de los reportes cerrados desde V52, y no tiene tabla. Le faltan los
-- mantenimientos, calibraciones y demás que el equipo tuvo con otras empresas, o antes de usar MalphasOS.
-- Decidido por el usuario: una tabla para ellos, colgada del equipo del cliente, con pocos campos para que
-- sea rápido de meter —cuándo, qué tipo, qué se hizo y qué empresa—, y opcionales cómo quedó el equipo y el
-- número del certificado o informe del tercero. Sin adjunto. La hoja de vida mezcla estas líneas con las de
-- los reportes y las ordena por fecha, y cuentan para el último servicio.
--
-- Una mal metida se retira, no se borra: b_estado_activo, como todo aquí.
--
-- QUÉ PASA CON LOS DATOS: nada. La tabla nace vacía y ninguna otra cambia.

CREATE TABLE intervencion_externa
(
    k_id_intervencion_externa uuid         NOT NULL,
    k_id_equipo_cliente       uuid         NOT NULL,
    f_realizada               date         NOT NULL,
    t_tipo_servicio           varchar(11)  NOT NULL,
    t_descripcion             varchar(500) NOT NULL,
    n_empresa                 varchar(120) NOT NULL,
    t_resultado               varchar(27),
    n_documento               varchar(80),
    f_registrada              timestamp    NOT NULL DEFAULT now(),
    b_estado_activo           boolean      NOT NULL DEFAULT true,

    CONSTRAINT "PK_intervencion_externa" PRIMARY KEY (k_id_intervencion_externa),
    CONSTRAINT "FK_intervencion_externa_equipo" FOREIGN KEY (k_id_equipo_cliente)
        REFERENCES equipo_cliente (k_id_equipo_cliente),
    -- Los tres de un reporte, más OTRO: una inspección, una actualización de software, un traslado.
    CONSTRAINT "CHK_intervencion_externa_tipo"
        CHECK (t_tipo_servicio IN ('PREVENTIVO', 'CORRECTIVO', 'CALIBRACION', 'OTRO')),
    CONSTRAINT "CHK_intervencion_externa_resultado"
        CHECK (t_resultado IS NULL
            OR t_resultado IN ('OPERATIVO', 'OPERATIVO_CON_RESTRICCIONES', 'FUERA_DE_SERVICIO')),
    CONSTRAINT "CHK_intervencion_externa_descripcion" CHECK (length(btrim(t_descripcion)) > 0),
    CONSTRAINT "CHK_intervencion_externa_empresa" CHECK (length(btrim(n_empresa)) > 0)
    -- Que la fecha no sea futura no va aquí: now() no es inmutable y un CHECK no puede usarla. Lo comprueba
    -- el dominio.
);

-- El historial de un equipo, por fecha: es como lo lee la hoja de vida. Encabeza la foránea, que el
-- proyecto exige indexada (V42).
CREATE INDEX "IX_intervencion_externa_equipo_fecha" ON intervencion_externa (k_id_equipo_cliente, f_realizada);

COMMENT ON TABLE intervencion_externa IS
    'Lo que se le hizo a un equipo fuera de MalphasOS —otra empresa, o antes de usarlo—: una línea más de su historial, que la hoja de vida ordena por fecha con las de los reportes.';
COMMENT ON COLUMN intervencion_externa.f_realizada IS 'El día en que se hizo, no el día en que se registró.';
COMMENT ON COLUMN intervencion_externa.n_empresa IS 'Quién lo hizo: la empresa externa.';
COMMENT ON COLUMN intervencion_externa.t_resultado IS 'Cómo quedó el equipo, si se sabe.';
COMMENT ON COLUMN intervencion_externa.n_documento IS 'El número del certificado o del informe del tercero, si lo hay.';
COMMENT ON COLUMN intervencion_externa.f_registrada IS 'Cuándo se metió en MalphasOS.';
