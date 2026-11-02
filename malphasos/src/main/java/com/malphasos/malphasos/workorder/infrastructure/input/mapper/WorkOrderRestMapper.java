package com.malphasos.malphasos.workorder.infrastructure.input.mapper;

import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderReportsPort;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.workorder.infrastructure.input.model.response.WorkOrderDocumentResponse;
import com.malphasos.malphasos.workorder.infrastructure.input.model.response.WorkOrderEquipmentResponse;
import com.malphasos.malphasos.workorder.infrastructure.input.model.response.WorkOrderResponse;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Traduce el agregado a la respuesta del API, solo en esa dirección.
 *
 * <p>A mano y no con MapStruct porque la respuesta junta dos fuentes: la orden y, desde {@code V50}, sus
 * reportes activos, que son sus equipos. Quien llama los pide aparte y se los pasa.
 */
@Component
@RequiredArgsConstructor
public class WorkOrderRestMapper {

    /**
     * El nombre del ingeniero (2026-11-02): quien lee la orden —el supervisor del cliente— no tiene
     * {@code person.read}, y sin él la pantalla no podía nombrarlo.
     */
    private final PersonCommunicationPort personCommunicationPort;

    public WorkOrderResponse toResponse(WorkOrder orden, List<WorkOrderReportsPort.ActiveReport> reportes) {
        return toResponse(orden, reportes, nombresDe(List.of(orden)));
    }

    private WorkOrderResponse toResponse(
            WorkOrder orden, List<WorkOrderReportsPort.ActiveReport> reportes, Map<UUID, String> nombres) {
        List<WorkOrderEquipmentResponse> equipos = reportes.stream()
                .map(reporte -> new WorkOrderEquipmentResponse(reporte.idEquipoCliente(), reporte.idReporte()))
                .toList();

        return WorkOrderResponse.builder()
                .id(orden.getId())
                .numero(orden.numero())
                .idCliente(orden.getIdCliente())
                .idSede(orden.getIdSede())
                .fechaMantenimiento(orden.getFechaMantenimiento())
                .periodicidad(orden.getPeriodicidad())
                .tipoServicio(orden.getTipoServicio())
                .garantia(orden.isGarantia())
                .estadoEjecucion(orden.getEstadoEjecucion())
                .idIngeniero(orden.getIdIngeniero())
                .nombreIngeniero(orden.getIdIngeniero() == null ? null : nombres.get(orden.getIdIngeniero()))
                .equipos(equipos)
                .estadoActivo(orden.isEstadoActivo())
                .documento(orden.getDocumento() == null ? null : WorkOrderDocumentResponse.from(orden.getDocumento()))
                .documentoTardio(orden.isDocumentoTardio())
                .build();
    }

    public List<WorkOrderResponse> toList(
            List<WorkOrder> ordenes, Map<UUID, List<WorkOrderReportsPort.ActiveReport>> reportes) {

        Map<UUID, String> nombres = nombresDe(ordenes);

        return ordenes.stream()
                .map(orden -> toResponse(orden, reportes.getOrDefault(orden.getId(), List.of()), nombres))
                .toList();
    }

    /** Los ingenieros de todas las órdenes en una consulta, y no una por orden. */
    private Map<UUID, String> nombresDe(List<WorkOrder> ordenes) {
        return personCommunicationPort.namesOf(ordenes.stream()
                .map(WorkOrder::getIdIngeniero)
                .filter(java.util.Objects::nonNull)
                .toList());
    }
}
