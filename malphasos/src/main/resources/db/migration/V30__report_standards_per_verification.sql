-- V30: los patrones de un reporte, por verificación y varios en cada una (2026-10-16).
--
-- Decidido por el usuario: V27 guardaba un patrón por reporte, y los informes que la empresa entrega
-- hoy usan varios —una balanza se verifica con tres juegos de pesas, un termohigrómetro con un patrón
-- de temperatura y otro de humedad—. Cada verificación del reporte dice ahora contra qué patrones se
-- midió, y la copia lleva la ficha entera de V29, porque es lo que el informe imprime en su tabla de
-- trazabilidad.
--
-- Copia y no referencia viva, como en V27 y por lo mismo: renovar un certificado en el catálogo no
-- puede reescribir un reporte firmado. La foránea al patrón es simple —un patrón no se borra, solo se
-- retira— y la de la verificación también: que la verificación sea del tipo del equipo lo comprueba el
-- servicio, porque el tipo está a cuatro saltos del reporte.
--
-- Borrado lógico, como todo el esquema: quitar un patrón de una verificación retira la fila, y volver
-- a ponerlo la reactiva en vez de insertar otra. Por eso el índice único es parcial y nunca compiten
-- dos filas activas por el mismo sitio en un mismo vaciado de Hibernate.

CREATE TABLE patron_verificacion_reporte
(
    k_id_patron_verificacion_reporte uuid         NOT NULL,
    k_id_reporte_servicio            uuid         NOT NULL,
    k_id_verificacion                uuid         NOT NULL,
    k_id_equipo_patron               uuid         NOT NULL,
    n_nombre                         varchar(120) NOT NULL,
    n_marca                          varchar(80)  NULL,
    k_serie                          varchar(60)  NOT NULL,
    n_certificado                    varchar(80)  NOT NULL,
    f_vencimiento                    date         NOT NULL,
    n_modelo                         varchar(80)  NULL,
    k_codigo_interno                 varchar(40)  NULL,
    t_rango                          varchar(120) NULL,
    t_resolucion                     varchar(80)  NULL,
    f_calibracion                    date         NULL,
    n_empresa_calibradora            varchar(120) NULL,
    b_estado_activo                  boolean      NOT NULL DEFAULT true,

    CONSTRAINT "PK_patron_verificacion_reporte" PRIMARY KEY (k_id_patron_verificacion_reporte),
    CONSTRAINT "FK_patron_verificacion_reporte_reporte"
        FOREIGN KEY (k_id_reporte_servicio) REFERENCES reporte_servicio (k_id_reporte_servicio),
    CONSTRAINT "FK_patron_verificacion_reporte_verificacion"
        FOREIGN KEY (k_id_verificacion) REFERENCES verificacion_tipo_equipo (k_id_verificacion),
    CONSTRAINT "FK_patron_verificacion_reporte_patron"
        FOREIGN KEY (k_id_equipo_patron) REFERENCES equipo_patron (k_id_equipo_patron),
    CONSTRAINT "CHK_patron_verificacion_reporte_calibracion" CHECK (f_calibracion <= f_vencimiento)
);

CREATE UNIQUE INDEX "UQ_patron_verificacion_reporte_activo"
    ON patron_verificacion_reporte (k_id_reporte_servicio, k_id_verificacion, k_id_equipo_patron)
    WHERE b_estado_activo;

COMMENT ON TABLE patron_verificacion_reporte IS
    'Contra qué patrones se tomaron las lecturas de cada verificación de un reporte, copiados como estaban ese día.';

-- El patrón de V27 pasa a cada verificación de la que el reporte tiene lecturas, retiradas incluidas:
-- una lectura corregida también se tomó contra él. La ficha nueva queda vacía, porque V27 no la
-- copiaba y la de hoy no es la que valía entonces.
--
-- Un reporte con patrón y sin una sola lectura lo pierde, y es a propósito: se eligió en un borrador
-- donde no se midió nada, de modo que no certifica nada, y repartirlo exigiría inventar a qué
-- verificación pertenecía.

INSERT INTO patron_verificacion_reporte
    (k_id_patron_verificacion_reporte, k_id_reporte_servicio, k_id_verificacion, k_id_equipo_patron, n_nombre,
     n_marca, k_serie, n_certificado, f_vencimiento)
SELECT gen_random_uuid(),
       r.k_id_reporte_servicio,
       v.k_id_verificacion,
       r.k_id_equipo_patron,
       r.n_patron_nombre,
       r.n_patron_marca,
       r.k_patron_serie,
       r.n_patron_certificado,
       r.f_patron_vencimiento
FROM reporte_servicio r
         JOIN (SELECT DISTINCT k_id_reporte_servicio, k_id_verificacion FROM dato_verificacion) v
              ON v.k_id_reporte_servicio = r.k_id_reporte_servicio
WHERE r.k_id_equipo_patron IS NOT NULL;

ALTER TABLE reporte_servicio
    DROP CONSTRAINT "CHK_reporte_servicio_patron_completo",
    DROP CONSTRAINT "FK_reporte_servicio_equipo_patron",
    DROP COLUMN k_id_equipo_patron,
    DROP COLUMN n_patron_nombre,
    DROP COLUMN n_patron_marca,
    DROP COLUMN k_patron_serie,
    DROP COLUMN n_patron_certificado,
    DROP COLUMN f_patron_vencimiento;
