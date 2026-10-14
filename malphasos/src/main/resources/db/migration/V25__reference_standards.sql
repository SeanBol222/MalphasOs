-- V25: el catálogo de equipos patrón de la empresa (2026-10-14).
--
-- Decidido por el usuario: la calibración —y todo reporte con lecturas— registra contra qué patrón se
-- midió, y ese patrón sale de un catálogo y no se escribe a mano en cada reporte. El reporte copiará lo
-- que el patrón tenía ese día (V27), de modo que renovar aquí un certificado no reescribe reportes
-- viejos. Con el vencimiento en su sitio, RF-40 y RF-41 —las alertas— quedan a una consulta de
-- distancia, aunque no se construyen ahora.
--
-- La serie no se repite entre los vigentes: es lo que identifica al aparato físico. Uno retirado libera
-- su serie, por si vuelve.

CREATE TABLE equipo_patron
(
    k_id_equipo_patron        uuid         NOT NULL,
    n_nombre                  varchar(120) NOT NULL,
    n_marca                   varchar(80)  NULL,
    k_serie                   varchar(60)  NOT NULL,
    n_certificado             varchar(80)  NOT NULL,
    f_vencimiento_certificado date         NOT NULL,
    b_estado_activo           boolean      NOT NULL DEFAULT true,

    CONSTRAINT "PK_equipo_patron" PRIMARY KEY (k_id_equipo_patron),
    CONSTRAINT "CHK_equipo_patron_nombre" CHECK (btrim(n_nombre) <> ''),
    CONSTRAINT "CHK_equipo_patron_marca" CHECK (n_marca IS NULL OR btrim(n_marca) <> ''),
    CONSTRAINT "CHK_equipo_patron_serie" CHECK (btrim(k_serie) <> ''),
    CONSTRAINT "CHK_equipo_patron_certificado" CHECK (btrim(n_certificado) <> '')
);

CREATE UNIQUE INDEX "UQ_equipo_patron_serie_activo"
    ON equipo_patron (k_serie)
    WHERE b_estado_activo;

COMMENT ON TABLE equipo_patron IS
    'Los equipos patrón de la empresa, contra los que se toman las lecturas de una verificación.';
COMMENT ON COLUMN equipo_patron.f_vencimiento_certificado IS
    'Hasta cuándo vale su certificado de calibración. Con él vencido el día del servicio, un reporte no se cierra.';
