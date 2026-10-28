-- V44: la ciudad de la empresa, del catálogo (2026-10-27).
--
-- Grupo C2 del análisis del esquema (Documentation/DatabaseDesign/analisis-2026-10-27.md), decidido por
-- el usuario. empresa.n_ciudad era texto libre en un esquema que tiene catálogo de ciudades desde V3 y lo
-- usa para las sedes y los fabricantes: «Bogota», «Bogotá D.C.» y «bogotá» eran tres ciudades distintas
-- que nada comprobaba. Pasa a ser una foránea, como la de la sede.
--
-- LA QUE YA TENÍA SE BUSCA POR SU NOMBRE, sin mayúsculas, sin tildes y sin espacios alrededor, que es como
-- busca la pantalla. Si casa con UNA ciudad del catálogo se enlaza. Si no casa con ninguna, o con varias
-- —el catálogo repite nombres entre países—, la migración FALLA diciendo cuál: es la decisión de V10, y
-- las otras dos opciones eran adivinar o borrarla en silencio. Se arregla corrigiendo el nombre en la
-- pantalla de la empresa antes de actualizar, o vaciándolo y eligiéndola después.

ALTER TABLE empresa ADD COLUMN k_id_ciudad uuid NULL;

ALTER TABLE empresa
    ADD CONSTRAINT "FK_empresa_ciudad" FOREIGN KEY (k_id_ciudad) REFERENCES ciudad (k_id_ciudad);

CREATE INDEX "IX_empresa_ciudad" ON empresa (k_id_ciudad);

COMMENT ON COLUMN empresa.k_id_ciudad IS 'La ciudad de la empresa, del catálogo. Nula: el membrete no la imprime.';

DO
$$
    DECLARE
        nombre     varchar;
        candidatas integer;
    BEGIN
        SELECT e.n_ciudad,
               (SELECT count(*)
                FROM ciudad c
                WHERE lower(translate(btrim(c.n_nombre_ciudad), 'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun'))
                          = lower(translate(btrim(e.n_ciudad), 'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun')))
        INTO nombre, candidatas
        FROM empresa e
        WHERE e.n_ciudad IS NOT NULL;

        IF nombre IS NOT NULL AND candidatas <> 1 THEN
            RAISE EXCEPTION 'V44: la ciudad de la empresa, «%», casa con % ciudades del catálogo y tiene que casar con una. Corríjala en la pantalla de la empresa, o déjela vacía, antes de actualizar.',
                nombre, candidatas;
        END IF;
    END
$$;

UPDATE empresa e
SET k_id_ciudad = c.k_id_ciudad
FROM ciudad c
WHERE lower(translate(btrim(c.n_nombre_ciudad), 'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun'))
          = lower(translate(btrim(e.n_ciudad), 'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun'));

ALTER TABLE empresa DROP CONSTRAINT "CHK_empresa_textos";

ALTER TABLE empresa DROP COLUMN n_ciudad;

-- El mismo CHECK de V36, sin la ciudad: «un texto opcional no es una cadena en blanco».
ALTER TABLE empresa
    ADD CONSTRAINT "CHK_empresa_textos"
        CHECK ((k_nit IS NULL OR btrim(k_nit) <> '')
            AND (t_direccion IS NULL OR btrim(t_direccion) <> '')
            AND (t_movil IS NULL OR btrim(t_movil) <> '')
            AND (t_correo IS NULL OR btrim(t_correo) <> '')
            AND (t_garantia IS NULL OR btrim(t_garantia) <> '')
            AND (t_aclaracion IS NULL OR btrim(t_aclaracion) <> '')
            AND (k_codigo_hoja_de_vida IS NULL OR btrim(k_codigo_hoja_de_vida) <> '')
            AND (k_codigo_informe IS NULL OR btrim(k_codigo_informe) <> ''));
