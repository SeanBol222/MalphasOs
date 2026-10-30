package com.malphasos.malphasos.workorder.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Un equipo de la orden y el reporte que lo pone en ella (V50).
 *
 * <p>Decía también el área en la que estaba al seleccionarlo, que guardaba la tabla puente. Sin ella, el
 * área es la de hoy —mientras la orden no se ejecuta el equipo no se traslada— y la da el listado de
 * equipos; la de una orden ejecutada está en su documento.
 */
@Schema(name = "WorkOrderEquipmentResponse")
public record WorkOrderEquipmentResponse(
        UUID idEquipoCliente,
        @Schema(description = "Su reporte activo en esta orden: un equipo está en una orden si y solo si tiene uno")
        UUID idReporteServicio) {
}
