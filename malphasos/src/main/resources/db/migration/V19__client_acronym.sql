-- V19: la sigla de cada cliente, que encabeza el numero de sus hojas de vida: «HV-CDN-0001».
--
-- Decisiones del usuario del 2026-10-05: se genera sola a partir de la razon social, se puede corregir
-- despues, y no se regenera si la razon social cambia. Tres a seis caracteres, mayusculas y digitos,
-- empezando por letra, unica entre clientes.
--
-- LA REGLA VIVE DOS VECES, y a proposito. En Java la usa el alta de un cliente (ClientAcronym.base); aqui
-- la usa esta migracion para dar sigla a los clientes que ya existian. Las dos tienen que generar lo
-- mismo, y la funcion SE QUEDA en la base para que una prueba pueda compararlas con la misma lista de
-- razones sociales: si una cambia sin la otra, la prueba falla.

CREATE FUNCTION sigla_base(razon_social text) RETURNS varchar
    LANGUAGE plpgsql
    IMMUTABLE
AS
$$
DECLARE
    texto          text;
    palabras       text[];
    significativas text[] := '{}';
    palabra        text;
    sigla          text   := '';
    ultima         text;
    i              integer;
BEGIN
    -- 1. Mayusculas, sin tildes ni puntuacion: «S.A.S.» es «SAS».
    texto := upper(translate(coalesce(razon_social, ''),
                             'áéíóúüñàèìòùâêîôûäëïöçÁÉÍÓÚÜÑÀÈÌÒÙÂÊÎÔÛÄËÏÖÇ',
                             'aeiouunaeiouaeiouaeiocAEIOUUNAEIOUAEIOUAEIOC'));
    texto := replace(texto, '.', '');
    texto := btrim(regexp_replace(texto, '[^A-Z0-9]+', ' ', 'g'));
    palabras := CASE WHEN texto = '' THEN '{}'::text[] ELSE string_to_array(texto, ' ') END;

    -- 2. Fuera las formas juridicas y las palabras vacias.
    FOREACH palabra IN ARRAY palabras
        LOOP
            IF palabra NOT IN ('SAS', 'SA', 'LTDA', 'EU', 'SCA', 'SENC', 'S', 'C',
                               'DE', 'DEL', 'LA', 'LAS', 'LOS', 'EL', 'Y', 'E', 'EN') THEN
                significativas := significativas || palabra;
            END IF;
        END LOOP;
    IF cardinality(significativas) = 0 AND texto <> '' THEN
        significativas := palabras;
    END IF;

    -- 3. La inicial de cada palabra, hasta seis.
    FOREACH palabra IN ARRAY significativas
        LOOP
            EXIT WHEN length(sigla) = 6;
            sigla := sigla || left(palabra, 1);
        END LOOP;

    -- 4. Menos de tres: se completa con las letras siguientes de la ultima palabra.
    IF length(sigla) < 3 AND cardinality(significativas) > 0 THEN
        ultima := significativas[cardinality(significativas)];
        i := 2;
        WHILE i <= length(ultima) AND length(sigla) < 3
            LOOP
                sigla := sigla || substr(ultima, i, 1);
                i := i + 1;
            END LOOP;
    END IF;

    -- 5. Y si aun no llega, o no empieza por letra, una «C».
    IF sigla = '' OR substr(sigla, 1, 1) !~ '[A-Z]' THEN
        sigla := 'C' || sigla;
    END IF;
    WHILE length(sigla) < 3
        LOOP
            sigla := sigla || 'C';
        END LOOP;

    RETURN left(sigla, 6);
END;
$$;

COMMENT ON FUNCTION sigla_base(text) IS
    'Gemela de ClientAcronym.base en Java. Se usa en V19 y en la prueba que exige que las dos coincidan.';

ALTER TABLE cliente
    ADD COLUMN n_sigla varchar(6) NULL;

-- Los clientes que ya existen, con el desempate «CDN», «CDN2», «CDN3». Por razon social y despues por
-- identificador, para que el resultado no dependa del orden fisico de las filas. Si con el numero no
-- cabe en seis, se recorta la base y no el numero, igual que en Java.
DO
$$
    DECLARE
        fila       record;
        base       text;
        candidata  text;
        numero     integer;
    BEGIN
        FOR fila IN SELECT k_id_cliente, n_razon_social FROM cliente ORDER BY n_razon_social, k_id_cliente
            LOOP
                base := sigla_base(fila.n_razon_social);
                numero := 1;
                LOOP
                    candidata := CASE
                                     WHEN numero = 1 THEN base
                                     ELSE left(base, 6 - length(numero::text)) || numero END;
                    EXIT WHEN NOT EXISTS (SELECT 1 FROM cliente WHERE n_sigla = candidata);
                    numero := numero + 1;
                END LOOP;
                UPDATE cliente SET n_sigla = candidata WHERE k_id_cliente = fila.k_id_cliente;
            END LOOP;
    END
$$;

ALTER TABLE cliente
    ALTER COLUMN n_sigla SET NOT NULL,
    ADD CONSTRAINT "UQ_cliente_sigla" UNIQUE (n_sigla),
    ADD CONSTRAINT "CHK_cliente_sigla" CHECK (n_sigla ~ '^[A-Z][A-Z0-9]{2,5}$');

-- Y LA FILA QUE ENTRE SIN SIGLA, LA RECIBE. El alta por la aplicacion la trae ya desempatada; esto es
-- para lo que entra por fuera del dominio -un INSERT a mano, un guion, los datos de una prueba-, que sin
-- el trigger fallaria contra el NOT NULL o, peor, obligaria a inventar una sigla a mano. Usa la misma
-- funcion, el mismo desempate y el mismo candado que ClientService, de modo que tampoco choca con un
-- alta concurrente.
CREATE FUNCTION asignar_sigla_cliente() RETURNS trigger
    LANGUAGE plpgsql
AS
$$
DECLARE
    base      text;
    candidata text;
    numero    integer := 1;
BEGIN
    IF NEW.n_sigla IS NULL THEN
        PERFORM pg_advisory_xact_lock(hashtext('cliente.n_sigla'));
        base := sigla_base(NEW.n_razon_social);
        LOOP
            candidata := CASE WHEN numero = 1 THEN base ELSE left(base, 6 - length(numero::text)) || numero END;
            EXIT WHEN NOT EXISTS (SELECT 1 FROM cliente WHERE n_sigla = candidata);
            numero := numero + 1;
        END LOOP;
        NEW.n_sigla := candidata;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER "TRG_cliente_sigla"
    BEFORE INSERT
    ON cliente
    FOR EACH ROW
EXECUTE FUNCTION asignar_sigla_cliente();

COMMENT ON COLUMN cliente.n_sigla IS
    'Encabeza el numero de las hojas de vida del cliente. Generada de la razon social, corregible, unica.';
