package com.malphasos.malphasos.client.infrastructure.output;

import com.malphasos.malphasos.client.application.ports.output.ManagerPersistencePort;
import com.malphasos.malphasos.client.domain.manager.Manager;
import com.malphasos.malphasos.client.infrastructure.output.mapper.ManagerPersistenceMapper;
import com.malphasos.malphasos.client.infrastructure.output.repository.ManagerRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Implementa el almacén de encargados sobre JPA. */
@Component
@RequiredArgsConstructor
public class ManagerPersistenceAdapter implements ManagerPersistencePort {

    private final ManagerRepository managerRepository;
    private final ManagerPersistenceMapper managerPersistenceMapper;

    @Override
    public List<Manager> findAll() {
        return managerPersistenceMapper.toDomainList(managerRepository.findAll());
    }

    @Override
    public Optional<Manager> findByPerson(UUID idPersona) {
        return managerRepository.findById(idPersona).map(managerPersistenceMapper::toDomain);
    }

    @Override
    public List<Manager> findByHeadquarter(UUID idSede) {
        return managerPersistenceMapper.toDomainList(managerRepository.findByIdSede(idSede));
    }

    @Override
    public List<Manager> findByServiceArea(UUID idArea) {
        return managerPersistenceMapper.toDomainList(
                managerRepository.findByIdAreaServicio(idArea));
    }

    @Override
    public Manager save(Manager manager) {
        return managerPersistenceMapper.toDomain(
                managerRepository.save(managerPersistenceMapper.toEntity(manager)));
    }
}
