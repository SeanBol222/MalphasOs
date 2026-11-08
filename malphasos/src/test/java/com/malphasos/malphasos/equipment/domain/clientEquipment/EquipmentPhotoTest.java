package com.malphasos.malphasos.equipment.domain.clientEquipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La foto de un equipo (V58): solo PNG, de hasta 512 KB, mirado en los bytes. */
class EquipmentPhotoTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2};
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 8, 10, 0);

    @Test
    @DisplayName("un PNG se acepta y se pinta como data: URI")
    void unPng() {
        assertThat(new EquipmentPhoto(PNG, AHORA).dataUri()).isEqualTo("data:image/png;base64,iVBORw0KGgoBAg==");
    }

    @Test
    @DisplayName("un JPEG no: solo PNG, decidido por el usuario")
    void unJpeg() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};

        assertThatThrownBy(() -> new EquipmentPhoto(jpeg, AHORA))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PNG");
    }

    @Test
    @DisplayName("ni vacía ni de más de 512 KB; justo 512 KB sí")
    void tamano() {
        byte[] justa = Arrays.copyOf(PNG, EquipmentPhoto.TAMANO_MAXIMO);
        byte[] deMas = Arrays.copyOf(PNG, EquipmentPhoto.TAMANO_MAXIMO + 1);

        assertThat(new EquipmentPhoto(justa, AHORA).imagen()).hasSize(EquipmentPhoto.TAMANO_MAXIMO);
        assertThatThrownBy(() -> new EquipmentPhoto(deMas, AHORA)).hasMessageContaining("máximo");
        assertThatThrownBy(() -> new EquipmentPhoto(new byte[0], AHORA)).hasMessageContaining("vacía");
        assertThatThrownBy(() -> new EquipmentPhoto(null, AHORA)).hasMessageContaining("vacía");
    }

    @Test
    @DisplayName("los bytes se copian: cambiar el arreglo de fuera no cambia la foto")
    void copia() {
        byte[] bytes = PNG.clone();
        EquipmentPhoto foto = new EquipmentPhoto(bytes, AHORA);
        bytes[9] = 99;
        foto.imagen()[8] = 98;

        assertThat(foto.imagen()).isEqualTo(PNG);
    }
}
