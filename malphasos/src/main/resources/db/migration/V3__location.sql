-- Modulo de ubicaciones: paises y ciudades.
--
-- Es una tabla de referencia compartida, no un modulo de negocio: los paises los
-- consultan los clientes y los fabricantes de equipos, y las ciudades las sedes de
-- los clientes. Por eso se migra antes que client, cuya tabla sede exige una ciudad
-- que exista (k_id_ciudad es NOT NULL).
--
-- Se conserva la convencion de prefijos del esquema original: k_ llaves, n_ nombres,
-- t_ texto libre, b_ booleanos.

CREATE TABLE pais
(
    k_id_pais      uuid        NOT NULL,
    -- Codigo ISO 3166-1 alfa-3 (COL, USA, DEU). El esquema original lo usaba como
    -- llave primaria, en un varchar(3) sin restriccion de unicidad ni de formato.
    -- Aqui la llave es un UUID, por la convencion adoptada en V1__baseline, y el
    -- codigo se conserva como llave natural: sigue siendo unico y consultable, pero
    -- deja de ser aquello a lo que apuntan las claves foraneas de medio esquema.
    k_codigo_iso   char(3)     NOT NULL,
    n_nombre_pais  varchar(50) NOT NULL,
    b_estado_activo boolean    NOT NULL DEFAULT true,

    CONSTRAINT "PK_pais" PRIMARY KEY (k_id_pais),
    CONSTRAINT "UQ_pais_codigo_iso" UNIQUE (k_codigo_iso),
    CONSTRAINT "UQ_pais_nombre" UNIQUE (n_nombre_pais),
    -- El original no restringia el formato: cabia cualquier cosa de hasta tres
    -- caracteres, incluidos minusculas y espacios.
    CONSTRAINT "CHK_pais_codigo_iso" CHECK (k_codigo_iso ~ '^[A-Z]{3}$')
);

COMMENT ON TABLE pais IS
    'Paises de referencia. Los consultan los clientes y los fabricantes de equipos.';

COMMENT ON COLUMN pais.k_codigo_iso IS
    'Codigo ISO 3166-1 alfa-3, en mayusculas.';

CREATE TABLE ciudad
(
    k_id_ciudad     uuid        NOT NULL,
    n_nombre_ciudad varchar(50) NOT NULL,
    k_id_pais       uuid        NOT NULL,                      -- Pais al que pertenece
    b_estado_activo boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_ciudad" PRIMARY KEY (k_id_ciudad),
    CONSTRAINT "FK_ciudad_pais" FOREIGN KEY (k_id_pais) REFERENCES pais (k_id_pais),
    -- El nombre de una ciudad solo es unico dentro de su pais: hay un Cordoba en
    -- Espana y otro en Argentina. El original no declaraba unicidad de ninguna clase,
    -- y ademas derivaba la llave primaria de las dos primeras letras del nombre
    -- (City.createIdFromName), de modo que Bogota y Boyaca colisionaban en 'BO'.
    CONSTRAINT "UQ_ciudad_nombre_por_pais" UNIQUE (k_id_pais, n_nombre_ciudad)
);

COMMENT ON TABLE ciudad IS
    'Ciudades donde los clientes tienen sedes.';

-- Las sedes se consultan por ciudad al listar la cobertura de un cliente.
CREATE INDEX "IX_ciudad_pais" ON ciudad (k_id_pais);
