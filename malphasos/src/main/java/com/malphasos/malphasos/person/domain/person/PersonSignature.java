package com.malphasos.malphasos.person.domain.person;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * La firma de una persona de la casa, dibujada una vez (V38, 2026-10-17). Decidido por el usuario: el
 * ingeniero no firma cada servicio; su firma vive en su cuenta y cada informe que cierra la copia.
 *
 * <p>Un PNG, que es lo que produce el lienzo de firma, de hasta 256 KB. Se mira la firma de los bytes, no
 * lo que diga quien la sube.
 */
public record PersonSignature(byte[] imagen, LocalDateTime actualizada) {

    public static final int TAMANO_MAXIMO = 256 * 1024;

    private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    public PersonSignature {
        if (imagen == null || imagen.length == 0) {
            throw new IllegalArgumentException("La firma no puede estar vacía");
        }
        if (imagen.length > TAMANO_MAXIMO) {
            throw new IllegalArgumentException(
                    "La firma pesa " + imagen.length + " bytes y el máximo es " + TAMANO_MAXIMO);
        }
        if (imagen.length < FIRMA_PNG.length
                || !Arrays.equals(Arrays.copyOf(imagen, FIRMA_PNG.length), FIRMA_PNG)) {
            throw new IllegalArgumentException("La firma tiene que ser una imagen PNG");
        }
        Objects.requireNonNull(actualizada, "La firma lleva cuándo se guardó");
        imagen = imagen.clone();
    }

    @Override
    public byte[] imagen() {
        return imagen.clone();
    }

    public String dataUri() {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(imagen);
    }

    @Override
    public boolean equals(Object otra) {
        return otra instanceof PersonSignature firma && Arrays.equals(imagen, firma.imagen)
                && actualizada.equals(firma.actualizada);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(imagen) + actualizada.hashCode();
    }

    @Override
    public String toString() {
        return "PersonSignature[" + imagen.length + " bytes, " + actualizada + "]";
    }
}
