-- V37: el informe se congela al cerrar (2026-10-17).
--
-- Decidido por el usuario con el diseño único del informe. Hasta aquí, lo que el informe imprime y no
-- es del reporte —la orden, el cliente, el equipo, cómo se verifica, la empresa y el ingeniero— se leía
-- en vivo cada vez, así que corregir el modelo de un equipo o la garantía de la empresa cambiaba un
-- informe ya entregado. Ahora se copia al cerrar, y el reporte lo guarda.
--
-- UN DOCUMENTO Y NO CUARENTA COLUMNAS. Es lo que se imprime, y no se consulta campo a campo: nadie
-- busca reportes por el voltaje que tenía el modelo el día del servicio. Lleva su versión dentro.
--
-- LOS QUE YA ESTABAN CERRADOS se congelan al arrancar, con los datos de ese día, y quedan marcados con
-- b_documento_tardio: decidido por el usuario, que se sepa que su copia no es la del día del servicio.
-- Por eso no hay CHECK que exija documento a un cerrado: entre esta migración y ese arranque no lo
-- tienen, y el dominio es quien no deja cerrar sin él.
--
-- EL LOGO NO VA EN EL DOCUMENTO: se guarda una vez por versión distinta en logo_documento, por la huella
-- SHA-256 de sus bytes, y el documento lleva la huella. Copiado en cada informe serían hasta 512 KB por
-- reporte; así, mil informes con el mismo logo lo guardan una vez.

ALTER TABLE reporte_servicio
    ADD COLUMN t_documento        jsonb   NULL,
    ADD COLUMN b_documento_tardio boolean NOT NULL DEFAULT false,
    -- Un borrador compone su informe en vivo: no tiene documento, ni tardío ni del otro.
    ADD CONSTRAINT "CHK_reporte_servicio_documento"
        CHECK (CASE
                   WHEN t_estado_reporte = 'BORRADOR' THEN t_documento IS NULL AND NOT b_documento_tardio
                   ELSE NOT b_documento_tardio OR t_documento IS NOT NULL
               END);

COMMENT ON COLUMN reporte_servicio.t_documento IS
    'Lo que el informe imprime de la orden, el cliente, el equipo, su tipo, la empresa y el ingeniero, copiado al cerrar.';
COMMENT ON COLUMN reporte_servicio.b_documento_tardio IS
    'El documento se congeló después del cierre, con los datos de ese día: reportes cerrados antes de V37.';

CREATE TABLE logo_documento
(
    k_huella    varchar(64) NOT NULL,
    b_contenido bytea       NOT NULL,
    t_tipo      varchar(20) NOT NULL,

    CONSTRAINT "PK_logo_documento" PRIMARY KEY (k_huella),
    CONSTRAINT "CHK_logo_documento_huella" CHECK (k_huella ~ '^[0-9a-f]{64}$'),
    CONSTRAINT "CHK_logo_documento_tipo" CHECK (t_tipo IN ('image/png', 'image/jpeg', 'image/svg+xml')),
    CONSTRAINT "CHK_logo_documento_contenido" CHECK (octet_length(b_contenido) BETWEEN 1 AND 524288)
);

COMMENT ON TABLE logo_documento IS
    'Cada versión del logo de la empresa que algún informe congeló, una sola vez, por la huella SHA-256 de sus bytes.';
