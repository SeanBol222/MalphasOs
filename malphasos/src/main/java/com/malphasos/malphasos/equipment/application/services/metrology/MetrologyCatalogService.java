package com.malphasos.malphasos.equipment.application.services.metrology;

import com.malphasos.malphasos.equipment.application.ports.input.MetrologyCatalogServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.MetrologyCatalogPersistencePort;
import com.malphasos.malphasos.equipment.domain.exception.MagnitudeNotFoundException;
import com.malphasos.malphasos.equipment.domain.magnitude.Magnitude;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta del catálogo metrológico. Sin escrituras: el catálogo entra sembrado. */
@Service
@RequiredArgsConstructor
public class MetrologyCatalogService implements MetrologyCatalogServicePort {

    private final MetrologyCatalogPersistencePort metrologyCatalogPersistencePort;

    @Override
    @Transactional(readOnly = true)
    public List<Magnitude> findAllMagnitudes() {
        return metrologyCatalogPersistencePort.findAllMagnitudes();
    }

    /**
     * Las unidades de una magnitud.
     *
     * <p>Comprueba primero que la magnitud existe, y no es ceremonia: sin eso, pedir las unidades de un
     * identificador inventado devolvería una lista vacía, que es indistinguible de una magnitud real a
     * la que nadie le ha puesto unidades. La pantalla pintaría «no hay unidades» en los dos casos.
     */
    @Override
    @Transactional(readOnly = true)
    public List<MeasurementUnit> findUnitsByMagnitude(UUID magnitudId) {
        metrologyCatalogPersistencePort.findMagnitudeById(magnitudId)
                .orElseThrow(() -> new MagnitudeNotFoundException(magnitudId));

        return metrologyCatalogPersistencePort.findUnitsByMagnitude(magnitudId);
    }
}
