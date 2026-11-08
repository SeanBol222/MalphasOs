package com.malphasos.malphasos.equipment.domain.clientEquipment;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * La foto de un equipo del cliente, la que lleva su hoja de vida (V58, 2026-11-08).
 *
 * <p>Decidido por el usuario: una por unidad y <b>solo PNG</b>. La reduce el navegador antes de subirla,
 * para que ocupe lo menos posible; aquí se comprueba lo que se guarda, no cómo llegó. El tipo se mira en
 * los bytes, no en lo que diga quien la sube, igual que la firma y el logo. El tope es el del almacén
 * común, 512 KB: una foto reducida pesa una fracción.
 */
public record EquipmentPhoto(byte[] imagen, LocalDateTime actualizada) {

    public static final int TAMANO_MAXIMO = 512 * 1024;

    private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    public EquipmentPhoto {
        if (imagen == null || imagen.length == 0) {
            throw new IllegalArgumentException("La foto del equipo no puede estar vacía");
        }
        if (imagen.length > TAMANO_MAXIMO) {
            throw new IllegalArgumentException(
                    "La foto del equipo pesa " + imagen.length + " bytes y el máximo es " + TAMANO_MAXIMO);
        }
        if (imagen.length < FIRMA_PNG.length
                || !Arrays.equals(Arrays.copyOf(imagen, FIRMA_PNG.length), FIRMA_PNG)) {
            throw new IllegalArgumentException("La foto del equipo tiene que ser una imagen PNG");
        }
        Objects.requireNonNull(actualizada, "La foto lleva cuándo se subió");
        imagen = imagen.clone();
    }

    @Override
    public byte[] imagen() {
        return imagen.clone();
    }

    /** La foto como {@code data:} URI, que es como la pintan la pantalla y el PDF. */
    public String dataUri() {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(imagen);
    }

    @Override
    public boolean equals(Object otra) {
        return otra instanceof EquipmentPhoto foto && Arrays.equals(imagen, foto.imagen)
                && actualizada.equals(foto.actualizada);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(imagen) + actualizada.hashCode();
    }

    @Override
    public String toString() {
        return "EquipmentPhoto[" + imagen.length + " bytes, " + actualizada + "]";
    }
}
