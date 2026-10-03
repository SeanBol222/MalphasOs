-- QUE SE VERIFICA, y no solo con que valores: un equipo puede verificarse en varias magnitudes.
--
-- POR QUE EXISTE ESTA MIGRACION. V8 dio por supuesto que un tipo de equipo se verifica en UNA sola
-- cosa: puso la modalidad y la cantidad de lecturas como columnas de tipo_equipo, y la unidad en cada
-- punto. Un termohigrometro lo desmiente: mide temperatura Y humedad relativa, con unidades distintas
-- -grados y por ciento-, con puntos distintos -0 grados no es 40 por ciento- y a veces con modalidades
-- distintas. Con el modelo de V8 habia que registrarlo como dos tipos de equipo, que es mentira: es un
-- aparato, con una hoja de vida y un reporte.
--
-- LO QUE ANADE ES UN NIVEL EN MEDIO. Antes: tipo_equipo -> punto_verificacion. Ahora:
--
--     tipo_equipo -> verificacion_tipo_equipo -> punto_verificacion
--
-- y lo que era del tipo pasa a ser de cada verificacion: la modalidad, la cantidad de lecturas y la
-- unidad. Un punto se queda con lo unico que es suyo, el valor.
--
-- ES DESTRUCTIVA SI HAY DATOS, Y SE NIEGA A CORRER EN ESE CASO. Reestructurar punto_verificacion
-- obliga a rehacerla, y no hay forma automatica de repartir puntos ya registrados entre magnitudes que
-- nadie declaro. En vez de adivinar -o de borrar en silencio- hay una guarda al principio que tumba el
-- arranque con un mensaje explicito. Migrar una instalacion con datos reales es OTRA migracion, que
-- tendria que preguntar a que magnitud pertenece cada punto existente.

-- ---------------------------------------------------------------------------------------------------
-- 0. La guarda.
-- ---------------------------------------------------------------------------------------------------
-- Cuenta las dos tablas que esta migracion rehace. Si alguna tiene filas, falla aqui y no a medias:
-- Flyway envuelve cada migracion en una transaccion, de modo que el esquema queda como estaba.
DO
$$
    DECLARE
        puntos  bigint;
        lecturas bigint;
    BEGIN
        SELECT count(*) INTO puntos FROM punto_verificacion;
        SELECT count(*) INTO lecturas FROM dato_verificacion;

        IF puntos > 0 OR lecturas > 0 THEN
            RAISE EXCEPTION
                'V10 reestructura punto_verificacion y no puede conservar datos existentes: hay % punto(s) y % lectura(s). Migrar datos reales exige decidir a que magnitud pertenece cada punto, y eso no se puede deducir.',
                puntos, lecturas;
        END IF;
    END
$$;

-- ---------------------------------------------------------------------------------------------------
-- 1. Los dos catalogos.
-- ---------------------------------------------------------------------------------------------------
-- POR QUE CATALOGO Y NO TEXTO LIBRE, que era lo que habia. Dos razones, y la segunda es la que pesa.
-- Una: el mismo concepto se escribiria distinto en cada tipo de equipo -'Temperatura', 'temp',
-- 'TEMPERATURA'- y eso se imprime en el reporte que firma el cliente. Dos: el indice que impide
-- declarar dos veces el mismo punto compara la unidad COMO TEXTO, de modo que con texto libre los
-- grados Celsius escritos con el signo de grado (U+00B0) y con el indicador ordinal masculino (U+00BA)
-- son dos unidades distintas para la base de datos y la MISMA a la vista. Un catalogo lo hace
-- imposible de teclear.
--
-- SE SIEMBRAN Y NO TIENEN PANTALLA QUE LOS CREE, igual que los 249 paises y las 1350 ciudades de V7:
-- son datos de referencia que una instalacion nueva necesita el primer dia. Queda anotado como deuda
-- -administrarlos desde la aplicacion-, no como olvido.
CREATE TABLE magnitud
(
    k_id_magnitud     uuid        NOT NULL,
    -- La llave natural, como el codigo ISO de un pais: estable, sin acentos y citable desde el codigo.
    n_codigo_magnitud varchar(30) NOT NULL,
    n_nombre_magnitud varchar(50) NOT NULL,
    b_estado_activo   boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_magnitud" PRIMARY KEY (k_id_magnitud),
    CONSTRAINT "UQ_magnitud_codigo" UNIQUE (n_codigo_magnitud),
    CONSTRAINT "UQ_magnitud_nombre" UNIQUE (n_nombre_magnitud),
    CONSTRAINT "CHK_magnitud_codigo" CHECK (btrim(n_codigo_magnitud) <> ''),
    CONSTRAINT "CHK_magnitud_nombre" CHECK (btrim(n_nombre_magnitud) <> '')
);

