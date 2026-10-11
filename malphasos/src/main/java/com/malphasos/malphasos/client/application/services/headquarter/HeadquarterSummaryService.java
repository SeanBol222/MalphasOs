package com.malphasos.malphasos.client.application.services.headquarter;

import com.malphasos.malphasos.client.application.model.HeadquarterSummary;
import com.malphasos.malphasos.client.application.ports.input.HeadquarterSummaryServicePort;
import com.malphasos.malphasos.client.application.ports.output.HeadquarterPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.ServiceAreaPersistencePort;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma el resumen de sedes en dos consultas —las sedes y las áreas de esas sedes—, sea cual sea el
 * número de sedes. Va aparte de {@link HeadquarterService} por la misma razón que
 * {@code ClientOwnershipService}: no es un caso de uso de la sede, es una lectura para pintar listados.
 */
@Service
@RequiredArgsConstructor
public class HeadquarterSummaryService implements HeadquarterSummaryServicePort {

    private final HeadquarterPersistencePort headquarterPersistencePort;
    private final ServiceAreaPersistencePort serviceAreaPersistencePort;

    @Override
    @Transactional(readOnly = true)
    public List<HeadquarterSummary> summarize(UUID idCliente, ReadScope alcance) {
        List<Headquarter> sedes = sedes(idCliente, alcance);

        if (sedes.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<ServiceArea>> areas = serviceAreaPersistencePort
                .findByHeadquarters(sedes.stream().map(Headquarter::getId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.groupingBy(ServiceArea::getIdSede));

        return sedes.stream()
                .sorted(Comparator.comparing(Headquarter::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(sede -> new HeadquarterSummary(
                        sede.getId(),
                        sede.getNombre(),
                        sede.getIdCliente(),
                        sede.getIdCiudad(),
                        sede.isEstadoActivo(),
                        areas.getOrDefault(sede.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(ServiceArea::getNombre, String.CASE_INSENSITIVE_ORDER))
                                .map(area -> new HeadquarterSummary.Area(area.getId(), area.getNombre(), area.isEstadoActivo()))
                                .toList()))
                .toList();
    }

    private List<Headquarter> sedes(UUID idCliente, ReadScope alcance) {
        if (idCliente != null) {
            return alcance.covers(idCliente) ? headquarterPersistencePort.findByClient(idCliente) : List.of();
        }
        if (alcance.coversEverything()) {
            return headquarterPersistencePort.findAll();
        }

        Set<UUID> visibles = alcance.visibleClients();

        return visibles.isEmpty() ? List.of() : headquarterPersistencePort.findByClients(visibles);
    }
}
