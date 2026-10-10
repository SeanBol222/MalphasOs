-- V22: los pasos del protocolo dentro de cada reporte de servicio (RF-13).
--
-- Decisiones del usuario del 2026-10-10. Al abrir el reporte de una orden PREVENTIVA se copian aqui los
-- protocolos que el tipo del equipo tiene asignados, en su orden, y en esta copia se marca cada uno
-- HECHO o NO_APLICA. Se copian -nombre, descripcion e indicacion- y no se leen del catalogo, porque
-- editar un protocolo despues no puede cambiar lo que dice un reporte viejo. Ver
-- [[congelar-una-referencia-historica]] en SecondBrain.
--
-- La foranea al protocolo se conserva aunque el texto se copie: dice de que protocolo salio cada paso,
-- y un protocolo nunca se borra, solo se retira, de modo que la foranea no congela nada.

CREATE TABLE paso_reporte
(
    k_id_paso_reporte     uuid         NOT NULL,
    k_id_reporte_servicio uuid         NOT NULL,
    -- La prioridad que el protocolo tenia en el tipo el dia que se abrio el reporte.
    i_orden               integer      NOT NULL,
    k_id_protocolo        uuid         NOT NULL,
    n_nombre              varchar(120) NOT NULL,
    t_descripcion         varchar(500) NOT NULL,
    t_indicacion          varchar(300) NULL,
    t_estado_paso         varchar(10)  NOT NULL DEFAULT 'PENDIENTE',
    t_nota                varchar(300) NULL,

    CONSTRAINT "PK_paso_reporte" PRIMARY KEY (k_id_paso_reporte),
    CONSTRAINT "FK_paso_reporte_reporte" FOREIGN KEY (k_id_reporte_servicio)
        REFERENCES reporte_servicio (k_id_reporte_servicio),
    CONSTRAINT "FK_paso_reporte_protocolo" FOREIGN KEY (k_id_protocolo)
        REFERENCES protocolo (k_id_protocolo),
    CONSTRAINT "UQ_paso_reporte_orden" UNIQUE (k_id_reporte_servicio, i_orden),
    CONSTRAINT "CHK_paso_reporte_orden" CHECK (i_orden > 0),
    CONSTRAINT "CHK_paso_reporte_estado" CHECK (t_estado_paso IN ('PENDIENTE', 'HECHO', 'NO_APLICA')),
    CONSTRAINT "CHK_paso_reporte_nota" CHECK (t_nota IS NULL OR btrim(t_nota) <> ''),
    -- Decidido por el usuario: saltarse un paso del protocolo queda justificado. Con CASE y no con OR,
    -- porque un CHECK se satisface con NULL y la nota nula es justo lo que se quiere prohibir.
    CONSTRAINT "CHK_paso_reporte_no_aplica_con_nota"
        CHECK (CASE WHEN t_estado_paso = 'NO_APLICA' THEN t_nota IS NOT NULL ELSE true END)
);

COMMENT ON TABLE paso_reporte IS
    'Un protocolo del preventivo copiado en el reporte al abrirlo, y si se hizo o no aplico (RF-13).';

-- Y LA REGLA DE CIERRE SE PARTE EN DOS. V9 exigia procedimientos al cerrar. Desde aqui, un reporte con
-- pasos se cierra con todos marcados y el texto -que la pantalla llama «trabajo adicional»- es
-- opcional; uno sin pasos lo sigue exigiendo. Esa condicion mira otra tabla, y un CHECK no puede, asi
-- que pasa al dominio (ServiceReport.finish). Lo que el esquema sigue exigiendo es lo que no depende de
-- nada mas: resultado y fecha al cerrar, y fecha nula en borrador.
ALTER TABLE reporte_servicio
    DROP CONSTRAINT "CHK_reporte_servicio_cierre",
    ADD CONSTRAINT "CHK_reporte_servicio_cierre"
        CHECK (CASE
                   WHEN t_estado_reporte = 'FINALIZADO'
                       THEN t_resultado IS NOT NULL
                           AND t_finalizado IS NOT NULL
                   ELSE t_finalizado IS NULL
               END);
