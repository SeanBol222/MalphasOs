package com.malphasos.malphasos.workorder.infrastructure.output.mapper;

import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.SelectedEquipment;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.workorder.infrastructure.output.entities.WorkOrderEntity;
import com.malphasos.malphasos.workorder.infrastructure.output.entities.WorkOrderEquipmentEntity;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Traduce entre la orden y sus filas.
 *
 * <p>A mano y no con MapStruct: el agregado se construye por {@code rehydrate} y no ofrece setters
 * ni builder a propósito. Y aquí hay además una regla que ningún generador adivinaría — qué hacer
 * con un equipo que salió del alcance—, por lo que el mapeo hacia la entidad recibe la fila actual
 * en vez de construir una nueva.
 */
@Component
public class WorkOrderPersistenceMapper {

    public WorkOrder toDomain(WorkOrderEntity entity) {
        // Solo los equipos activos: el agregado responde "sobre qué se trabaja en esta orden", y
        // los retirados del alcance quedan en la tabla como historial.
        Set<SelectedEquipment> equipos = entity.getEquipos().stream()
                .filter(WorkOrderEquipmentEntity::isEstadoActivo)
                .map(fila -> SelectedEquipment.of(fila.getEquipoCliente(), fila.getAreaServicio()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return WorkOrder.rehydrate(
                entity.getId(),
                entity.getIdCliente(),
                entity.getIdSede(),
                entity.getFechaMantenimiento(),
                Periodicity.valueOf(entity.getPeriodicidad()),
                ServiceType.valueOf(entity.getTipoServicio()),
                ExecutionState.valueOf(entity.getEstadoEjecucion()),
                entity.getIdIngeniero(),
                equipos,
                entity.isEstadoActivo());
    }

    public List<WorkOrder> toDomainList(List<WorkOrderEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    /**
     * Vuelca la orden sobre su fila.
     *
     * <p>Recibe la entidad existente cuando la hay, para conservar las filas hijas que JPA ya
     * gestiona y limitarse a ponerlas al día. Construir una entidad nueva en cada guardado haría que
     * Hibernate insertara duplicados del alcance.
     */
    public WorkOrderEntity toEntity(WorkOrder orden, WorkOrderEntity existente) {
        WorkOrderEntity entity = existente != null ? existente : new WorkOrderEntity();

        entity.setId(orden.getId());
        entity.setIdCliente(orden.getIdCliente());
        entity.setIdSede(orden.getIdSede());
        entity.setFechaMantenimiento(orden.getFechaMantenimiento());
        entity.setPeriodicidad(orden.getPeriodicidad().name());
        entity.setTipoServicio(orden.getTipoServicio().name());
        entity.setEstadoEjecucion(orden.getEstadoEjecucion().name());
        entity.setIdIngeniero(orden.getIdIngeniero());
        entity.setEstadoActivo(orden.isEstadoActivo());

        sincronizarEquipos(orden, entity);

        return entity;
    }

    /**
     * Concilia el alcance del agregado con las filas del puente.
     *
     * <p>El equipo que ya no está en el agregado es que fue retirado, y su fila queda inactiva en
     * vez de desaparecer: la llave compuesta impide volver a insertarla, y el historial de un equipo
     * —en qué órdenes se le intervino— dejaría de ser cierto si se borrara.
     *
     * <p>El que vuelve reactiva la suya <b>con el área que traiga el agregado</b>, que para un
     * equipo readmitido es la de ese momento y no la de la primera vez. Es exactamente lo que
     * significa retirarlo y volver a añadirlo: la única forma de corregir un área congelada.
     */
    private void sincronizarEquipos(WorkOrder orden, WorkOrderEntity entity) {
        Map<UUID, SelectedEquipment> activos = new LinkedHashMap<>();
        orden.getEquipos()
                .forEach(equipo -> activos.put(equipo.getIdEquipoCliente(), equipo));

        for (WorkOrderEquipmentEntity fila : entity.getEquipos()) {
            SelectedEquipment enElAlcance = activos.get(fila.getEquipoCliente());

            fila.setEstadoActivo(enElAlcance != null);

            if (enElAlcance != null) {
                fila.setAreaServicio(enElAlcance.getIdAreaServicio());
            }
        }

        for (SelectedEquipment seleccionado : activos.values()) {
            boolean yaEsta = entity.getEquipos().stream()
                    .anyMatch(fila -> fila.getEquipoCliente()
                            .equals(seleccionado.getIdEquipoCliente()));

            if (!yaEsta) {
                WorkOrderEquipmentEntity nueva = new WorkOrderEquipmentEntity();
                nueva.setOrden(entity);
                nueva.setEquipoCliente(seleccionado.getIdEquipoCliente());
                nueva.setAreaServicio(seleccionado.getIdAreaServicio());
                nueva.setEstadoActivo(true);

                entity.getEquipos().add(nueva);
            }
        }
    }
}
