package com.malphasos.malphasos.client.domain.client;

import java.util.UUID;

/**
 * Una persona que supervisa a un cliente (V53): tiene cuenta, ve lo de ese cliente y aprueba sus
 * órdenes, en todas sus sedes.
 *
 * <p>El cargo es de la supervisión y no de la persona: quien supervisa a dos clientes puede hacerlo con
 * cargos distintos.
 *
 * @param idPersona de otro contexto acotado, por eso un identificador y no el objeto
 * @param cargo «Ingeniero biomédico», «Médico propietario»; nulo, sin cargo
 */
public record ClientSupervisor(UUID idPersona, String cargo) {

    /** El cargo más largo que admite el esquema. */
    static final int CARGO_MAXIMO = 80;

    public ClientSupervisor {
        if (idPersona == null) {
            throw new IllegalArgumentException("Un supervisor es una persona");
        }
        if (cargo != null) {
            cargo = cargo.trim();
            if (cargo.isEmpty()) {
                cargo = null;
            } else if (cargo.length() > CARGO_MAXIMO) {
                throw new IllegalArgumentException(
                        "El cargo no puede pasar de " + CARGO_MAXIMO + " caracteres");
            }
        }
    }
}
