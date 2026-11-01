package com.malphasos.malphasos.client.domain.exception;

import com.malphasos.malphasos.person.domain.person.PersonType;
import java.util.UUID;

/**
 * Esa persona no puede ser supervisora de un cliente (V53, 2026-11-01): solo lo es quien es de tipo
 * {@code SUPERVISOR}. Un ingeniero nombrado supervisor podría aprobar el trabajo que él mismo hizo.
 *
 * <p>Es un conflicto y no datos inválidos: la persona existe y el cliente también, pero esa persona no
 * es de las que supervisan. Hereda el código que tenía la regla gemela de los encargados, que salió con
 * ellos.
 */
public class SupervisorPersonTypeException extends RuntimeException {

    public SupervisorPersonTypeException(UUID idPersona, PersonType tipo) {
        super("La persona " + idPersona + " es " + tipo + ": solo una persona SUPERVISOR supervisa a un cliente");
    }
}
