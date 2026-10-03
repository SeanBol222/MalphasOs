package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.domain.magnitude.Magnitude;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import java.util.List;
import java.util.UUID;

/**
 * Consulta del catálogo metrológico. <b>Solo lectura</b>: ver
 * {@link com.malphasos.malphasos.equipment.application.ports.output.MetrologyCatalogPersistencePort}.
 */
public interface MetrologyCatalogServicePort {

    List<Magnitude> findAllMagnitudes();

    /** Las unidades de una magnitud. Falla si la magnitud no existe, para no devolver una lista vacía
     * que se confundiría con «esta magnitud no tiene unidades». */
    List<MeasurementUnit> findUnitsByMagnitude(UUID magnitudId);
}
