-- V36: la empresa que presta el servicio, en la base y editable por un administrador (2026-10-17).
--
-- Desde el 2026-10-16 sus datos —el membrete de la hoja de vida y del informe— vivían en
-- config/empresa.yaml, fuera de git, y solo cambiaban reiniciando el backend. El usuario decidió que un
-- administrador los edite desde la aplicación, logo incluido, y que el archivo quede solo para sembrar
-- el primer arranque: si esta tabla está vacía y el archivo tiene empresa, el backend la crea con él.
--
-- UNA SOLA FILA. b_unica es siempre verdadera y única, así que una segunda fila choca. Es la forma de
-- decir «solo hay una» que el esquema puede expresar sin un trigger.
--
-- SIN BORRADO LÓGICO, y es la única tabla así: no hay nada que retirar. La empresa existe o todavía no
-- se ha configurado.
--
-- EL LOGO VA EN LA FILA, en bytea, con su tipo. Es una imagen de hasta 512 KB que se imprime en cada
-- documento: guardarla en un disco del servidor obligaría a respaldar ese disco aparte de la base.

CREATE TABLE empresa
(
    k_id_empresa          uuid          NOT NULL,
    b_unica               boolean       NOT NULL DEFAULT true,
    n_nombre              varchar(160)  NOT NULL,
    k_nit                 varchar(30)   NULL,
    t_direccion           varchar(200)  NULL,
    n_ciudad              varchar(80)   NULL,
    t_telefonos           varchar(30)[] NOT NULL DEFAULT '{}',
    t_movil               varchar(30)   NULL,
    t_correo              varchar(120)  NULL,
    t_garantia            varchar(2000) NULL,
    t_aclaracion          varchar(2000) NULL,
    k_codigo_hoja_de_vida varchar(40)   NULL,
    k_codigo_informe      varchar(40)   NULL,
    b_logo                bytea         NULL,
    t_logo_tipo           varchar(20)   NULL,

    CONSTRAINT "PK_empresa" PRIMARY KEY (k_id_empresa),
    CONSTRAINT "UQ_empresa_unica" UNIQUE (b_unica),
    CONSTRAINT "CHK_empresa_unica" CHECK (b_unica),
    CONSTRAINT "CHK_empresa_nombre" CHECK (btrim(n_nombre) <> ''),
    CONSTRAINT "CHK_empresa_textos"
        CHECK ((k_nit IS NULL OR btrim(k_nit) <> '')
            AND (t_direccion IS NULL OR btrim(t_direccion) <> '')
            AND (n_ciudad IS NULL OR btrim(n_ciudad) <> '')
            AND (t_movil IS NULL OR btrim(t_movil) <> '')
            AND (t_correo IS NULL OR btrim(t_correo) <> '')
            AND (t_garantia IS NULL OR btrim(t_garantia) <> '')
            AND (t_aclaracion IS NULL OR btrim(t_aclaracion) <> '')
            AND (k_codigo_hoja_de_vida IS NULL OR btrim(k_codigo_hoja_de_vida) <> '')
            AND (k_codigo_informe IS NULL OR btrim(k_codigo_informe) <> '')),
    -- El logo y su tipo van juntos, y el tipo es uno de los tres que un documento sabe pintar. Con CASE
    -- y no con OR, por la trampa de V9: un CHECK se satisface con NULL. Y por la misma trampa el IS NOT
    -- NULL de la segunda rama no sobra: con el tipo nulo, el IN da NULL y dejaba pasar un logo sin tipo.
    -- Lo encontró la prueba de persistencia, no la lectura.
    CONSTRAINT "CHK_empresa_logo"
        CHECK (CASE
                   WHEN b_logo IS NULL THEN t_logo_tipo IS NULL
                   ELSE t_logo_tipo IS NOT NULL
                       AND t_logo_tipo IN ('image/png', 'image/jpeg', 'image/svg+xml')
                       AND octet_length(b_logo) BETWEEN 1 AND 524288
               END)
);

COMMENT ON TABLE empresa IS
    'La empresa que presta el servicio técnico: el membrete de los documentos. Una sola fila.';
COMMENT ON COLUMN empresa.b_logo IS
    'El logo, PNG, JPG o SVG de hasta 512 KB. Nulo: el membrete es el nombre.';
