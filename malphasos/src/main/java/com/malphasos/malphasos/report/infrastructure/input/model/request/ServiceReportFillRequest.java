package com.malphasos.malphasos.report.infrastructure.input.model.request;

import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Los cinco campos de información técnica que pide RF-15.
 *
 * <p><b>Ningún campo es obligatorio</b>, y no es una omisión: el reporte se llena por partes mientras
 * se trabaja. Un campo ausente —{@code null}— deja el valor como estaba, y uno en blanco lo borra.
 * Lo que hace falta para <i>cerrar</i> el reporte se exige al cerrarlo, no aquí.
 */
@Schema(name = "ServiceReportFillRequest")
public record ServiceReportFillRequest(
        @Schema(description = "Que reporto el cliente. Nulo deja el valor, blanco lo borra")
        @Size(max = 500, message = "La falla reportada no puede pasar de 500 caracteres")
        String fallaReportada,

        @Size(max = 500, message = "El diagnostico no puede pasar de 500 caracteres")
        String diagnostico,

        @Schema(description = "Que se hizo. Obligatorio para poder cerrar el reporte")
        @Size(max = 500, message = "Los procedimientos no pueden pasar de 500 caracteres")
        String procedimientos,

        @Size(max = 500, message = "Las observaciones no pueden pasar de 500 caracteres")
        String observaciones,

        @Schema(description = "Como queda el equipo. Obligatorio para poder cerrar el reporte")
        ServiceResult resultado) {
}
