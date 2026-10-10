package com.malphasos.malphasos.equipment.infrastructure.output;

import com.malphasos.malphasos.equipment.application.ports.output.ProtocolPersistencePort;
import com.malphasos.malphasos.equipment.domain.protocol.Protocol;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.ProtocolEntity;
import com.malphasos.malphasos.equipment.infrastructure.output.repository.ProtocolRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementa el almacén de protocolos sobre JPA. El mapeo es a mano, como en todos los agregados: uno
 * de Generación 2 no ofrece setters ni constructor público.
 */
@Component
@RequiredArgsConstructor
public class ProtocolPersistenceAdapter implements ProtocolPersistencePort {

    private final ProtocolRepository protocolRepository;

    /** Por nombre, que es como se buscan en una lista: el catálogo se lee, no se recorre por fecha. */
    @Override
    public List<Protocol> findAll() {
        return protocolRepository.findAll().stream()
                .map(ProtocolPersistenceAdapter::toDomain)
                .sorted(Comparator.comparing(Protocol::getClave))
                .toList();
    }

    @Override
    public Optional<Protocol> findById(UUID id) {
        return protocolRepository.findById(id).map(ProtocolPersistenceAdapter::toDomain);
    }

    @Override
    public List<Protocol> findAllById(Collection<UUID> ids) {
        return ids.isEmpty()
                ? List.of()
                : protocolRepository.findAllById(ids).stream().map(ProtocolPersistenceAdapter::toDomain).toList();
    }

    @Override
    public Optional<Protocol> findActiveByClave(String clave) {
        return protocolRepository.findByClaveAndEstadoActivoTrue(clave).map(ProtocolPersistenceAdapter::toDomain);
    }

    @Override
    public long countActiveAssignments(UUID idProtocolo) {
        return protocolRepository.countActiveAssignments(idProtocolo);
    }

    @Override
    public Protocol save(Protocol protocolo) {
        return toDomain(protocolRepository.save(new ProtocolEntity(
                protocolo.getId(),
                protocolo.getNombre(),
                protocolo.getClave(),
                protocolo.getDescripcion(),
                protocolo.isEstadoActivo())));
    }

    private static Protocol toDomain(ProtocolEntity entity) {
        return Protocol.rehydrate(entity.getId(), entity.getNombre(), entity.getDescripcion(), entity.isEstadoActivo());
    }
}
