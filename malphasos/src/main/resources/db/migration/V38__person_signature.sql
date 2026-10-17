-- V38: la firma del ingeniero vive en su cuenta (2026-10-17).
--
-- Decidido por el usuario con la versión 1.0.0: quien recibe el servicio firma en la pantalla, pero el
-- ingeniero no firma cada servicio. Dibuja su firma una vez, se guarda con su persona, y cada informe que
-- cierra la copia. Solo la cambia su dueño. Cambiarla no altera los informes ya cerrados: la copia se
-- hace al cerrar, como todo lo congelado (V37).
--
-- Una por persona, en PNG —es lo que produce el lienzo de firma— y de hasta 256 KB.

CREATE TABLE firma_persona
(
    k_id_persona  uuid      NOT NULL,
    b_imagen      bytea     NOT NULL,
    f_actualizada timestamp NOT NULL,

    CONSTRAINT "PK_firma_persona" PRIMARY KEY (k_id_persona),
    CONSTRAINT "FK_firma_persona_persona" FOREIGN KEY (k_id_persona) REFERENCES persona (k_identificador),
    CONSTRAINT "CHK_firma_persona_imagen" CHECK (octet_length(b_imagen) BETWEEN 1 AND 262144)
);

COMMENT ON TABLE firma_persona IS
    'La firma de una persona de la casa, dibujada una vez: los informes que cierra la copian al cerrar.';

-- Y el almacén de V37 deja de ser solo de logos: la firma del ingeniero que un informe congela se guarda
-- igual, una vez por versión y por su huella. Se renombra en vez de crear una segunda tabla gemela.
ALTER TABLE logo_documento RENAME TO imagen_documento;
ALTER TABLE imagen_documento RENAME CONSTRAINT "PK_logo_documento" TO "PK_imagen_documento";
ALTER TABLE imagen_documento RENAME CONSTRAINT "CHK_logo_documento_huella" TO "CHK_imagen_documento_huella";
ALTER TABLE imagen_documento RENAME CONSTRAINT "CHK_logo_documento_tipo" TO "CHK_imagen_documento_tipo";
ALTER TABLE imagen_documento RENAME CONSTRAINT "CHK_logo_documento_contenido" TO "CHK_imagen_documento_contenido";

COMMENT ON TABLE imagen_documento IS
    'Cada versión de una imagen que algún informe congeló —el logo de la empresa, la firma del ingeniero—, una sola vez, por la huella SHA-256 de sus bytes.';
