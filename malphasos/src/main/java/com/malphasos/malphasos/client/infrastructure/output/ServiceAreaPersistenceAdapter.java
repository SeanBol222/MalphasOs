package com.malphasos.malphasos.client.infrastructure.output;

import com.malphasos.malphasos.client.application.ports.output.ServiceAreaPersistencePort;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.client.infrastructure.output.mapper.ServiceAreaPersistenceMapper;
import com.malphasos.malphasos.client.infrastructure.output.repository.ServiceAreaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Implementa el almacén de áreas de servicio sobre JPA. */
@Component
@RequiredArgsConstructor
public class ServiceAreaPersistenceAdapter implements ServiceAreaPersistencePort {

    private final ServiceAreaRepository serviceAreaRepository;
    private final ServiceAreaPersistenceMapper serviceAreaPersistenceMapper;

    @Override
    public List<ServiceArea> findAll() {
        return serviceAreaPersistenceMapper.toDomainList(serviceAreaRepository.findAll());
    }

    @Override
    public Optional<ServiceArea> findById(UUID id) {
        return serviceAreaRepository.findById(id).map(serviceAreaPersistenceMapper::toDomain);
    }

    @Override
    public List<ServiceArea> findByHeadquarter(UUID idSede) {
        return serviceAreaPersistenceMapper.toDomainList(serviceAreaRepository.findByIdSede(idSede));
    }

    @Override
    public ServiceArea save(ServiceArea serviceArea) {
        return serviceAreaPersistenceMapper.toDomain(
                serviceAreaRepository.save(serviceAreaPersistenceMapper.toEntity(serviceArea)));
    }
}
