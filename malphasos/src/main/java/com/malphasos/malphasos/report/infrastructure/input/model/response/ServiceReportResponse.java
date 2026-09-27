package com.malphasos.malphasos.report.infrastructure.input.model.response;

import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Un reporte de servicio con sus lecturas.
 *
 * <p><b>Solo trae las lecturas vigentes</b>, no las corregidas. El agregado conserva las retiradas
 * porque son historia, pero un reporte se imprime con lo que vale hoy.
 *
 * <p>Como el resto del módulo, devuelve identificadores y no nombres: el cliente, la sede y los
 * responsables se consultan por el identificador de la orden, que es lo que RF-11 llama
 * autocompletar.
 */
@Builder
@Schema(name = "ServiceReportResponse")
public record ServiceReportResponse(
        UUID id,
        UUID idOrdenTrabajo,
        UUID idEquipoCliente,
        ReportState estado,
        String fallaReportada,
        String diagnostico,
        String procedimientos,
        String observaciones,
        ServiceResult resultado,
        @Schema(description = "Cuando se cerro el reporte. Nulo mientras es borrador")
        LocalDateTime finalizado,
        List<VerificationReadingResponse> lecturas,
        boolean estadoActivo) {
}
