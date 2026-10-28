-- V45: el logo de la empresa y la firma de cada persona, por huella (2026-10-27).
--
-- Grupo C1 del análisis del esquema (Documentation/DatabaseDesign/analisis-2026-10-27.md), decidido por
-- el usuario. Había tres formas de guardar una imagen: los bytes en empresa.b_logo con su tipo al lado,
-- los bytes en firma_persona.b_imagen, y imagen_documento, que guarda cada versión una vez por la huella
-- SHA-256 de sus bytes desde V37. Las dos primeras pasan a la tercera: la empresa y la firma apuntan a una
-- huella, y la imagen vive una sola vez aunque la use un informe congelado, la empresa y diez reportes.
--
-- imagen_documento PASA A SER DE TODOS, no del informe. En el código se movió del módulo report a shared:
-- company y person no pueden depender de report, que ya depende de los dos.
--
-- LOS LÍMITES QUE SE PIERDEN EN EL ESQUEMA, y dónde quedan. empresa exigía tipo con el logo y firma_persona
-- un máximo de 256 KB. El tipo lo lleva ahora la imagen, con su CHECK de V37; los tamaños —512 KB el logo,
-- 256 KB la firma— los comprueban CompanyLogo y PersonSignature en el dominio, que ya lo hacían, y el
-- almacén no admite nada de más de 512 KB.
--
-- UNA IMAGEN NO SE BORRA aunque la empresa cambie de logo o una persona de firma: un informe congelado
-- puede estar apuntándola, y el documento solo lleva la huella. Es lo que V37 ya decidía.

-- Las imágenes que había, una vez por versión. Los bytes son los mismos, así que la huella coincide con
-- la que calcula DocumentImage en Java: la de un logo que ya congeló un informe ya está y no se repite.
INSERT INTO imagen_documento (k_huella, b_contenido, t_tipo)
SELECT encode(sha256(b_logo), 'hex'), b_logo, t_logo_tipo
FROM empresa
WHERE b_logo IS NOT NULL
ON CONFLICT (k_huella) DO NOTHING;

INSERT INTO imagen_documento (k_huella, b_contenido, t_tipo)
SELECT DISTINCT encode(sha256(b_imagen), 'hex'), b_imagen, 'image/png'
FROM firma_persona
ON CONFLICT (k_huella) DO NOTHING;

-- La empresa.
ALTER TABLE empresa ADD COLUMN k_huella_logo varchar(64) NULL;

UPDATE empresa SET k_huella_logo = encode(sha256(b_logo), 'hex') WHERE b_logo IS NOT NULL;

ALTER TABLE empresa DROP CONSTRAINT "CHK_empresa_logo";
ALTER TABLE empresa DROP COLUMN b_logo;
ALTER TABLE empresa DROP COLUMN t_logo_tipo;

ALTER TABLE empresa
    ADD CONSTRAINT "FK_empresa_logo" FOREIGN KEY (k_huella_logo) REFERENCES imagen_documento (k_huella);

CREATE INDEX "IX_empresa_logo" ON empresa (k_huella_logo);

COMMENT ON COLUMN empresa.k_huella_logo IS
    'El logo, en imagen_documento por su huella (V45). Nulo: el membrete es el nombre.';

-- La firma. Obligatoria: una fila de firma_persona es que la persona tiene firma.
ALTER TABLE firma_persona ADD COLUMN k_huella varchar(64) NULL;

UPDATE firma_persona SET k_huella = encode(sha256(b_imagen), 'hex');

ALTER TABLE firma_persona ALTER COLUMN k_huella SET NOT NULL;
ALTER TABLE firma_persona DROP CONSTRAINT "CHK_firma_persona_imagen";
ALTER TABLE firma_persona DROP COLUMN b_imagen;

ALTER TABLE firma_persona
    ADD CONSTRAINT "FK_firma_persona_imagen" FOREIGN KEY (k_huella) REFERENCES imagen_documento (k_huella);

CREATE INDEX "IX_firma_persona_imagen" ON firma_persona (k_huella);

COMMENT ON COLUMN firma_persona.k_huella IS 'La firma, en imagen_documento por su huella (V45).';