COMMENT ON TABLE magnitud IS
    'Que se mide en una verificacion metrologica: temperatura, presion, humedad relativa. Datos de referencia.';

CREATE TABLE unidad_medida
(
    k_id_unidad_medida uuid        NOT NULL,
    -- Cada unidad pertenece a UNA magnitud, y es lo que permite ofrecer solo las de la magnitud
    -- elegida: quien verifica temperatura no deberia ver %HR en la lista.
    k_id_magnitud      uuid        NOT NULL,
    -- Lo que se imprime junto al numero.
    n_simbolo_unidad   varchar(20) NOT NULL,
    -- Para la lista desplegable y para el reporte extenso: 'grado Celsius' se lee, '°C' se imprime.
    n_nombre_unidad    varchar(50) NOT NULL,
    b_estado_activo    boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_unidad_medida" PRIMARY KEY (k_id_unidad_medida),
    CONSTRAINT "FK_unidad_medida_magnitud"
        FOREIGN KEY (k_id_magnitud) REFERENCES magnitud (k_id_magnitud),

    -- EL SIMBOLO ES UNICO DENTRO DE SU MAGNITUD Y NO EN TODA LA TABLA, y la siembra de mas abajo lo
    -- demuestra: '%' es a la vez humedad relativa y concentracion. Una unicidad global habria obligado
    -- a inventarse un simbolo falso para uno de los dos.
    CONSTRAINT "UQ_unidad_medida_simbolo_por_magnitud" UNIQUE (k_id_magnitud, n_simbolo_unidad),
    CONSTRAINT "CHK_unidad_medida_simbolo" CHECK (btrim(n_simbolo_unidad) <> ''),
    CONSTRAINT "CHK_unidad_medida_nombre" CHECK (btrim(n_nombre_unidad) <> '')
);

-- EL DESTINO DE UNA FORANEA COMPUESTA, y es el motivo de que exista. Una restriccion unica sobre
-- (magnitud, unidad) permite que verificacion_tipo_equipo apunte a las DOS columnas a la vez, y con eso
-- el esquema impide por si solo elegir %HR para una verificacion de temperatura. Sin esto seria una
-- regla mas que el servicio tendria que comprobar, y una regla que el esquema puede expresar va al
-- esquema.
ALTER TABLE unidad_medida
    ADD CONSTRAINT "UQ_unidad_medida_por_magnitud" UNIQUE (k_id_magnitud, k_id_unidad_medida);

COMMENT ON TABLE unidad_medida IS
    'Unidades de cada magnitud. El simbolo es unico dentro de la magnitud, no en toda la tabla.';

