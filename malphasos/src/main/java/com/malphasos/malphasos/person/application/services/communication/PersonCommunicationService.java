package com.malphasos.malphasos.person.application.services.communication;

import com.malphasos.malphasos.person.application.mapper.PersonCommunicationMapper;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationRequest;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationResponse;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.person.application.ports.input.PersonServicePort;
import com.malphasos.malphasos.person.domain.person.Person;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Implementa el contrato que este módulo publica hacia los demás.
 *
 * <p>No añade reglas propias: traduce entre los DTO de la frontera y el modelo de dominio, y delega
 * en {@link PersonServicePort}, de modo que una persona registrada desde otro módulo pasa por las
 * mismas validaciones y recibe el mismo tratamiento que una registrada por el API REST.
 *
 * <p>En el original esta pieza vivía en {@code infrastructure/adapters/input}. Se mueve a la capa de
 * aplicación porque no adapta ninguna tecnología: la llamada llega de otro módulo dentro del mismo
 * proceso, sin HTTP ni mensajería de por medio.
 */
@Service
@RequiredArgsConstructor
public class PersonCommunicationService implements PersonCommunicationPort {

    private final PersonServicePort personServicePort;
    private final PersonCommunicationMapper personCommunicationMapper;

    @Override
    public PersonCommunicationResponse findById(UUID id) {
        return personCommunicationMapper.toResponse(personServicePort.findById(id));
    }

    @Override
    public UUID save(PersonCommunicationRequest request) {
        Person guardada = personServicePort.save(personCommunicationMapper.toPerson(request));

        return guardada.getIdentificador();
    }
}
