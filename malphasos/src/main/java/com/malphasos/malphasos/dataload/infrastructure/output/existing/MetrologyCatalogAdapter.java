package com.malphasos.malphasos.dataload.infrastructure.output.existing;

import com.malphasos.malphasos.dataload.application.ports.output.MetrologyCatalogPort;
import com.malphasos.malphasos.equipment.application.ports.output.MetrologyCatalogPersistencePort;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Lee el catálogo metrológico con el puerto de persistencia de {@code equipment}, sin modificarlo. */
@Component
@RequiredArgsConstructor
public class MetrologyCatalogAdapter implements MetrologyCatalogPort {

    private final MetrologyCatalogPersistencePort metrologyCatalogPersistencePort;

    @Override
    @Transactional(readOnly = true)
    public Optional<List<String>> unitsOf(String codigoMagnitud) {
        return metrologyCatalogPersistencePort.findAllMagnitudes().stream()
                .filter(magnitud -> magnitud.codigo().equals(codigoMagnitud))
                .findFirst()
                .map(magnitud -> metrologyCatalogPersistencePort.findUnitsByMagnitude(magnitud.id()).stream()
                        .filter(MeasurementUnit::estadoActivo)
                        .map(MeasurementUnit::simbolo)
                        .toList());
    }
}
