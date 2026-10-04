-- V12: el historial de intervenciones de un equipo, que es la cuarta seccion de su hoja de vida.
--
-- RF-26 pide que cada mantenimiento quede registrado automaticamente al completar un reporte, y
-- RF-27 que ese historial guarde fecha de servicio, tipo y resultado, consultable y ordenado.
--
-- LO QUE ESTA MIGRACION NO ES. No crea la hoja de vida: la hoja de vida es un COMPILADO y existe
-- desde que el equipo se registra, con su historial en cero. Sus otras tres secciones -identificacion,
-- tecnica y fabricante- salen de datos que ya estan repartidos por la cadena del catalogo y no
-- necesitan tabla ninguna. Esta tabla es solo la cuarta seccion, la unica que sale de los reportes.
--
-- POR QUE HAY TABLA Y NO UNA CONSULTA. Los tres datos que RF-27 pide ya existen: el resultado y la
-- fecha de cierre estan en reporte_servicio y el tipo de servicio en orden_trabajo, de modo que el
-- historial se podria derivar sin guardar nada. Se guarda por una razon de dominio y no tecnica: una
-- intervencion ocurrio, y la hoja de vida de un equipo medico es un documento con valor legal. Si el
-- reporte se retira despues -el borrado es logico, pero deja de contar en todas partes- el
-- mantenimiento no deja de haber ocurrido.
--
-- Y POR ESO LAS TRES COLUMNAS SON UNA COPIA CONGELADA: guardan lo que el servicio FUE, no lo que su
-- reporte dice hoy. Es el mismo patron que k_id_area_servicio en orden_trabajo_equipo, y trae la misma
-- consecuencia, que conviene escribir para quien lea esto buscando la restriccion que falta:
--
--   -- NO anadir
--   FOREIGN KEY (k_id_reporte_servicio, t_resultado)
--       REFERENCES reporte_servicio (k_id_reporte_servicio, t_resultado)
--
-- Parece la restriccion correcta y lo seria en el INSERT. El problema es el UPDATE del otro lado: con
-- NO ACTION, corregir el resultado de un reporte quedaria bloqueado por la intervencion que lo copio.
-- La restriccion que protege el pasado impediria el futuro.
--
-- Y hay un segundo motivo, que aparecio al mutar esta migracion para ver fallar la prueba que la
-- defiende: esa foranea NO SE PUEDE CREAR tal cual. PostgreSQL exige que las columnas referenciadas
-- tengan su propia restriccion unica, y k_id_reporte_servicio ya es la llave primaria, de modo que
-- habria que anadir ademas UNIQUE (k_id_reporte_servicio, t_resultado) a reporte_servicio: un indice
-- redundante por definicion -la llave primaria ya es unica- cuyo unico proposito seria sostener una
-- restriccion que no debe existir.

CREATE TABLE intervencion
(
    k_id_intervencion     uuid        NOT NULL,

    -- De quien es la hoja de vida. Es el equipo INSTALADO y no el del catalogo: el historial es de la
    -- maquina concreta, con su serie.
    k_id_equipo_cliente   uuid        NOT NULL,

    -- De donde salio. Se conserva para poder volver al reporte completo desde el historial, y es lo
    -- que hace que la fila sea trazable en vez de un dato suelto.
    k_id_reporte_servicio uuid        NOT NULL,

    -- Las tres de RF-27, congeladas al cerrar el reporte.
    f_fecha_servicio      timestamp   NOT NULL,
    t_tipo_servicio       varchar(11) NOT NULL,
    t_resultado           varchar(27) NOT NULL,

    b_estado_activo       boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_intervencion" PRIMARY KEY (k_id_intervencion),

    CONSTRAINT "FK_intervencion_equipo_cliente"
        FOREIGN KEY (k_id_equipo_cliente) REFERENCES equipo_cliente (k_id_equipo_cliente),

    CONSTRAINT "FK_intervencion_reporte_servicio"
        FOREIGN KEY (k_id_reporte_servicio) REFERENCES reporte_servicio (k_id_reporte_servicio),

    -- Un reporte se registra UNA sola vez, y esta restriccion es lo que hace al oyente idempotente
    -- sin que el oyente tenga que saberlo. No es parcial a proposito: retirar una intervencion no
    -- debe dejar hueco para registrar otra del mismo reporte, porque entonces el historial podria
    -- contar dos veces el mismo mantenimiento.
    CONSTRAINT "UQ_intervencion_por_reporte" UNIQUE (k_id_reporte_servicio),

    -- Los mismos valores que su origen, repetidos a proposito: son una copia, y una copia que admita
    -- valores que el original no admite deja de ser comprobable contra nada.
    CONSTRAINT "CHK_intervencion_tipo_servicio"
        CHECK (t_tipo_servicio IN ('PREVENTIVO', 'CORRECTIVO', 'CALIBRACION')),

    CONSTRAINT "CHK_intervencion_resultado"
        CHECK (t_resultado IN ('OPERATIVO', 'OPERATIVO_CON_RESTRICCIONES', 'FUERA_DE_SERVICIO'))
);

COMMENT ON TABLE intervencion IS
    'Historial de mantenimientos, calibraciones y diagnosticos de un equipo instalado: la cuarta '
    'seccion de su hoja de vida. Una fila por reporte de servicio finalizado. Las columnas de fecha, '
    'tipo y resultado son copias congeladas del momento del cierre, no referencias al estado actual '
    'del reporte: una intervencion ocurrio, y sigue habiendo ocurrido si su reporte se retira.';

COMMENT ON COLUMN intervencion.f_fecha_servicio IS
    'Cuando se completo el servicio, copiado de reporte_servicio.t_finalizado. No es la fecha '
    'programada de la orden: un mantenimiento se ejecuta el dia que se ejecuta.';

-- El historial se consulta por equipo y en orden cronologico, que es literalmente lo que pide el
-- segundo criterio de RF-27. El indice lleva la fecha para que ese orden no cueste una ordenacion.
CREATE INDEX "IX_intervencion_equipo_fecha"
    ON intervencion (k_id_equipo_cliente, f_fecha_servicio DESC);