-- ---------------------------------------------------------------------------------------------------
-- 2. La siembra.
-- ---------------------------------------------------------------------------------------------------
-- IDEMPOTENTE, por lo mismo que V7: ON CONFLICT DO NOTHING sin nombrar la restriccion, de modo que
-- cubre codigo y nombre a la vez y no pisa lo que alguien haya anadido a mano.
--
-- EL CONJUNTO ES UN PUNTO DE PARTIDA, no una tabla cerrada de metrologia. Cubre lo que se verifica en
-- equipamiento biomedico -tensiometros, termohigrometros, balanzas, incubadoras, centrifugas, bombas
-- de infusion, oximetros- y crece anadiendo filas.
INSERT INTO magnitud (k_id_magnitud, n_codigo_magnitud, n_nombre_magnitud)
SELECT gen_random_uuid(), datos.codigo, datos.nombre
FROM (VALUES ('temperatura', 'Temperatura'),
             ('presion', 'Presión'),
             ('humedad_relativa', 'Humedad relativa'),
             ('masa', 'Masa'),
             ('flujo', 'Flujo'),
             ('volumen', 'Volumen'),
             ('tiempo', 'Tiempo'),
             ('frecuencia', 'Frecuencia'),
             ('velocidad_rotacion', 'Velocidad de rotación'),
             ('corriente_electrica', 'Corriente eléctrica'),
             ('tension_electrica', 'Tensión eléctrica'),
             ('resistencia_electrica', 'Resistencia eléctrica'),
             ('potencia', 'Potencia'),
             ('energia', 'Energía'),
             ('longitud', 'Longitud'),
             ('concentracion', 'Concentración'),
             ('saturacion_oxigeno', 'Saturación de oxígeno'),
             ('acidez', 'Acidez'),
             ('iluminancia', 'Iluminancia'),
             ('conductividad', 'Conductividad eléctrica')) AS datos(codigo, nombre)
ON CONFLICT DO NOTHING;

