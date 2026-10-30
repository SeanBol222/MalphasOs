package com.malphasos.malphasos.workorder.infrastructure.output.mapper;

import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrderDocument;
import com.malphasos.malphasos.workorder.infrastructure.output.entities.WorkOrderEntity;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Traduce entre la orden y sus filas.
 *
 * <p>A mano y no con MapStruct: el agregado se construye por {@code rehydrate} y no ofrece setters
 * ni builder a propósito. El mapeo hacia la entidad recibe la fila actual en vez de construir una
 * nueva, desde que la orden tenía filas hijas —la tabla puente, que {@code V50} quitó—.
 */
@Component
public class WorkOrderPersistenceMapper {

    /**
     * Para la orden congelada (V49). Propio y no el de Spring, como el del informe: lo que se guarda no
     * puede cambiar de forma porque alguien configure la serialización del API.
     */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public WorkOrder toDomain(WorkOrderEntity entity) {
        return WorkOrder.rehydrate(
                entity.getId(),
                entity.getIdCliente(),
                entity.getIdSede(),
                entity.getFechaMantenimiento(),
                Periodicity.valueOf(entity.getPeriodicidad()),
                ServiceType.valueOf(entity.getTipoServicio()),
                entity.isGarantia(),
                ExecutionState.valueOf(entity.getEstadoEjecucion()),
                entity.getIdIngeniero(),
                entity.isEstadoActivo(),
                entity.getDocumento() == null ? null : JSON.readValue(entity.getDocumento(), WorkOrderDocument.class),
                entity.isDocumentoTardio());
    }

    public List<WorkOrder> toDomainList(List<WorkOrderEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    /** Vuelca la orden sobre su fila, la existente cuando la hay. */
    public WorkOrderEntity toEntity(WorkOrder orden, WorkOrderEntity existente) {
        WorkOrderEntity entity = existente != null ? existente : new WorkOrderEntity();

        entity.setId(orden.getId());
        entity.setIdCliente(orden.getIdCliente());
        entity.setIdSede(orden.getIdSede());
        entity.setFechaMantenimiento(orden.getFechaMantenimiento());
        entity.setPeriodicidad(orden.getPeriodicidad().name());
        entity.setTipoServicio(orden.getTipoServicio().name());
        entity.setGarantia(orden.isGarantia());
        entity.setEstadoEjecucion(orden.getEstadoEjecucion().name());
        entity.setIdIngeniero(orden.getIdIngeniero());
        entity.setEstadoActivo(orden.isEstadoActivo());
        entity.setDocumento(orden.getDocumento() == null ? null : JSON.writeValueAsString(orden.getDocumento()));
        entity.setDocumentoTardio(orden.isDocumentoTardio());

        return entity;
    }
}
