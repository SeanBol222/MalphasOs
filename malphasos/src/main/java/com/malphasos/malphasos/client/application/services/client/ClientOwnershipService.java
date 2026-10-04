package com.malphasos.malphasos.client.application.services.client;

import com.malphasos.malphasos.client.application.ports.input.ClientOwnershipPort;
import com.malphasos.malphasos.client.application.ports.output.ClientPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.ServiceAreaPersistencePort;
import java.util.Set;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve a qué clientes pertenece una persona.
 *
 * <p>Va aparte de {@link ClientService} aunque consulte el mismo almacén, por la convención de un
 * servicio por agregado llevada a su conclusión: esto no es un caso de uso sobre el agregado
 * cliente, es una pregunta sobre la pertenencia, la hace otro módulo y su único consumidor es la
 * capa de seguridad. Mezclarla con los once casos de uso del cliente habría acoplado el filtrado
 * por dueño a la clase más concurrida del módulo.
 */
@Service
@RequiredArgsConstructor
public class ClientOwnershipService implements ClientOwnershipPort {

    private final ClientPersistencePort clientPersistencePort;
    private final ServiceAreaPersistencePort serviceAreaPersistencePort;

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> clientsRepresentedBy(UUID idPersona) {
        if (idPersona == null) {
            return Set.of();
        }

        return clientPersistencePort.findIdsRepresentedBy(idPersona);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> serviceAreasOf(Collection<UUID> idsClientes) {
        if (idsClientes == null || idsClientes.isEmpty()) {
            return Set.of();
        }

        return serviceAreaPersistencePort.findIdsByClients(idsClientes);
    }
}
