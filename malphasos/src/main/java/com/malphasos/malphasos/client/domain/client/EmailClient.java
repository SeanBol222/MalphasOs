package com.malphasos.malphasos.client.domain.client;

import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Correo de contacto de un cliente. Entidad interna del agregado {@link Client}: no se accede a
 * ella desde fuera ni tiene sentido por su cuenta.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EmailClient {

    @EqualsAndHashCode.Include
    private final UUID id;

    private final String correo;

    private boolean estadoActivo;

    private EmailClient(UUID id, String correo, boolean estadoActivo) {
        this.id = id;
        this.correo = correo;
        this.estadoActivo = estadoActivo;
    }

    static EmailClient create(String correo) {
        return new EmailClient(UUID.randomUUID(), validar(correo), true);
    }

    public static EmailClient rehydrate(UUID id, String correo, boolean estadoActivo) {
        return new EmailClient(id, correo, estadoActivo);
    }

    void deactivate() {
        this.estadoActivo = false;
    }

    private static String validar(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new IllegalArgumentException("Un correo no puede estar vacio");
        }
        if (!correo.contains("@")) {
            throw new IllegalArgumentException("Un correo necesita una arroba: " + correo);
        }

        return correo.trim();
    }
}
