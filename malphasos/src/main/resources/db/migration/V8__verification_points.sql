-- Cuantos datos se toman en una verificacion, y en que valores constantes se toman.
--
-- Es el primer trozo de la segunda tanda de equipment, la de verificaciones tecnicas y datos
-- metrologicos, que el modulo dejo anunciada desde el 2026-09-02 -esta escrito en el javadoc de
-- EquipmentType-. Lo pidio el usuario para poder llenar el reporte: sin estos dos datos, un tipo dice
-- COMO se verifica pero no CON QUE ni CUANTAS veces.
--
-- DONDE VIVEN, y por que aqui. En el tipo de equipo y no en cada equipo del cliente: todos los
-- tensiometros de un mismo tipo se verifican igual, la modalidad ya vive en esta tabla, y configurarlo
-- una vez sirve para mil equipos. Si algun dia un equipo concreto necesita desviarse, eso es una
-- columna en equipo_cliente que sobrescribe, no un cambio de este modelo.
--
-- LA CANTIDAD SOLO APLICA A LAS DOS MODALIDADES CONSTANTES, por decision del usuario: cuando patron y
-- equipo varian, cuantas lecturas tomar lo decide el ingeniero en campo. El CHECK lo impone, de modo
-- que el estado «modalidad variable con cantidad fijada» no se puede escribir.
--
-- LOS PUNTOS SON VARIOS, tambien por decision del usuario: una verificacion se hace a 50, a 100 y a
-- 150 mmHg, y en cada punto se toman las N lecturas que dice i_cantidad_datos. Esa es la lectura del
-- modelo: N no es el total de datos, es el numero de datos POR PUNTO.

ALTER TABLE tipo_equipo
    ADD COLUMN i_cantidad_datos integer NULL;

COMMENT ON COLUMN tipo_equipo.i_cantidad_datos IS
    'Lecturas que se toman en cada punto de verificacion. Solo para las modalidades constantes.';

-- Rellenar antes de restringir, porque el CHECK se aplica tambien a lo que ya estaba: en una
-- instalacion con tipos constantes ya registrados, anadirlo sin esto haria FALLAR LA MIGRACION y con
-- ella el arranque de la aplicacion. En el equipo de desarrollo la tabla esta vacia y esto no toca
-- nada; existe para las demas.
--
-- Se pone 1 y no 3 ni 5: es la afirmacion mas debil posible -una lectura por punto- y cualquier valor
-- mayor seria inventarse una practica que nadie declaro. Un tipo configurado con una sola lectura salta
-- a la vista la primera vez que alguien abre su ficha, que es justo lo que se quiere.
UPDATE tipo_equipo
SET i_cantidad_datos = 1
WHERE n_tipo_verificacion IN ('patron_constante', 'equipo_constante')
  AND i_cantidad_datos IS NULL;

-- VA CON CASE Y NO CON TRES RAMAS UNIDAS POR OR, y la razon es la trampa de SQL que este proyecto
-- acaba de pagar: un CHECK se satisface cuando NO es falso, de modo que NULL lo satisface. La primera
-- version era
--
--     CHECK ((n_tipo_verificacion IN ('patron_constante','equipo_constante') AND ...)
--         OR (n_tipo_verificacion = 'patron_equipo_variable' AND i_cantidad_datos IS NULL)
--         OR (n_tipo_verificacion IS NULL AND i_cantidad_datos IS NULL))
--
-- y para un tipo NO verificable con cantidad fijada daba NULL OR NULL OR FALSE = NULL: pasaba. Lo
-- delato la prueba escrita justo para esa rama. Con CASE, la comparacion con NULL cae en el ELSE y el
-- resultado es siempre TRUE o FALSE.
--
-- El tope de 100 no es metrologia, es un atajo contra el dedo: nadie toma mil lecturas de un punto, y
-- un 1000 tecleado por error se descubriria al imprimir el reporte.
ALTER TABLE tipo_equipo
    ADD CONSTRAINT "CHK_tipo_equipo_cantidad_datos"
        CHECK (CASE
                   WHEN n_tipo_verificacion IN ('patron_constante', 'equipo_constante')
                       THEN i_cantidad_datos IS NOT NULL AND i_cantidad_datos BETWEEN 1 AND 100
                   ELSE i_cantidad_datos IS NULL
               END);

CREATE TABLE punto_verificacion
(
    k_id_punto_verificacion uuid           NOT NULL,
    k_id_tipo_equipo        uuid           NOT NULL,
    -- El valor en el que se mantiene constante el patron o el equipo, segun la modalidad.
    --
    -- numeric y no double: una lectura metrologica se compara y se imprime, y el redondeo binario de
    -- un double convierte 0,1 en 0,09999999. Cuatro decimales cubren desde mmHg hasta mA.
    d_valor                 numeric(12, 4) NOT NULL,
    -- La unidad va junto al valor y no se sobreentiende del tipo: un numero pelado no se puede
    -- escribir en un reporte, y el mismo tipo de equipo mide en unidades distintas segun el fabricante.
    n_unidad                varchar(20)    NOT NULL,
    b_estado_activo         boolean        NOT NULL DEFAULT true,

    CONSTRAINT "PK_punto_verificacion" PRIMARY KEY (k_id_punto_verificacion),
    CONSTRAINT "FK_punto_verificacion_tipo_equipo"
        FOREIGN KEY (k_id_tipo_equipo) REFERENCES tipo_equipo (k_id_tipo_equipo),

    -- NO hay CHECK de positividad sobre el valor, y es deliberado: un congelador se verifica a -20 °C.
    -- Exigir un valor positivo aqui habria dejado fuera media cadena de frio.
    CONSTRAINT "CHK_punto_verificacion_unidad" CHECK (btrim(n_unidad) <> '')
);

-- Dos puntos activos con el mismo valor y la misma unidad en el mismo tipo son el mismo punto dos
-- veces. El indice es PARCIAL a proposito: una restriccion normal impediria volver a dar de alta un
-- punto que se retiro, y aqui nada se borra, de modo que los retirados se quedan en la tabla.
CREATE UNIQUE INDEX "UQ_punto_verificacion_activo"
    ON punto_verificacion (k_id_tipo_equipo, d_valor, n_unidad)
    WHERE b_estado_activo;

COMMENT ON TABLE punto_verificacion IS
    'Valores constantes en los que se verifica un tipo de equipo. En cada uno se toman i_cantidad_datos lecturas.';
