package com.malphasos.malphasos.client.domain.client;

import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Teléfono de contacto de un cliente. Entidad interna del agregado {@link Client}. */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PhoneClient {

    @EqualsAndHashCode.Include
    private final UUID id;

    private final String telefono;

    private boolean estadoActivo;

    private PhoneClient(UUID id, String telefono, boolean estadoActivo) {
        this.id = id;
        this.telefono = telefono;
        this.estadoActivo = estadoActivo;
    }

    static PhoneClient create(String telefono) {
        return new PhoneClient(UUID.randomUUID(), validar(telefono), true);
    }

    public static PhoneClient rehydrate(UUID id, String telefono, boolean estadoActivo) {
        return new PhoneClient(id, telefono, estadoActivo);
    }

    void deactivate() {
        this.estadoActivo = false;
    }

    private static String validar(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            throw new IllegalArgumentException("Un telefono no puede estar vacio");
        }

        return telefono.trim();
    }
}
