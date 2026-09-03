package com.malphasos.malphasos.equipment.infrastructure.output;

import com.malphasos.malphasos.equipment.application.ports.output.ModelPersistencePort;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.equipment.infrastructure.output.mapper.EquipmentChainPersistenceMapper;
import com.malphasos.malphasos.equipment.infrastructure.output.repository.ModelRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Implementa el almacen sobre JPA. */
@Component
@RequiredArgsConstructor
public class ModelPersistenceAdapter implements ModelPersistencePort {

    private final ModelRepository modelRepository;
    private final EquipmentChainPersistenceMapper mapper;

    @Override
    public List<Model> findAll() {
        return mapper.toModelList(modelRepository.findAll());
    }

    @Override
    public Optional<Model> findById(UUID id) {
        return modelRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Model> findByEquipment(UUID idEquipo) {
        return mapper.toModelList(modelRepository.findByIdEquipo(idEquipo));
    }

    @Override
    public Model save(Model model) {
        return mapper.toDomain(modelRepository.save(mapper.toEntity(model)));
    }
}
