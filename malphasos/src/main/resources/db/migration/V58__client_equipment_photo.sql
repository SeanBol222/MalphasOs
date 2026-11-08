-- La foto de cada equipo del cliente, para su hoja de vida (2026-11-08).
--
-- Decidido por el usuario: una foto por unidad —no por modelo—, solo PNG, guardada en la base y no en
-- disco. La imagen vive en imagen_documento, el almacén común del logo y las firmas (V45), una vez por su
-- huella; esta tabla dice de qué equipo es, como firma_persona dice de quién es una firma. Va aparte de
-- equipo_cliente para que el equipo, sus listados y sus búsquedas no carguen nunca los bytes: solo los lee
-- la hoja de vida.
--
-- QUITAR LA FOTO BORRA ESTA FILA, no la imagen: el almacén no borra, porque una imagen puede estar en un
-- documento congelado. Tampoco hay borrado lógico, como en firma_persona: una foto quitada no es historia
-- de nada. El tipo, PNG, lo comprueba el dominio (EquipmentPhoto) y el tamaño, el almacén (512 KB).
--
-- QUÉ PASA CON LOS DATOS: nada. La tabla nace vacía y cada equipo sigue sin foto hasta que se le suba.

CREATE TABLE foto_equipo_cliente
(
    k_id_equipo_cliente uuid        NOT NULL,
    k_huella            varchar(64) NOT NULL,
    f_actualizada       timestamp   NOT NULL,

    CONSTRAINT "PK_foto_equipo_cliente" PRIMARY KEY (k_id_equipo_cliente),
    CONSTRAINT "FK_foto_equipo_cliente_equipo" FOREIGN KEY (k_id_equipo_cliente)
        REFERENCES equipo_cliente (k_id_equipo_cliente),
    CONSTRAINT "FK_foto_equipo_cliente_imagen" FOREIGN KEY (k_huella) REFERENCES imagen_documento (k_huella)
);

CREATE INDEX "IX_foto_equipo_cliente_imagen" ON foto_equipo_cliente (k_huella);

COMMENT ON TABLE foto_equipo_cliente IS
    'La foto de un equipo del cliente para su hoja de vida: la huella de un PNG en imagen_documento. Una por equipo.';
COMMENT ON COLUMN foto_equipo_cliente.f_actualizada IS
    'Cuándo se subió la foto que tiene hoy.';
