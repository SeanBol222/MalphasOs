package com.malphasos.malphasos.equipment.infrastructure.output;

import com.malphasos.malphasos.equipment.application.ports.output.ReferenceStandardPersistencePort;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.ReferenceStandardEntity;
import com.malphasos.malphasos.equipment.infrastructure.output.repository.ReferenceStandardRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Implementa el almacén de equipos patrón sobre JPA, con el mapeo a mano como en todo agregado. */
@Component
@RequiredArgsConstructor
public class ReferenceStandardPersistenceAdapter implements ReferenceStandardPersistencePort {

    private final ReferenceStandardRepository referenceStandardRepository;

    /** Por nombre, sin distinguir mayúsculas: es como se buscan en una lista. */
    @Override
    public List<ReferenceStandard> findAll() {
        return referenceStandardRepository.findAll().stream()
                .map(ReferenceStandardPersistenceAdapter::toDomain)
                .sorted(Comparator.comparing(ReferenceStandard::getNombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public Optional<ReferenceStandard> findById(UUID id) {
        return referenceStandardRepository.findById(id).map(ReferenceStandardPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<ReferenceStandard> findActiveBySerie(String serie) {
        return referenceStandardRepository.findBySerieAndEstadoActivoTrue(serie)
                .map(ReferenceStandardPersistenceAdapter::toDomain);
    }

    @Override
    public ReferenceStandard save(ReferenceStandard patron) {
        return toDomain(referenceStandardRepository.save(new ReferenceStandardEntity(
                patron.getId(), patron.getNombre(), patron.getMarca(), patron.getSerie(),
                patron.getCertificado(), patron.getVencimientoCertificado(), patron.isEstadoActivo())));
    }

    private static ReferenceStandard toDomain(ReferenceStandardEntity entity) {
        return ReferenceStandard.rehydrate(entity.getId(), entity.getNombre(), entity.getMarca(), entity.getSerie(),
                entity.getCertificado(), entity.getVencimientoCertificado(), entity.isEstadoActivo());
    }
}
