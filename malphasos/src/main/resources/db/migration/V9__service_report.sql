-- Modulo de reportes de servicio: lo que se HIZO sobre cada equipo de una orden de trabajo.
--
--   orden_trabajo_equipo ──> reporte_servicio ──> dato_verificacion ──> punto_verificacion
--      (que se iba a hacer)     (que se hizo)        (que se midio)        (donde se mide)
--
-- La orden dice que se va a hacer y sobre que equipos; el reporte dice, equipo por equipo, que se
-- encontro, que se hizo y como quedo. Es lo que V6 anuncio en su cabecera -- "lo que se hizo sera el
-- reporte de servicio, que todavia no existe y colgara de aqui" -- y lo que RF-09 pide: UN REPORTE
-- POR EQUIPO DE LA ORDEN, no uno por orden.
--
-- CUATRO DECISIONES, y la primera es la que sostiene a las demas:
--
--   1. La llave foranea es COMPUESTA contra orden_trabajo_equipo, no dos foraneas suertas hacia
--      orden_trabajo y equipo_cliente. Con dos foraneas sueltas cabria un reporte del equipo A en una
--      orden que nunca lo incluyo: dos filas existentes que juntas no significan nada. Contra el
--      puente, el esquema garantiza por si solo que solo se reporta lo que estaba en el alcance. Es la
--      misma tecnica de "UQ_sede_identidad_con_cliente" en V6, y aqui sale gratis porque el par ya es
--      la llave primaria del puente.
--
--   2. El reporte NO copia cliente, sede, tipo de servicio ni ingeniero. RF-11 pide autocompletar esos
--      datos "a partir de la informacion registrada en la orden de trabajo", y eso es exactamente lo
--      que hace una consulta: la orden ya los congelo al crearse -- por eso V6 los guarda de forma
--      redundante -- y copiarlos otra vez seria una tercera copia que hay que mantener de acuerdo. El
--      original SI copiaba k_id_cliente aqui, con k_id_orden_trabajo declarado varchar(10) contra un
--      uuid: la copia existia y el vinculo estaba roto.
--
--   3. Los cinco campos de RF-15 son NULOS mientras el reporte esta en BORRADOR y hay dos que dejan de
--      serlo al FINALIZARLO. Un reporte se abre vacio al llegar al equipo y se llena en campo; exigir
--      los cinco desde el INSERT obligaria a inventarselos. Los que se exigen al cerrar son
--      procedimientos y resultado -- que se hizo y como quedo --; falla y diagnostico se quedan
--      opcionales a proposito, porque un preventivo que sale bien no tiene ninguna de las dos.
--
--   4. Retirar un equipo de la orden NO invalida su reporte, y es deliberado. La fila del puente no se
--      borra nunca, solo apaga su b_estado_activo, asi que el reporte sigue apuntando a algo que
--      existe. Un servicio prestado ocurrio: el reporte es historia, no una vista del alcance actual.

