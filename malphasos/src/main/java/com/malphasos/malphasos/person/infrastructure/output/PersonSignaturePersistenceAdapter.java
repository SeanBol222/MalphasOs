package com.malphasos.malphasos.person.infrastructure.output;

import com.malphasos.malphasos.person.application.ports.output.PersonSignaturePersistencePort;
import com.malphasos.malphasos.person.domain.person.PersonSignature;
import com.malphasos.malphasos.person.infrastructure.output.entities.PersonSignatureEntity;
import com.malphasos.malphasos.person.infrastructure.output.repository.PersonSignatureRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Implementa el almacén de firmas sobre JPA. */
@Component
@RequiredArgsConstructor
public class PersonSignaturePersistenceAdapter implements PersonSignaturePersistencePort {

    private final PersonSignatureRepository personSignatureRepository;

    @Override
    public Optional<PersonSignature> find(UUID idPersona) {
        return personSignatureRepository.findById(idPersona)
                .map(fila -> new PersonSignature(fila.getImagen(), fila.getActualizada()));
    }

    @Override
    public PersonSignature save(UUID idPersona, PersonSignature firma) {
        PersonSignatureEntity fila = personSignatureRepository.save(
                new PersonSignatureEntity(idPersona, firma.imagen(), firma.actualizada()));

        return new PersonSignature(fila.getImagen(), fila.getActualizada());
    }
}
