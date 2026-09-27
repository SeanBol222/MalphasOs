package com.malphasos.malphasos.report.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Abre el reporte de un equipo de la orden. No lleva más datos: el reporte nace vacío y se llena en
 * campo.
 *
 * <p>La orden no viene en el cuerpo, viene en la ruta: el identificador sale de la ruta y nunca del
 * cuerpo, que es la convención de este API.
 */
@Schema(name = "ServiceReportOpenRequest")
public record ServiceReportOpenRequest(
        @NotNull(message = "El equipo es obligatorio") UUID idEquipoCliente) {
}
