-- El voltaje de un modelo admite decimales (2026-11-07). Un termómetro a pila es de 1,5 V, y con un entero
-- la hoja de vida decía 1 V: lo destapó la carga de los informes de la aplicación anterior, que traen
-- «1,5v». Pasa a numeric(8,2), como el amperaje, y el prefijo de la columna cambia con su tipo.
--
-- QUÉ PASA CON LOS DATOS. Nada se pierde: todo entero cabe en numeric(8,2), y 110 sigue siendo 110. El
-- CHECK de V15 se rehace sobre la columna nueva, igual que estaba. Los documentos congelados de los
-- reportes cerrados (V37) guardan el voltaje en su JSON como entero, y se siguen leyendo: un entero es
-- un decimal válido.

ALTER TABLE modelo DROP CONSTRAINT "CHK_modelo_voltaje";

ALTER TABLE modelo ALTER COLUMN i_voltaje TYPE numeric(8,2);

ALTER TABLE modelo RENAME COLUMN i_voltaje TO d_voltaje;

ALTER TABLE modelo
    ADD CONSTRAINT "CHK_modelo_voltaje" CHECK (d_voltaje IS NULL OR d_voltaje > 0);

COMMENT ON COLUMN modelo.d_voltaje IS
    'Voltaje nominal en V, con dos decimales desde V57: un equipo a pila es de 1,5 V. Vivia en tipo_equipo hasta V15, de donde se copio a cada modelo.';
