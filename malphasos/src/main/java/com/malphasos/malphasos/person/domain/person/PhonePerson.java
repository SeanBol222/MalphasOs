package com.malphasos.malphasos.person.domain.person;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Teléfono de una {@link Person}.
 *
 * <p>No existe por sí solo: siempre pertenece a una persona, que es quien lo agrega y lo
 * desactiva.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class PhonePerson {

    private UUID idTelefonoPersona;

    private String telefonoPersona;

    /** Borrado lógico: un teléfono inactivo se conserva como historial. */
    private boolean estadoActivo;
}
