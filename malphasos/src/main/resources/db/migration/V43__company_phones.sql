-- V43: los teléfonos fijos de la empresa, en su tabla (2026-10-27).
--
-- Grupo A1 del análisis del esquema (Documentation/DatabaseDesign/analisis-2026-10-27.md), decidido por
-- el usuario. empresa.t_telefonos era un arreglo en una columna, que es lo que la primera forma normal
-- prohíbe: el arreglo no se puede indexar, ni ponerle un CHECK a cada teléfono, ni lo ve nadie que lea
-- el esquema como tablas. V36 lo eligió por comodidad, y es la única columna así del esquema.
--
-- UNA LISTA DE VALORES, NO UNA TABLA DE ENTIDADES. Los teléfonos de la empresa no se editan uno a uno:
-- el formulario de la empresa manda la lista entera, y su orden es el que se imprime. Por eso la llave es
-- (empresa, posición) y no un identificador propio, y por eso no llevan b_estado_activo: la empresa
-- misma no lo lleva —es una sola fila que se edita, V36—, y un teléfono quitado de la lista no es un
-- hecho que haya que conservar. telefono_cliente sí tiene identificador y borrado lógico porque cada uno
-- se da de alta y se retira por separado.
--
-- t_movil SE QUEDA en empresa: no es un teléfono más de la lista sino otro dato, el móvil, que los
-- documentos imprimen aparte.

CREATE TABLE telefono_empresa
(
    k_id_empresa       uuid        NOT NULL,
    i_orden            integer     NOT NULL,
    n_telefono_empresa varchar(30) NOT NULL,

    CONSTRAINT "PK_telefono_empresa" PRIMARY KEY (k_id_empresa, i_orden),
    CONSTRAINT "FK_telefono_empresa_empresa" FOREIGN KEY (k_id_empresa) REFERENCES empresa (k_id_empresa),
    CONSTRAINT "CHK_telefono_empresa_orden" CHECK (i_orden >= 0),
    CONSTRAINT "CHK_telefono_empresa_telefono" CHECK (btrim(n_telefono_empresa) <> '')
);

COMMENT ON TABLE telefono_empresa IS
    'Los teléfonos fijos de la empresa, en el orden en que se imprimen. Se reescriben enteros al editarla.';

-- Los que había, en su orden. WITH ORDINALITY numera desde 1 y la posición, como la de JPA, desde 0. Se
-- saltan los que estuvieran en blanco, que el dominio nunca guardó pero el arreglo no impedía.
INSERT INTO telefono_empresa (k_id_empresa, i_orden, n_telefono_empresa)
SELECT e.k_id_empresa, row_number() OVER (PARTITION BY e.k_id_empresa ORDER BY t.posicion) - 1, btrim(t.telefono)
FROM empresa e
         CROSS JOIN LATERAL unnest(e.t_telefonos) WITH ORDINALITY AS t(telefono, posicion)
WHERE btrim(t.telefono) <> '';

ALTER TABLE empresa DROP COLUMN t_telefonos;