INSERT INTO unidad_medida (k_id_unidad_medida, k_id_magnitud, n_simbolo_unidad, n_nombre_unidad)
SELECT gen_random_uuid(), magnitud.k_id_magnitud, datos.simbolo, datos.nombre
FROM (VALUES ('temperatura', '°C', 'grado Celsius'),
             ('temperatura', '°F', 'grado Fahrenheit'),
             ('temperatura', 'K', 'kelvin'),
             ('presion', 'mmHg', 'milímetro de mercurio'),
             ('presion', 'kPa', 'kilopascal'),
             ('presion', 'psi', 'libra por pulgada cuadrada'),
             ('presion', 'bar', 'bar'),
             ('presion', 'cmH2O', 'centímetro de agua'),
             -- Las dos formas de escribir lo mismo, y las dos se usan. Es la fila que justifica que el
             -- simbolo sea unico por magnitud: '%' vuelve a aparecer mas abajo en concentracion.
             ('humedad_relativa', '%HR', 'por ciento de humedad relativa'),
             ('humedad_relativa', '%', 'por ciento de humedad relativa'),
             ('masa', 'mg', 'miligramo'),
             ('masa', 'g', 'gramo'),
             ('masa', 'kg', 'kilogramo'),
             ('flujo', 'L/min', 'litro por minuto'),
             ('flujo', 'mL/min', 'mililitro por minuto'),
             ('flujo', 'mL/h', 'mililitro por hora'),
             ('volumen', 'µL', 'microlitro'),
             ('volumen', 'mL', 'mililitro'),
             ('volumen', 'L', 'litro'),
             ('tiempo', 's', 'segundo'),
             ('tiempo', 'min', 'minuto'),
             ('tiempo', 'h', 'hora'),
             ('frecuencia', 'Hz', 'hercio'),
             ('frecuencia', 'lpm', 'latido por minuto'),
             ('frecuencia', 'rpm_resp', 'respiración por minuto'),
             ('velocidad_rotacion', 'rpm', 'revolución por minuto'),
             ('corriente_electrica', 'µA', 'microamperio'),
             ('corriente_electrica', 'mA', 'miliamperio'),
             ('corriente_electrica', 'A', 'amperio'),
             ('tension_electrica', 'mV', 'milivoltio'),
             ('tension_electrica', 'V', 'voltio'),
             ('resistencia_electrica', 'Ω', 'ohmio'),
             ('resistencia_electrica', 'kΩ', 'kiloohmio'),
             ('potencia', 'W', 'vatio'),
             ('potencia', 'kW', 'kilovatio'),
             ('energia', 'J', 'julio'),
             ('longitud', 'mm', 'milímetro'),
             ('longitud', 'cm', 'centímetro'),
             ('longitud', 'm', 'metro'),
             ('concentracion', '%', 'por ciento'),
             ('concentracion', 'ppm', 'parte por millón'),
             ('concentracion', 'mg/L', 'miligramo por litro'),
             ('saturacion_oxigeno', '%SpO2', 'por ciento de saturación'),
             ('acidez', 'pH', 'unidad de pH'),
             ('iluminancia', 'lx', 'lux'),
             ('conductividad', 'µS/cm', 'microsiemens por centímetro')) AS datos(magnitud, simbolo, nombre)
         JOIN magnitud ON magnitud.n_codigo_magnitud = datos.magnitud
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------------------------------
-- 3. El nivel nuevo.
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE verificacion_tipo_equipo
(
    k_id_verificacion        uuid        NOT NULL,
    k_id_tipo_equipo         uuid        NOT NULL,
    k_id_magnitud            uuid        NOT NULL,
    k_id_unidad_medida       uuid        NOT NULL,
    -- La modalidad baja de tipo_equipo a aqui. En un termohigrometro la temperatura puede verificarse
    -- contra un patron constante y la humedad con patron y equipo variables: en V8 habia que elegir una
    -- sola para todo el aparato.
    n_modalidad_verificacion varchar(50) NOT NULL,
    -- La cantidad de lecturas tambien baja: de cada punto de temperatura pueden tomarse tres lecturas y
    -- de cada punto de humedad una.
    i_cantidad_datos         integer     NULL,
    b_estado_activo          boolean     NOT NULL DEFAULT true,

    CONSTRAINT "PK_verificacion_tipo_equipo" PRIMARY KEY (k_id_verificacion),
    CONSTRAINT "FK_verificacion_tipo_equipo_tipo"
        FOREIGN KEY (k_id_tipo_equipo) REFERENCES tipo_equipo (k_id_tipo_equipo),

    -- LA FORANEA COMPUESTA, el motivo de la restriccion unica de mas arriba. Apuntando a las dos
    -- columnas a la vez, el esquema garantiza que la unidad elegida PERTENECE a la magnitud declarada:
    -- '%HR' para una verificacion de temperatura es imposible de escribir, no solo de rechazar.
    CONSTRAINT "FK_verificacion_tipo_equipo_unidad_de_su_magnitud"
        FOREIGN KEY (k_id_magnitud, k_id_unidad_medida)
            REFERENCES unidad_medida (k_id_magnitud, k_id_unidad_medida),

    CONSTRAINT "CHK_verificacion_modalidad"
        CHECK (n_modalidad_verificacion IN
               ('patron_constante', 'equipo_constante', 'patron_equipo_variable')),

    -- VA CON CASE Y NO CON RAMAS UNIDAS POR OR. Es la trampa que este proyecto ya pago en V8: un CHECK
    -- se satisface cuando NO es falso, de modo que NULL lo satisface y una comparacion con NULL deja
    -- pasar justo el estado que se queria prohibir. Con CASE el resultado es siempre TRUE o FALSE.
    --
    -- Aqui, al contrario que en V8, la modalidad es NOT NULL -una verificacion que no declara como se
    -- hace no es una verificacion-, de modo que el ELSE solo cubre la modalidad variable. El tope de
    -- 100 no es metrologia: es un atajo contra el dedo.
    CONSTRAINT "CHK_verificacion_cantidad_datos"
        CHECK (CASE
                   WHEN n_modalidad_verificacion IN ('patron_constante', 'equipo_constante')
                       THEN i_cantidad_datos IS NOT NULL AND i_cantidad_datos BETWEEN 1 AND 100
                   ELSE i_cantidad_datos IS NULL
               END)
);

-- Un tipo de equipo no declara dos veces la misma magnitud: dos verificaciones de temperatura en el
-- mismo aparato son la misma verificacion escrita dos veces. PARCIAL, como todos los de este esquema:
-- una restriccion normal impediria volver a declarar una magnitud que se retiro, y aqui nada se borra.
CREATE UNIQUE INDEX "UQ_verificacion_magnitud_activa_por_tipo"
    ON verificacion_tipo_equipo (k_id_tipo_equipo, k_id_magnitud)
    WHERE b_estado_activo;

