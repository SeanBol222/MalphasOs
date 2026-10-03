package com.malphasos.malphasos.equipment.infrastructure.output;

import com.malphasos.malphasos.equipment.application.ports.output.MetrologyCatalogPersistencePort;
import com.malphasos.malphasos.equipment.domain.magnitude.Magnitude;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import com.malphasos.malphasos.equipment.infrastructure.output.mapper.EquipmentCatalogPersistenceMapper;
import com.malphasos.malphasos.equipment.infrastructure.output.repository.MagnitudeRepository;
import com.malphasos.malphasos.equipment.infrastructure.output.repository.MeasurementUnitRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa la consulta del catálogo metrológico sobre JPA.
 *
 * <p><b>Sin {@code @Transactional} en las escrituras porque no hay escrituras.</b> Y las lecturas lo
 * llevan en modo {@code readOnly} aunque no mapeen colecciones perezosas: la unidad carga su magnitud en
 * {@code EAGER} porque siempre se necesita —para saber si pertenece a ella— y así la transacción es
 * explícita en vez de depender de que el llamante tenga una abierta.
 */
@Component
@RequiredArgsConstructor
public class MetrologyCatalogPersistenceAdapter implements MetrologyCatalogPersistencePort {

    private final MagnitudeRepository magnitudeRepository;
    private final MeasurementUnitRepository measurementUnitRepository;
    private final EquipmentCatalogPersistenceMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<Magnitude> findAllMagnitudes() {
        return mapper.toMagnitudeList(magnitudeRepository.findByEstadoActivoTrueOrderByNombreAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Magnitude> findMagnitudeById(UUID id) {
        return magnitudeRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeasurementUnit> findUnitsByMagnitude(UUID magnitudId) {
        return mapper.toUnitList(
                measurementUnitRepository
                        .findByMagnitudIdAndEstadoActivoTrueOrderBySimboloAsc(magnitudId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MeasurementUnit> findUnitById(UUID id) {
        return measurementUnitRepository.findById(id).map(mapper::toDomain);
    }
}
