package com.malphasos.malphasos.workorder.application.services.workOrderStandards;

import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ReferenceStandardServicePort;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderStandardsServicePort;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderReportsPort;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderStandardsPersistencePort;
import com.malphasos.malphasos.workorder.application.services.workOrderStandards.commands.AssignWorkOrderStandardsCommand;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.workorder.domain.workOrderStandards.WorkOrderStandards;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los patrones que una orden lleva a su visita, por tipo de equipo (V60, 2026-11-19).
 *
 * <p><b>Lo que el esquema no expresa vive aquí</b>: que el tipo tenga equipos en la orden, que cada patrón
 * exista, esté activo y el catálogo lo admita en alguna verificación vigente del tipo, y que una orden
 * ejecutada o retirada no los cambie. Un patrón retirado no se añade, pero el que ya llevaba puede
 * quedarse, como en el catálogo.
 *
 * <p><b>Cambiarlos llega a los reportes</b>: los que se añaden se ponen en los borradores de ese tipo,
 * marcados {@code desdeOrden}, en las verificaciones que el catálogo les permite, y los que se quitan salen
 * de ellos si llegaron por la orden. Lo que el ingeniero puso a mano no se toca. Los reportes son de
 * {@code report}, así que se pide por {@link WorkOrderReportsPort}, como abrir un borrador.
 */
@Service
@RequiredArgsConstructor
public class WorkOrderStandardsService implements WorkOrderStandardsServicePort {

    private final WorkOrderStandardsPersistencePort workOrderStandardsPersistencePort;
    private final WorkOrderServicePort workOrderServicePort;
    private final WorkOrderReportsPort workOrderReportsPort;
    private final ClientEquipmentServicePort clientEquipmentServicePort;
    private final ReferenceStandardServicePort referenceStandardServicePort;
    private final WorkOrderStandardsCatalog workOrderStandardsCatalog;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public List<WorkOrderStandards> standardsOf(UUID idOrdenTrabajo, ReadScope alcance) {
        WorkOrder orden = workOrderServicePort.findById(idOrdenTrabajo, alcance);
        Map<UUID, WorkOrderStandards> guardados = workOrderStandardsPersistencePort.findByWorkOrder(orden.getId())
                .stream()
                .collect(Collectors.toMap(WorkOrderStandards::getIdTipoEquipo, Function.identity()));

        return tiposEnLaOrden(orden.getId()).stream()
                .sorted(Comparator.comparing(UUID::toString))
                .map(tipo -> guardados.getOrDefault(tipo, WorkOrderStandards.empty(orden.getId(), tipo)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> standardsForEquipment(UUID idOrdenTrabajo, UUID idEquipoCliente) {
        UUID tipo = clientEquipmentServicePort.equipmentTypesOf(List.of(idEquipoCliente)).get(idEquipoCliente);

        return tipo == null ? Set.of() : workOrderStandardsPersistencePort.find(idOrdenTrabajo, tipo).getPatrones();
    }

    @Override
    @Transactional
    public WorkOrderStandards assign(AssignWorkOrderStandardsCommand command) {
        WorkOrder orden = workOrderServicePort.findById(command.idOrdenTrabajo(), ReadScope.unrestricted());
        orden.requireScopeChangeable("cambiar los patrones");

        if (!tiposEnLaOrden(orden.getId()).contains(command.idTipoEquipo())) {
            throw new IllegalArgumentException("La orden no tiene equipos del tipo " + command.idTipoEquipo()
                    + ": sus patrones se eligen para los tipos que lleva");
        }

        WorkOrderStandards actuales = workOrderStandardsPersistencePort.find(orden.getId(), command.idTipoEquipo());
        exigirAdmitidos(actuales, command);

        Set<UUID> antes = actuales.getPatrones();
        actuales.assign(command.patrones());
        Set<UUID> despues = actuales.getPatrones();

        if (antes.equals(despues)) {
            return actuales;
        }

        WorkOrderStandards guardados = workOrderStandardsPersistencePort.save(actuales);
        actuales.pullEvents().forEach(eventDispatcherPort::dispatch);

        workOrderReportsPort.applyStandards(orden.getId(), command.idTipoEquipo(),
                diferencia(despues, antes), diferencia(antes, despues));

        return guardados;
    }

    /**
     * Cada patrón pedido existe, está activo —o ya lo llevaba la orden— y el catálogo lo admite en alguna
     * verificación vigente del tipo. Los nulos y repetidos los rechaza el agregado.
     */
    private void exigirAdmitidos(WorkOrderStandards actuales, AssignWorkOrderStandardsCommand command) {
        if (command.patrones() == null) {
            return;
        }

        Set<UUID> admitidos = workOrderStandardsCatalog.admitted(command.idTipoEquipo());

        for (UUID idPatron : command.patrones().stream().filter(Objects::nonNull).distinct().toList()) {
            ReferenceStandard patron = referenceStandardServicePort.findById(idPatron);

            if (!patron.isEstadoActivo() && !actuales.lleva(idPatron)) {
                throw new IllegalArgumentException("El patron " + patron.getNombre() + " (" + patron.getSerie()
                        + ") esta retirado: no se anade a una orden");
            }
            if (!admitidos.contains(idPatron)) {
                throw new IllegalArgumentException("El patron " + patron.getNombre() + " (" + patron.getSerie()
                        + ") no es de los que el catalogo admite para ninguna verificacion de este tipo");
            }
        }
    }

    /** Los tipos de los equipos de la orden, que desde V50 son los de sus reportes activos. */
    private Set<UUID> tiposEnLaOrden(UUID idOrdenTrabajo) {
        List<UUID> equipos = workOrderReportsPort.activeReports(idOrdenTrabajo).stream()
                .map(WorkOrderReportsPort.ActiveReport::idEquipoCliente)
                .toList();

        return new HashSet<>(clientEquipmentServicePort.equipmentTypesOf(equipos).values());
    }

    private static Set<UUID> diferencia(Set<UUID> de, Set<UUID> menos) {
        Set<UUID> resto = new HashSet<>(de);
        resto.removeAll(menos);

        return Set.copyOf(resto);
    }
}
