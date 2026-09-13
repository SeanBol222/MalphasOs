package com.malphasos.malphasos.workorder.infrastructure.input.mapper;

import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.workorder.infrastructure.input.model.response.WorkOrderEquipmentResponse;
import com.malphasos.malphasos.workorder.infrastructure.input.model.response.WorkOrderResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Traduce el agregado a la respuesta del API, solo en esa dirección.
 *
 * <p>A mano y no con MapStruct porque el alcance no es una lista de campos: el agregado guarda un
 * conjunto cuya identidad es el equipo, y la respuesta expone una lista con el par completo.
 */
@Component
public class WorkOrderRestMapper {

    public WorkOrderResponse toResponse(WorkOrder orden) {
        List<WorkOrderEquipmentResponse> equipos = orden.getEquipos().stream()
                .map(equipo -> new WorkOrderEquipmentResponse(
                        equipo.getIdEquipoCliente(), equipo.getIdAreaServicio()))
                .toList();

        return WorkOrderResponse.builder()
                .id(orden.getId())
                .idCliente(orden.getIdCliente())
                .idSede(orden.getIdSede())
                .fechaMantenimiento(orden.getFechaMantenimiento())
                .periodicidad(orden.getPeriodicidad())
                .tipoServicio(orden.getTipoServicio())
                .estadoEjecucion(orden.getEstadoEjecucion())
                .idIngeniero(orden.getIdIngeniero())
                .equipos(equipos)
                .estadoActivo(orden.isEstadoActivo())
                .build();
    }

    public List<WorkOrderResponse> toList(List<WorkOrder> ordenes) {
        return ordenes.stream().map(this::toResponse).toList();
    }
}