CREATE TABLE reporte_servicio
(
    k_id_reporte_servicio uuid         NOT NULL,
    k_id_orden_trabajo    uuid         NOT NULL,
    k_id_equipo_cliente   uuid         NOT NULL,
    -- BORRADOR mientras se llena en campo, FINALIZADO cuando se cierra. No hay un tercer estado:
    -- firmar y exportar a PDF son RF-21 y RF-17, y ninguno existe todavia.
    t_estado_reporte      varchar(10)  NOT NULL DEFAULT 'BORRADOR',
    -- Los cinco campos que enumera el criterio de aceptacion de RF-15, en su orden.
    t_falla_reportada     varchar(500) NULL,
    t_diagnostico         varchar(500) NULL,
    t_procedimientos      varchar(500) NULL,
    t_observaciones       varchar(500) NULL,
    -- El resultado es el estado en el que queda el equipo, y va como catalogo cerrado porque de el
    -- cuelgan la hoja de vida (RF-26) y las alertas (RF-40): un texto libre no se puede agrupar ni
    -- disparar nada.
    --
    -- ⚠️ ESTE VOCABULARIO NO SALE DE LA ERS. La ERS solo dice "resultado" y no enumera valores, a
    -- diferencia de las periodicidades y los tipos de servicio de V6, que si estaban escritos. Los
    -- tres de aqui son una propuesta: lo que un mantenimiento puede concluir sobre un equipo. Cambiar
    -- la lista mientras no haya datos es una linea; despues es una migracion.
    t_resultado           varchar(27)  NULL,
    -- Cuando se cerro el reporte. La orden tiene la fecha PROGRAMADA del mantenimiento; esta es la de
    -- la intervencion real, y es la que necesita el historial de la hoja de vida.
    t_finalizado          timestamp    NULL,
    b_estado_activo       boolean      NOT NULL DEFAULT true,

    CONSTRAINT "PK_reporte_servicio" PRIMARY KEY (k_id_reporte_servicio),
    -- Compuesta a proposito: ver la decision 1 de la cabecera. El par es la llave primaria de
    -- orden_trabajo_equipo, de modo que no hace falta anadir ninguna restriccion a esa tabla.
    CONSTRAINT "FK_reporte_servicio_equipo_de_la_orden"
        FOREIGN KEY (k_id_orden_trabajo, k_id_equipo_cliente)
            REFERENCES orden_trabajo_equipo (k_id_orden_trabajo, k_id_equipo_cliente),

    CONSTRAINT "CHK_reporte_servicio_estado"
        CHECK (t_estado_reporte IN ('BORRADOR', 'FINALIZADO')),
    CONSTRAINT "CHK_reporte_servicio_resultado"
        CHECK (t_resultado IS NULL
            OR t_resultado IN ('OPERATIVO', 'OPERATIVO_CON_RESTRICCIONES', 'FUERA_DE_SERVICIO')),

    -- Un campo de texto o trae algo o esta nulo: la cadena vacia es la tercera opcion que sobra, y
    -- distinguir "no se reporto falla" de "se reporto una falla en blanco" no tiene sentido en un
    -- reporte que alguien va a leer impreso.
    CONSTRAINT "CHK_reporte_servicio_falla_reportada"
        CHECK (t_falla_reportada IS NULL OR btrim(t_falla_reportada) <> ''),
    CONSTRAINT "CHK_reporte_servicio_diagnostico"
        CHECK (t_diagnostico IS NULL OR btrim(t_diagnostico) <> ''),
    CONSTRAINT "CHK_reporte_servicio_procedimientos"
        CHECK (t_procedimientos IS NULL OR btrim(t_procedimientos) <> ''),
    CONSTRAINT "CHK_reporte_servicio_observaciones"
        CHECK (t_observaciones IS NULL OR btrim(t_observaciones) <> ''),

    -- VA CON CASE, y AQUI NO ES POR LA TRAMPA DE V8 -- esto se comprobo, y conviene dejarlo escrito
    -- para no repetir un motivo que no aplica. En V8, "CHK_tipo_equipo_cantidad_datos" necesitaba CASE
    -- porque la columna que discrimina, n_tipo_verificacion, es ANULABLE: con ramas unidas por OR, un
    -- tipo no verificable con cantidad fijada daba NULL OR NULL OR FALSE = NULL, y un CHECK que
    -- devuelve NULL se considera satisfecho.
    --
    -- Aqui t_estado_reporte es NOT NULL y solo admite dos valores, de modo que la version con OR es
    -- equivalente: se sustituyo a proposito por las dos ramas con OR y las 33 pruebas del esquema
    -- siguieron verdes. Se conserva el CASE por legibilidad y por parecerse a V8, no porque el OR
    -- estuviera mal.
    CONSTRAINT "CHK_reporte_servicio_cierre"
        CHECK (CASE
                   WHEN t_estado_reporte = 'FINALIZADO'
                       THEN btrim(coalesce(t_procedimientos, '')) <> ''
                           AND t_resultado IS NOT NULL
                           AND t_finalizado IS NOT NULL
                   ELSE t_finalizado IS NULL
               END)
);

COMMENT ON TABLE reporte_servicio IS
    'Lo que se hizo sobre un equipo concreto de una orden de trabajo. Uno por equipo (RF-09).';

COMMENT ON COLUMN reporte_servicio.t_resultado IS
    'Estado en el que queda el equipo tras la intervencion. Vocabulario propuesto, no declarado en la ERS.';

COMMENT ON COLUMN reporte_servicio.t_finalizado IS
    'Cuando se cerro el reporte, que es cuando se presto el servicio de verdad. Nulo en borrador.';

-- Un equipo de una orden tiene UN reporte, no dos. El indice es PARCIAL a proposito, como los de V8:
-- una restriccion normal impediria volver a abrir el reporte de un equipo cuyo reporte se retiro, y
-- aqui nada se borra.
CREATE UNIQUE INDEX "UQ_reporte_servicio_activo"
    ON reporte_servicio (k_id_orden_trabajo, k_id_equipo_cliente)
    WHERE b_estado_activo;

-- "Los reportes de esta orden" es la consulta con la que se entra al modulo -- RF-09 pide poder
-- acceder al reporte desde la orden --, y el indice unico de arriba no la sirve cuando hay reportes
-- retirados de por medio.
CREATE INDEX "IX_reporte_servicio_orden" ON reporte_servicio (k_id_orden_trabajo);

-- "El historial de este equipo" es la consulta de la hoja de vida (RF-26, RF-27).
CREATE INDEX "IX_reporte_servicio_equipo_cliente" ON reporte_servicio (k_id_equipo_cliente);

