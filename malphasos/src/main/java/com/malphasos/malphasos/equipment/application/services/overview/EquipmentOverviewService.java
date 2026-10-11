package com.malphasos.malphasos.equipment.application.services.overview;

import com.malphasos.malphasos.client.application.model.ServiceAreaLocation;
import com.malphasos.malphasos.client.application.ports.input.ClientOwnershipPort;
import com.malphasos.malphasos.client.application.ports.input.ServiceAreaLocationPort;
import com.malphasos.malphasos.equipment.application.model.overview.EquipmentOverview;
import com.malphasos.malphasos.equipment.application.model.overview.EquipmentOverviewQuery;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentOverviewServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.EquipmentOverviewPersistencePort;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.equipment.application.model.overview.EquipmentAreaSummary;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma el listado de equipos: decide a qué áreas se limita, pide la página y le pone a cada fila
 * dónde está.
 *
 * <p>Un equipo no guarda su cliente, guarda su área. Así que <b>el alcance y el filtro por cliente se
 * traducen a áreas</b> antes de consultar, con {@link ClientOwnershipPort}, igual que
 * {@code ClientEquipmentService.findAll}. Y los nombres de cliente, sede y área se piden <b>después</b>
 * y solo para las filas de la página, con una consulta para todas.
 */
@Service
@RequiredArgsConstructor
public class EquipmentOverviewService implements EquipmentOverviewServicePort {

    private final EquipmentOverviewPersistencePort equipmentOverviewPersistencePort;
    private final ClientOwnershipPort clientOwnershipPort;
    private final ServiceAreaLocationPort serviceAreaLocationPort;

    @Override
    @Transactional(readOnly = true)
    public EquipmentOverview list(EquipmentOverviewQuery consulta, ReadScope alcance) {
        Set<UUID> areas = areasPermitidas(consulta, alcance);

        // Sin áreas no hay equipos, y preguntarlo igual sería un «IN ()». Cae aquí un representante de
        // un cliente sin sedes, y quien filtra por un cliente que no puede ver.
        if (areas != null && areas.isEmpty()) {
            return EquipmentOverview.empty(consulta);
        }

        EquipmentOverview pagina = equipmentOverviewPersistencePort.find(consulta, areas);
        Map<UUID, ServiceAreaLocation> ubicaciones = serviceAreaLocationPort.locate(pagina.filas().stream()
                .map(EquipmentOverview.Row::idAreaServicio)
                .collect(Collectors.toSet()));

        return new EquipmentOverview(
                pagina.filas().stream()
                        .map(fila -> fila.conUbicacion(ubicaciones.get(fila.idAreaServicio())))
                        .toList(),
                pagina.total(),
                pagina.pagina(),
                pagina.tamano());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EquipmentAreaSummary> summarizeByArea(UUID idCliente, ReadScope alcance) {
        Set<UUID> areas = areasPermitidas(
                new EquipmentOverviewQuery(null, idCliente, null, null, false, 0, 1), alcance);

        if (areas != null && areas.isEmpty()) {
            return List.of();
        }

        List<EquipmentAreaSummary> conteos = equipmentOverviewPersistencePort.countByArea(areas);
        Map<UUID, ServiceAreaLocation> ubicaciones = serviceAreaLocationPort.locate(
                conteos.stream().map(EquipmentAreaSummary::idArea).collect(Collectors.toSet()));

        return conteos.stream()
                .filter(conteo -> ubicaciones.containsKey(conteo.idArea()))
                .map(conteo -> {
                    ServiceAreaLocation donde = ubicaciones.get(conteo.idArea());
                    return conteo.enSede(donde.idSede(), donde.idCliente());
                })
                .toList();
    }

    /** Las áreas a las que se limita la consulta, o {@code null} si no se limita. */
    private Set<UUID> areasPermitidas(EquipmentOverviewQuery consulta, ReadScope alcance) {
        Set<UUID> areas = alcance.coversEverything()
                ? null
                : clientOwnershipPort.serviceAreasOf(alcance.visibleClients());

        if (consulta.idCliente() == null) {
            return areas;
        }

        // Filtrar por un cliente fuera del alcance no es un error: la intersección queda vacía y la
        // respuesta es una lista vacía, igual que pedirle a la lista general lo que no se puede ver.
        Set<UUID> delCliente = new HashSet<>(clientOwnershipPort.serviceAreasOf(Set.of(consulta.idCliente())));

        if (areas != null) {
            delCliente.retainAll(areas);
        }

        return delCliente;
    }
}
