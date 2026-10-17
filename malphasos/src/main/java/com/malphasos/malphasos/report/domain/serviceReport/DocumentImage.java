package com.malphasos.malphasos.report.domain.serviceReport;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Una imagen que un informe congela —el logo de la empresa, la firma del ingeniero— (2026-10-17): sus
 * bytes y su tipo, ya validados por quien la produjo. Se guarda una vez por versión, por su huella.
 */
public record DocumentImage(byte[] contenido, String tipo) {

    public DocumentImage {
        if (contenido == null || contenido.length == 0) {
            throw new IllegalArgumentException("Una imagen del informe no puede estar vacía");
        }
        Objects.requireNonNull(tipo, "Una imagen del informe lleva su tipo");
        contenido = contenido.clone();
    }

    @Override
    public byte[] contenido() {
        return contenido.clone();
    }

    /** SHA-256 de sus bytes, en hexadecimal: dos imágenes iguales tienen la misma. */
    public String huella() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no trae SHA-256, que el estándar exige", e);
        }
    }

    public String dataUri() {
        return "data:" + tipo + ";base64," + Base64.getEncoder().encodeToString(contenido);
    }

    @Override
    public boolean equals(Object otra) {
        return otra instanceof DocumentImage imagen && tipo.equals(imagen.tipo)
                && Arrays.equals(contenido, imagen.contenido);
    }

    @Override
    public int hashCode() {
        return 31 * tipo.hashCode() + Arrays.hashCode(contenido);
    }

    @Override
    public String toString() {
        return "DocumentImage[" + tipo + ", " + contenido.length + " bytes]";
    }
}