-- Las lecturas de la verificacion metrologica: el RESULTADO de verificar.
--
-- V8 dejo configurado el otro lado -- en que valores constantes se verifica un tipo de equipo
-- (punto_verificacion) y cuantas lecturas se toman en cada uno (tipo_equipo.i_cantidad_datos) -- y dijo
-- que era "con que se verifica, no el resultado de haberlo hecho". Esta tabla es ese resultado, y por
-- eso vive con el reporte: se mide durante el servicio y se imprime en el reporte de ese servicio.
CREATE TABLE dato_verificacion
(
    k_id_dato_verificacion  uuid           NOT NULL,
    k_id_reporte_servicio   uuid           NOT NULL,
    -- El punto en el que se tomo la lectura. ANULABLE, y no por descuido: con la modalidad
    -- patron_equipo_variable no hay puntos declarados -- V8 lo impone con un CHECK -- y las lecturas
    -- existen igual. Nulo significa "esta lectura no corresponde a ningun valor constante declarado".
    --
    -- Que el punto pertenezca al tipo del equipo reportado el esquema NO lo puede comprobar: son
    -- cuatro saltos desde equipo_cliente hasta tipo_equipo. Lo comprueba el servicio.
    k_id_punto_verificacion uuid           NULL,
    -- Cual de las N lecturas de ese punto es. El tope de 100 es el mismo de i_cantidad_datos en V8: si
    -- alli se declaran como maximo 100 lecturas por punto, aqui no puede haber una centesimoprimera.
    i_secuencia             integer        NOT NULL,
    -- LAS DOS LECTURAS SE GUARDAN SIEMPRE, incluso la del lado que deberia ser constante, y es
    -- deliberado por dos razones. Una: el reporte imprime las dos columnas, y deducir una de ellas del
    -- punto obligaria a consultar una configuracion que puede haber cambiado -- el punto se retira, no
    -- se edita, justamente para que los reportes viejos sigan cuadrando. Dos: lo constante lo es por
    -- como se monta el ensayo, no por decreto; si el patron marco 50,2 donde el punto dice 50, el
    -- reporte tiene que decir 50,2.
    d_valor_patron          numeric(12, 4) NOT NULL,
    d_valor_equipo          numeric(12, 4) NOT NULL,
    -- La unidad va en la lectura y no solo en el punto: sin ella no hay numero que imprimir, y con
    -- modalidad variable no hay punto de donde sacarla. Se copia del punto al tomar el dato y queda
    -- congelada ahi.
    n_unidad                varchar(20)    NOT NULL,
    b_estado_activo         boolean        NOT NULL DEFAULT true,

    CONSTRAINT "PK_dato_verificacion" PRIMARY KEY (k_id_dato_verificacion),
    CONSTRAINT "FK_dato_verificacion_reporte"
        FOREIGN KEY (k_id_reporte_servicio) REFERENCES reporte_servicio (k_id_reporte_servicio),
    -- Hacia el punto, y no compuesta con el tipo de equipo: el punto retirado tiene que seguir siendo
    -- referenciable, que es para lo que V8 lo conserva en la tabla.
    CONSTRAINT "FK_dato_verificacion_punto"
        FOREIGN KEY (k_id_punto_verificacion) REFERENCES punto_verificacion (k_id_punto_verificacion),

    CONSTRAINT "CHK_dato_verificacion_secuencia" CHECK (i_secuencia BETWEEN 1 AND 100),
    -- Sin CHECK de positividad sobre los valores, por la misma razon que en V8: un congelador se
    -- verifica a -20 grados.
    CONSTRAINT "CHK_dato_verificacion_unidad" CHECK (btrim(n_unidad) <> '')
);

COMMENT ON TABLE dato_verificacion IS
    'Lecturas tomadas durante la verificacion metrologica de un reporte. V8 configura donde y cuantas; '
    'esta tabla guarda el resultado.';

-- Dos lecturas activas con el mismo numero de secuencia en el mismo punto del mismo reporte son la
-- misma lectura dos veces. NULLS NOT DISTINCT es imprescindible aqui: por defecto PostgreSQL considera
-- que dos NULL son distintos, y sin esta clausula la modalidad variable -- la que deja el punto nulo --
-- seria la unica que admitiria duplicados, que es justo al reves de lo que se quiere.
CREATE UNIQUE INDEX "UQ_dato_verificacion_activo"
    ON dato_verificacion (k_id_reporte_servicio, k_id_punto_verificacion, i_secuencia)
    NULLS NOT DISTINCT
    WHERE b_estado_activo;

-- Las lecturas de un reporte se leen siempre juntas, y el indice unico de arriba no las sirve cuando
-- hay lecturas retiradas.
CREATE INDEX "IX_dato_verificacion_reporte" ON dato_verificacion (k_id_reporte_servicio);
