-- V20: el numero de la hoja de vida de cada equipo instalado, «HV-CDN-0001».
--
-- Decisiones del usuario del 2026-10-05: la sigla del cliente (V19) y un consecutivo POR CLIENTE; el
-- numero se asigna al registrar el equipo y NO CAMBIA nunca, ni aunque cambie la sigla del cliente: una
-- hoja ya impresa no puede dejar de coincidir con el sistema. Por eso se guarda entero, sigla incluida,
-- y no se compone al leer.
--
-- LO ASIGNA LA BASE, con un trigger, y no la aplicacion. Es un contador, y un contador correcto con
-- altas concurrentes necesita bloquear la fila del cliente: el UPDATE ... RETURNING del trigger lo hace
-- solo, y si el alta falla el contador vuelve atras con ella. Hacerlo en Java era leer, sumar y escribir
-- en tres pasos, con la carrera en medio. Y asi tambien reciben numero los equipos que entran por fuera
-- del dominio -un INSERT a mano, los datos de una prueba-, igual que la sigla en V19.
--
-- Una secuencia de PostgreSQL no servia: es global, y el consecutivo es por cliente.

ALTER TABLE cliente
    ADD COLUMN i_consecutivo_hoja_vida integer NOT NULL DEFAULT 0,
    ADD CONSTRAINT "CHK_cliente_consecutivo_hoja_vida" CHECK (i_consecutivo_hoja_vida >= 0);

COMMENT ON COLUMN cliente.i_consecutivo_hoja_vida IS
    'El ultimo numero de hoja de vida asignado a un equipo de este cliente. Lo avanza un trigger.';

ALTER TABLE equipo_cliente
    ADD COLUMN n_numero_hoja_vida varchar(20) NULL;

-- Los equipos que ya existen, numerados dentro de cada cliente. No hay fecha de alta en ninguna tabla,
-- de modo que el orden es por serie y despues por identificador: estable, y no depende del orden fisico
-- de las filas. Pasado el 9999 el numero crece en vez de recortarse.
WITH numerados AS (SELECT u.k_id_equipo_cliente,
                          c.k_id_cliente,
                          c.n_sigla,
                          row_number() OVER (PARTITION BY c.k_id_cliente
                                             ORDER BY u.k_serie, u.k_id_equipo_cliente) AS n
                   FROM equipo_cliente u
                            JOIN area_servicio a ON a.k_id_area_servicio = u.k_id_area_servicio
                            JOIN sede s ON s.k_id_sede = a.k_id_sede
                            JOIN cliente c ON c.k_id_cliente = s.k_id_cliente)
UPDATE equipo_cliente u
SET n_numero_hoja_vida = 'HV-' || numerados.n_sigla || '-'
                             || CASE WHEN numerados.n < 10000 THEN lpad(numerados.n::text, 4, '0') ELSE numerados.n::text END
FROM numerados
WHERE numerados.k_id_equipo_cliente = u.k_id_equipo_cliente;

-- Y cada cliente sigue contando desde donde quedo.
UPDATE cliente c
SET i_consecutivo_hoja_vida = cuenta.n
FROM (SELECT s.k_id_cliente, count(*) AS n
      FROM equipo_cliente u
               JOIN area_servicio a ON a.k_id_area_servicio = u.k_id_area_servicio
               JOIN sede s ON s.k_id_sede = a.k_id_sede
      GROUP BY s.k_id_cliente) cuenta
WHERE cuenta.k_id_cliente = c.k_id_cliente;

ALTER TABLE equipo_cliente
    ALTER COLUMN n_numero_hoja_vida SET NOT NULL,
    ADD CONSTRAINT "UQ_equipo_cliente_numero_hoja_vida" UNIQUE (n_numero_hoja_vida);

COMMENT ON COLUMN equipo_cliente.n_numero_hoja_vida IS
    'HV-<sigla>-0001. Lo asigna un trigger al insertar y no cambia nunca, ni aunque cambie la sigla.';

CREATE FUNCTION asignar_numero_hoja_vida() RETURNS trigger
    LANGUAGE plpgsql
AS
$$
DECLARE
    sigla  text;
    numero integer;
BEGIN
    IF NEW.n_numero_hoja_vida IS NULL THEN
        -- El UPDATE bloquea la fila del cliente hasta el final de la transaccion: dos altas a la vez
        -- en el mismo cliente esperan una a la otra y no repiten numero.
        UPDATE cliente c
        SET i_consecutivo_hoja_vida = c.i_consecutivo_hoja_vida + 1
        FROM area_servicio a,
             sede s
        WHERE a.k_id_area_servicio = NEW.k_id_area_servicio
          AND s.k_id_sede = a.k_id_sede
          AND c.k_id_cliente = s.k_id_cliente
        RETURNING c.n_sigla, c.i_consecutivo_hoja_vida INTO sigla, numero;

        IF sigla IS NULL THEN
            RAISE EXCEPTION 'El area de servicio % no lleva a ningun cliente', NEW.k_id_area_servicio;
        END IF;

        NEW.n_numero_hoja_vida := 'HV-' || sigla || '-'
                                      || CASE WHEN numero < 10000 THEN lpad(numero::text, 4, '0') ELSE numero::text END;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER "TRG_equipo_cliente_numero_hoja_vida"
    BEFORE INSERT
    ON equipo_cliente
    FOR EACH ROW
EXECUTE FUNCTION asignar_numero_hoja_vida();
