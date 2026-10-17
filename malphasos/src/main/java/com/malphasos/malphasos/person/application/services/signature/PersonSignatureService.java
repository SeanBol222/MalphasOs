package com.malphasos.malphasos.person.application.services.signature;

import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.person.application.ports.input.PersonSignatureServicePort;
import com.malphasos.malphasos.person.application.ports.output.PersonSignaturePersistencePort;
import com.malphasos.malphasos.person.domain.person.PersonSignature;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PersonSignatureService implements PersonSignatureServicePort {

    private final PersonSignaturePersistencePort personSignaturePersistencePort;
    private final PersonCommunicationPort personCommunicationPort;

    @Override
    @Transactional(readOnly = true)
    public Optional<PersonSignature> find(UUID idPersona) {
        return personSignaturePersistencePort.find(idPersona);
    }

    @Override
    @Transactional
    public PersonSignature save(UUID idPersona, byte[] imagen) {
        // Que exista, con su error propio: si no, la foránea saltaría como un conflicto sin explicar.
        personCommunicationPort.findById(idPersona);

        return personSignaturePersistencePort.save(idPersona, new PersonSignature(imagen, LocalDateTime.now()));
    }
}