-- El destino de la foranea compuesta que dato_verificacion necesita mas abajo: sin esto, una lectura no
-- podria exigir que su punto pertenezca a su verificacion.
ALTER TABLE verificacion_tipo_equipo
    ADD CONSTRAINT "UQ_verificacion_por_tipo" UNIQUE (k_id_tipo_equipo, k_id_verificacion);

COMMENT ON TABLE verificacion_tipo_equipo IS
    'Que magnitud se verifica en un tipo de equipo, con que unidad, con que modalidad y cuantas lecturas por punto.';

-- ---------------------------------------------------------------------------------------------------
-- 4. punto_verificacion, rehecha.
-- ---------------------------------------------------------------------------------------------------
-- SE REHACE EN VEZ DE ALTERARSE. Cambia su padre -del tipo a la verificacion-, pierde una columna
-- NOT NULL y cambia su indice unico: en ALTER serian seis sentencias que dejan el mismo resultado y se
-- leen peor. La guarda del principio ya garantizo que no hay nada que perder.
ALTER TABLE dato_verificacion
    DROP CONSTRAINT "FK_dato_verificacion_punto";

DROP TABLE punto_verificacion;

CREATE TABLE punto_verificacion
(
    k_id_punto_verificacion uuid           NOT NULL,
    -- Cuelga de la verificacion y no del tipo de equipo: un punto de 50 mmHg no significa nada suelto
    -- en un aparato que mide presion Y temperatura.
    k_id_verificacion       uuid           NOT NULL,
    -- numeric y no double: una lectura metrologica se compara y se imprime, y el redondeo binario de un
    -- double convierte 0,1 en 0,09999999. Cuatro decimales cubren desde mmHg hasta mA.
    d_valor                 numeric(12, 4) NOT NULL,
    b_estado_activo         boolean        NOT NULL DEFAULT true,

    CONSTRAINT "PK_punto_verificacion" PRIMARY KEY (k_id_punto_verificacion),
    CONSTRAINT "FK_punto_verificacion_verificacion"
        FOREIGN KEY (k_id_verificacion) REFERENCES verificacion_tipo_equipo (k_id_verificacion)

    -- YA NO TIENE n_unidad, y es el cambio que pedia el usuario: la unidad era la misma en los tres
    -- puntos de una verificacion y habia que teclearla tres veces. Ahora la declara la verificacion y
    -- el punto la hereda, de modo que un punto no puede contradecir a su hermano.
    --
    -- NO hay CHECK de positividad sobre el valor, igual que en V8: un congelador se verifica a -20 °C.
);

-- Dos puntos activos con el mismo valor en la misma verificacion son el mismo punto dos veces. Ya no
-- entra la unidad en la clave, porque ya no es del punto. PARCIAL por lo mismo de siempre: los puntos
-- retirados se quedan en la tabla y tienen que poder repetirse.
CREATE UNIQUE INDEX "UQ_punto_verificacion_activo"
    ON punto_verificacion (k_id_verificacion, d_valor)
    WHERE b_estado_activo;

-- El destino de la foranea compuesta de dato_verificacion.
ALTER TABLE punto_verificacion
    ADD CONSTRAINT "UQ_punto_por_verificacion" UNIQUE (k_id_verificacion, k_id_punto_verificacion);

COMMENT ON TABLE punto_verificacion IS
    'Valores constantes de una verificacion. La unidad la declara la verificacion, no el punto.';

-- ---------------------------------------------------------------------------------------------------
-- 5. dato_verificacion aprende a que verificacion pertenece.
-- ---------------------------------------------------------------------------------------------------
-- EL DEFECTO QUE ESTE CAMBIO CREA, Y QUE SE ARREGLA AQUI. V9 dejo el punto anulable a proposito: con
-- modalidad variable no hay puntos declarados y las lecturas existen igual. Eso no era ambiguo porque
-- habia UNA modalidad por tipo de equipo, de modo que una lectura sin punto solo podia ser de la unica
-- verificacion que existia. Con la modalidad por verificacion, un termohigrometro con las dos
-- magnitudes variables produce lecturas sin punto que NO se pueden atribuir: el reporte no sabe en que
-- columna imprimirlas. La verificacion pasa a ser obligatoria.
ALTER TABLE dato_verificacion
    ADD COLUMN k_id_verificacion uuid NOT NULL;

