package com.malphasos.malphasos.client.application.services.serviceArea;

import com.malphasos.malphasos.client.application.model.ServiceAreaLocation;
import com.malphasos.malphasos.client.application.ports.input.ServiceAreaLocationPort;
import com.malphasos.malphasos.client.application.ports.output.ServiceAreaPersistencePort;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dice dónde está un lote de áreas. Va aparte de {@link ServiceAreaService} por lo mismo que
 * {@code ClientOwnershipService}: no es un caso de uso del área, es una pregunta que hace otro módulo.
 */
@Service
@RequiredArgsConstructor
public class ServiceAreaLocationService implements ServiceAreaLocationPort {

    private final ServiceAreaPersistencePort serviceAreaPersistencePort;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ServiceAreaLocation> locate(Collection<UUID> idsAreas) {
        // Sin áreas no se pregunta: un «IN ()» vacío es lo que el resto del módulo ya evita.
        if (idsAreas == null || idsAreas.isEmpty()) {
            return Map.of();
        }

        return serviceAreaPersistencePort.locate(idsAreas).stream()
                .collect(Collectors.toUnmodifiableMap(ServiceAreaLocation::idArea, Function.identity()));
    }
}
