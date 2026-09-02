package com.malphasos.malphasos.client.infrastructure.output;

import com.malphasos.malphasos.client.application.ports.output.HeadquarterPersistencePort;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.client.infrastructure.output.mapper.HeadquarterPersistenceMapper;
import com.malphasos.malphasos.client.infrastructure.output.repository.HeadquarterRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Implementa el almacén de sedes sobre JPA. */
@Component
@RequiredArgsConstructor
public class HeadquarterPersistenceAdapter implements HeadquarterPersistencePort {

    private final HeadquarterRepository headquarterRepository;
    private final HeadquarterPersistenceMapper headquarterPersistenceMapper;

    @Override
    public List<Headquarter> findAll() {
        return headquarterPersistenceMapper.toDomainList(headquarterRepository.findAll());
    }

    @Override
    public Optional<Headquarter> findById(UUID id) {
        return headquarterRepository.findById(id).map(headquarterPersistenceMapper::toDomain);
    }

    @Override
    public List<Headquarter> findByClient(UUID idCliente) {
        return headquarterPersistenceMapper.toDomainList(
                headquarterRepository.findByIdCliente(idCliente));
    }

    @Override
    public Headquarter save(Headquarter headquarter) {
        return headquarterPersistenceMapper.toDomain(
                headquarterRepository.save(headquarterPersistenceMapper.toEntity(headquarter)));
    }
}
