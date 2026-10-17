package com.malphasos.malphasos.person.application.ports.input;

import com.malphasos.malphasos.person.domain.person.PersonSignature;
import java.util.Optional;
import java.util.UUID;

/**
 * La firma de una persona de la casa (V38, 2026-10-17). La lee el informe al cerrar, y la guarda solo su
 * dueño: el adaptador REST no deja elegir de quién.
 */
public interface PersonSignatureServicePort {

    /** La firma, o vacío si esa persona no ha guardado ninguna. */
    Optional<PersonSignature> find(UUID idPersona);

    /**
     * Guarda la firma, o la sustituye. No se quita: una firma vieja sigue siendo mejor que una línea vacía,
     * y lo que se quiere cambiar se cambia dibujando otra.
     *
     * @throws com.malphasos.malphasos.person.domain.exception.PersonNotFoundException si la persona no existe
     */
    PersonSignature save(UUID idPersona, byte[] imagen);
}
