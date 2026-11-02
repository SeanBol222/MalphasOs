package com.malphasos.malphasos.person.application.services.communication;

import com.malphasos.malphasos.person.application.mapper.PersonCommunicationMapper;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationResponse;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.person.application.ports.input.PersonServicePort;
import com.malphasos.malphasos.person.application.ports.output.PersonPersistencePort;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final PersonPersistencePort personPersistencePort;

    @Override
    public PersonCommunicationResponse findById(UUID id) {
        return personCommunicationMapper.toResponse(personServicePort.findById(id));
    }

    @Override
    public boolean exists(UUID id) {
        return id != null && personPersistencePort.findById(id).isPresent();
    }

    @Override
    public Map<UUID, String> namesOf(Collection<UUID> ids) {
        List<UUID> conValor = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (conValor.isEmpty()) {
            return Map.of();
        }

        return personPersistencePort.findAllById(conValor).stream()
                .map(personCommunicationMapper::toResponse)
                .collect(Collectors.toMap(PersonCommunicationResponse::identificador,
                        PersonCommunicationResponse::nombreCompleto));
    }
}