COMMENT ON COLUMN dato_verificacion.k_id_verificacion IS
    'A que verificacion del tipo pertenece la lectura. Obligatoria: con modalidad variable no hay punto que lo diga.';

ALTER TABLE dato_verificacion
    ADD CONSTRAINT "FK_dato_verificacion_verificacion"
        FOREIGN KEY (k_id_verificacion) REFERENCES verificacion_tipo_equipo (k_id_verificacion);

-- LO QUE V9 NO PODIA COMPROBAR Y AHORA SI. Su comentario decia que el esquema no puede exigir que el
-- punto pertenezca al tipo del equipo reportado porque son cuatro saltos hasta tipo_equipo. Eso sigue
-- siendo verdad para el equipo, y lo comprueba el servicio; pero que el punto pertenezca a SU
-- VERIFICACION es un solo salto, y una foranea compuesta lo impone.
--
-- Y SIGUE ADMITIENDO EL PUNTO NULO sin una regla extra: una foranea compuesta con alguna columna nula
-- no se comprueba -- es el comportamiento MATCH SIMPLE, el que PostgreSQL usa por omision --, de modo
-- que la lectura sin punto pasa y la que trae punto queda atada a su verificacion.
ALTER TABLE dato_verificacion
    ADD CONSTRAINT "FK_dato_verificacion_punto_de_su_verificacion"
        FOREIGN KEY (k_id_verificacion, k_id_punto_verificacion)
            REFERENCES punto_verificacion (k_id_verificacion, k_id_punto_verificacion);

-- EL INDICE UNICO DE LAS LECTURAS TAMBIEN SE QUEDO CORTO, y es la segunda mitad del mismo defecto.
-- V9 lo declaro sobre (reporte, punto, secuencia) con NULLS NOT DISTINCT, para que la modalidad
-- variable -- la que deja el punto nulo -- no fuera la unica que admitiera duplicados. Correcto
-- entonces, insuficiente ahora: con dos verificaciones variables en el mismo tipo, la lectura numero 1
-- de temperatura y la numero 1 de humedad tienen las dos el punto nulo y el mismo numero, de modo que
-- la segunda choca contra la primera aunque midan cosas distintas. Entra la verificacion en la clave.
DROP INDEX "UQ_dato_verificacion_activo";

CREATE UNIQUE INDEX "UQ_dato_verificacion_activo"
    ON dato_verificacion (k_id_reporte_servicio, k_id_verificacion, k_id_punto_verificacion, i_secuencia)
    NULLS NOT DISTINCT
    WHERE b_estado_activo;

-- ---------------------------------------------------------------------------------------------------
-- 6. tipo_equipo se queda sin lo que ya no es suyo.
-- ---------------------------------------------------------------------------------------------------
-- LAS TRES COLUMNAS SE VAN, y b_verificable no se sustituye por nada. V5 la ato a la modalidad con un
-- CHECK que obliga a que vayan juntas, de modo que el booleano era exactamente
-- 'n_tipo_verificacion IS NOT NULL': redundante por construccion. Ahora 'se verifica' es 'tiene al
-- menos una verificacion activa', que se cuenta y no se puede desincronizar.
--
-- Los dos CHECK caen con las columnas, pero se nombran para que se vea que no se olvidaron.
ALTER TABLE tipo_equipo
    DROP CONSTRAINT "CHK_tipo_equipo_modalidad_segun_verificable",
    DROP CONSTRAINT "CHK_tipo_equipo_verificacion",
    DROP CONSTRAINT "CHK_tipo_equipo_cantidad_datos",
    DROP COLUMN b_verificable,
    DROP COLUMN n_tipo_verificacion,
    DROP COLUMN i_cantidad_datos;
