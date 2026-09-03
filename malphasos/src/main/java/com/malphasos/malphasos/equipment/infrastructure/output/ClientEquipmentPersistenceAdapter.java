package com.malphasos.malphasos.equipment.infrastructure.output;

import com.malphasos.malphasos.equipment.application.ports.output.ClientEquipmentPersistencePort;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.infrastructure.output.mapper.EquipmentChainPersistenceMapper;
import com.malphasos.malphasos.equipment.infrastructure.output.repository.ClientEquipmentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Implementa el almacen sobre JPA. */
@Component
@RequiredArgsConstructor
public class ClientEquipmentPersistenceAdapter implements ClientEquipmentPersistencePort {

    private final ClientEquipmentRepository clientEquipmentRepository;
    private final EquipmentChainPersistenceMapper mapper;

    @Override
    public List<ClientEquipment> findAll() {
        return mapper.toClientEquipmentList(clientEquipmentRepository.findAll());
    }

    @Override
    public Optional<ClientEquipment> findById(UUID id) {
        return clientEquipmentRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<ClientEquipment> findByServiceArea(UUID idAreaServicio) {
        return mapper.toClientEquipmentList(clientEquipmentRepository.findByIdAreaServicio(idAreaServicio));
    }

    @Override
    public ClientEquipment save(ClientEquipment clientEquipment) {
        return mapper.toDomain(clientEquipmentRepository.save(mapper.toEntity(clientEquipment)));
    }
}
