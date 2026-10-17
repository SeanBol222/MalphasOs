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
        @Schema(description = "Los seis primeros caracteres del id, en mayusculas: con lo que se cita", example = "1DC6F5")
        String numero,
        UUID idOrdenTrabajo,
        UUID idEquipoCliente,
        ReportState estado,
        String fallaReportada,
        String diagnostico,
        String procedimientos,
        String observaciones,
        @Schema(description = "Por que fallo el equipo. De un correctivo; desde V24") String causa,
        @Schema(description = "Repuestos cambiados, en texto libre; desde V24") String repuestos,
        @Schema(description = "Los patrones vigentes de cada verificacion, como estaban ese dia. Desde V30;"
                + " con V27 era uno por reporte")
        List<UsedReferenceStandardResponse> patrones,
        @Schema(description = "Estructura fisica al recibir el equipo. Nula mientras no se registre; desde V31")
        IntakeCheckResponse ingresoFisico,
        @Schema(description = "Estado operativo al recibir el equipo. Nulo mientras no se registre; desde V31")
        IntakeCheckResponse ingresoOperativo,
        @Schema(description = "Los accesorios con que llego, en su orden; desde V31")
        List<ReportAccessoryResponse> accesorios,
        @Schema(description = "Quien recibio el equipo. Nulo mientras no se diga; desde V31")
        HandoverResponse recibe,
        @Schema(description = "Cuando firmo quien recibe; nulo mientras no firme. La imagen viene en el"
                + " informe; desde V39")
        LocalDateTime firmadoRecibe,
        ServiceResult resultado,
        @Schema(description = "Cuando se cerro el reporte. Nulo mientras es borrador")
        LocalDateTime finalizado,
        List<VerificationReadingResponse> lecturas,
        @Schema(description = "Los pasos del protocolo preventivo, en su orden. Vacia en un correctivo")
        List<ReportStepResponse> pasos,
        boolean estadoActivo) {
}
