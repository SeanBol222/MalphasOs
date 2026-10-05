-- V15: la ficha tecnica pasa al modelo, y el tipo gana su uso y su limpieza cotidiana.
--
-- PARA QUE. La hoja de vida impresa que aprobo el usuario el 2026-10-05 pide datos que no existian:
-- la clasificacion por riesgo, las caracteristicas especificas, la alimentacion, la potencia y la
-- frecuencia del equipo, y su uso y limpieza cotidiana. Ver la nota hoja-de-vida-formato-impreso.
--
-- LA PARTE QUE NO ES SOLO AÑADIR. Voltaje y amperaje vivian en tipo_equipo desde V5, heredados del
-- original, y son de cada MODELO: dos balanzas de marcas distintas no consumen lo mismo. El usuario
-- decidio que los cinco datos electricos van en el modelo, y partirlos entre dos tablas habria sido lo
-- peor. Es la misma forma que V10, que bajo la modalidad de verificacion del tipo a cada verificacion:
-- un dato que estaba un nivel por encima del que le corresponde.
--
-- QUE PASA CON LOS DATOS. No se pierden: cada modelo recibe el voltaje y el amperaje que su tipo tenia,
-- que es exactamente el valor que ese modelo tenia hasta hoy por herencia. Un modelo cuelga de su tipo
-- a traves de equipo (la asociacion marca-tipo). Despues se corrigen modelo por modelo.

-- 1. La ficha tecnica en el modelo. Todo opcional: un modelo existe antes de que alguien lea su placa.
ALTER TABLE modelo
    ADD COLUMN n_clase_riesgo        varchar(3)   NULL,
    ADD COLUMN t_caracteristicas     varchar(250) NULL,
    ADD COLUMN n_alimentacion        varchar(50)  NULL,
    ADD COLUMN i_voltaje             integer      NULL,
    ADD COLUMN i_potencia            integer      NULL,
    ADD COLUMN d_amperaje            numeric(8,2) NULL,
    ADD COLUMN i_frecuencia          integer      NULL;

-- La clasificacion de dispositivos medicos por riesgo en Colombia (Decreto 4725 de 2005): I, IIa, IIb
-- y III. Se guarda en mayusculas, que es como la escribe el enum.
ALTER TABLE modelo
    ADD CONSTRAINT "CHK_modelo_clase_riesgo"
        CHECK (n_clase_riesgo IS NULL OR n_clase_riesgo IN ('I', 'IIA', 'IIB', 'III')),
    ADD CONSTRAINT "CHK_modelo_voltaje" CHECK (i_voltaje IS NULL OR i_voltaje > 0),
    ADD CONSTRAINT "CHK_modelo_potencia" CHECK (i_potencia IS NULL OR i_potencia > 0),
    ADD CONSTRAINT "CHK_modelo_amperaje" CHECK (d_amperaje IS NULL OR d_amperaje > 0),
    ADD CONSTRAINT "CHK_modelo_frecuencia" CHECK (i_frecuencia IS NULL OR i_frecuencia > 0);

COMMENT ON COLUMN modelo.n_clase_riesgo IS
    'Clasificacion por riesgo del dispositivo medico: I, IIA, IIB o III. Nula mientras no se conozca.';
COMMENT ON COLUMN modelo.i_voltaje IS
    'Voltaje nominal en V. Vivia en tipo_equipo hasta V15, de donde se copio a cada modelo.';
COMMENT ON COLUMN modelo.d_amperaje IS
    'Corriente nominal en A. Vivia en tipo_equipo hasta V15, de donde se copio a cada modelo.';

-- 2. Cada modelo hereda el valor que su tipo tenia. Antes de quitarlo, o se pierde.
UPDATE modelo m
SET i_voltaje  = t.i_voltage,
    d_amperaje = t.d_amperaje
FROM equipo e
         JOIN tipo_equipo t ON t.k_id_tipo_equipo = e.k_id_tipo_equipo
WHERE e.k_id_equipo = m.k_id_equipo;

-- 3. Y el tipo los pierde, con sus CHECK, que ahora viven en el modelo.
ALTER TABLE tipo_equipo
    DROP CONSTRAINT "CHK_tipo_equipo_voltage",
    DROP CONSTRAINT "CHK_tipo_equipo_amperaje",
    DROP COLUMN i_voltage,
    DROP COLUMN d_amperaje;

-- 4. El uso y la limpieza cotidiana son del tipo: todas las balanzas se usan para pesar y se limpian
-- igual, como ya comparten las recomendaciones de cuidado. Opcionales, porque los tipos que ya existen
-- no los tienen.
ALTER TABLE tipo_equipo
    ADD COLUMN t_uso                 varchar(250) NULL,
    ADD COLUMN t_limpieza_cotidiana  varchar(250) NULL;
