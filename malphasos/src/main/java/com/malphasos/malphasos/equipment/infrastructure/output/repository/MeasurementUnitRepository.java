package com.malphasos.malphasos.equipment.infrastructure.output.repository;

import com.malphasos.malphasos.equipment.infrastructure.output.entities.MeasurementUnitEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consulta de unidades de medida. Sin escrituras, igual que las magnitudes. */
public interface MeasurementUnitRepository extends JpaRepository<MeasurementUnitEntity, UUID> {

    /**
     * Las unidades activas de una magnitud, ordenadas por símbolo.
     *
     * <p>Por símbolo y no por nombre: la lista se lee por lo que se imprime, y «°C» antes de «K» es más
     * reconocible que «grado Celsius» antes de «kelvin».
     */
    List<MeasurementUnitEntity> findByMagnitudIdAndEstadoActivoTrueOrderBySimboloAsc(UUID magnitudId);
}
