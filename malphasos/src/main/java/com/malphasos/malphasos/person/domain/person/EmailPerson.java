package com.malphasos.malphasos.person.domain.person;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Correo electrónico de una {@link Person}.
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
public class EmailPerson {

    private UUID idCorreoPersona;

    private String correoPersona;

    /** Borrado lógico: un correo inactivo se conserva como historial. */
    private boolean estadoActivo;
}
