package com.malphasos.malphasos.person.domain.person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La firma de una persona de la casa (V38, 2026-10-17). */
class PersonSignatureTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 7};
    private static final LocalDateTime HOY = LocalDateTime.of(2026, 10, 17, 9, 0);

    @Test
    @DisplayName("un PNG se acepta y viaja como data: URI")
    void png() {
        assertThat(new PersonSignature(PNG, HOY).dataUri())
                .isEqualTo("data:image/png;base64," + Base64.getEncoder().encodeToString(PNG));
    }

    @Test
    @DisplayName("lo que no es PNG no es una firma, aunque sea una imagen")
    void soloPng() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0};
        assertThatIllegalArgumentException().isThrownBy(() -> new PersonSignature(jpeg, HOY))
                .withMessageContaining("PNG");
        assertThatIllegalArgumentException().isThrownBy(() -> new PersonSignature(new byte[] {1, 2}, HOY));
    }

    @Test
    @DisplayName("vacía no es una firma")
    void vacia() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PersonSignature(new byte[0], HOY));
        assertThatIllegalArgumentException().isThrownBy(() -> new PersonSignature(null, HOY));
    }

    @Test
    @DisplayName("el tope es un cuarto de megabyte: lo justo entra y un byte más no")
    void tamano() {
        assertThat(new PersonSignature(Arrays.copyOf(PNG, PersonSignature.TAMANO_MAXIMO), HOY).imagen())
                .hasSize(PersonSignature.TAMANO_MAXIMO);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PersonSignature(Arrays.copyOf(PNG, PersonSignature.TAMANO_MAXIMO + 1), HOY))
                .withMessageContaining("pesa");
    }

    @Test
    @DisplayName("no comparte sus bytes")
    void copia() {
        byte[] original = PNG.clone();
        PersonSignature firma = new PersonSignature(original, HOY);
        original[8] = 0;
        firma.imagen()[8] = 0;

        assertThat(firma.imagen()).isEqualTo(PNG);
    }
}
