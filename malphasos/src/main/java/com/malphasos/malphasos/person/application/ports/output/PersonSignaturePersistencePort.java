package com.malphasos.malphasos.person.application.ports.output;

import com.malphasos.malphasos.person.domain.person.PersonSignature;
import java.util.Optional;
import java.util.UUID;

/** El almacén de las firmas: una por persona. */
public interface PersonSignaturePersistencePort {

    Optional<PersonSignature> find(UUID idPersona);

    PersonSignature save(UUID idPersona, PersonSignature firma);
}
