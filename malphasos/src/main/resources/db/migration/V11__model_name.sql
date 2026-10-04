-- Un modelo tiene nombre: «IdeaPad 3» si la marca es Lenovo.
--
-- POR QUE FALTABA, que es lo que conviene no repetir. La cadena del catalogo se construyo el
-- 2026-09-02 con la forma del sistema original, y alli un modelo se identificaba por su REGISTRO
-- SANITARIO: n_invima era lo unico legible que llevaba. Eso basta para un tramite y no para una
-- pantalla -- y menos para una anulable, porque el INVIMA se tramita DESPUES de dar de alta el
-- modelo. Resultado: un modelo podia existir sin nombre y sin INVIMA, identificado solo por un UUID,
-- y la pantalla que lista modelos no tenia nada que escribir en la fila.
--
-- Lo pidio el usuario el 2026-10-04, con el ejemplo de arriba.
--
-- POR QUE EN modelo Y NO EN equipo. En este esquema «equipo» es la combinacion de una MARCA y un TIPO
-- -- «tensiometro Welch Allyn» -- y «modelo» es esa combinacion concretada por un fabricante. El
-- nombre comercial es del modelo: Lenovo es la marca, el tipo seria «portatil», y «IdeaPad 3» es
-- justamente lo que distingue este modelo de los demas portatiles de Lenovo.

ALTER TABLE modelo
    ADD COLUMN n_nombre_modelo varchar(50) NULL;

COMMENT ON COLUMN modelo.n_nombre_modelo IS
    'Nombre comercial del modelo: «IdeaPad 3». Lo que distingue un modelo de otro de la misma marca.';

-- RELLENAR ANTES DE RESTRINGIR, por lo mismo que V8: una columna NOT NULL anadida sobre filas que ya
-- estan FALLA LA MIGRACION y con ella el arranque de la aplicacion.
--
-- Y el relleno es un marcador a la vista, no un dato inventado. Se podria haber usado n_invima, que
-- es el unico texto que estas filas llevan, y seria PEOR: un numero de registro sanitario puesto en
-- la columna del nombre parece un nombre y no lo es, de modo que nadie lo corregiria. «Sin nombre»
-- salta a la vista la primera vez que alguien abre el listado de modelos, que es lo que se quiere.
-- Es la misma eleccion que V8 hizo poniendo 1 en i_cantidad_datos: la afirmacion mas debil posible.
UPDATE modelo
SET n_nombre_modelo = 'Sin nombre'
WHERE n_nombre_modelo IS NULL;

ALTER TABLE modelo
    ALTER COLUMN n_nombre_modelo SET NOT NULL;

-- Un nombre en blanco es no tener nombre, y la columna NOT NULL sola no lo impide: '' y '   ' pasan.
ALTER TABLE modelo
    ADD CONSTRAINT "CHK_modelo_nombre" CHECK (btrim(n_nombre_modelo) <> '');

-- UNICO POR equipo Y NO EN TODA LA TABLA, y la diferencia importa: «Serie 3» puede ser de dos marcas
-- distintas, y la marca vive en equipo. Dos modelos activos con el mismo nombre en la misma
-- combinacion marca-tipo son el mismo modelo dos veces.
--
-- PARCIAL, como los otros cuatro de este esquema: aqui nada se borra, de modo que un modelo retirado
-- se queda en la tabla y una restriccion normal impediria volver a dar de alta uno con su nombre.
CREATE UNIQUE INDEX "UQ_modelo_nombre_activo_por_equipo"
    ON modelo (k_id_equipo, n_nombre_modelo)
    WHERE b_estado_activo;
