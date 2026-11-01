package com.malphasos.malphasos.client.application.services.client;

import com.malphasos.malphasos.client.application.ports.input.ClientSupervisorPort;
import com.malphasos.malphasos.client.application.ports.output.ClientPersistencePort;
import com.malphasos.malphasos.client.domain.client.ClientSupervisor;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationResponse;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve quién supervisa a un cliente, por nombre.
 *
 * <p>No comprueba el alcance de quien pregunta, y es a propósito: lo llama la hoja de vida <b>después</b>
 * de haber comprobado que quien la pide tiene derecho al equipo, y a esa altura el cliente ya es suyo.
 */
@Service
@RequiredArgsConstructor
public class ClientSupervisorService implements ClientSupervisorPort {

    private final ClientPersistencePort clientPersistencePort;
    private final PersonCommunicationPort personCommunicationPort;

    @Override
    @Transactional(readOnly = true)
    public List<String> supervisorNamesOf(UUID idCliente) {
        return clientPersistencePort.findById(idCliente)
                .map(cliente -> cliente.getSupervisores().stream()
                        .map(ClientSupervisor::idPersona)
                        .map(personCommunicationPort::findById)
                        .filter(PersonCommunicationResponse::estadoActivo)
                        .map(PersonCommunicationResponse::nombreCompleto)
                        .sorted()
                        .toList())
                .orElse(List.of());
    }
}
