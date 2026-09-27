package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import java.util.UUID;

/**
 * Registrar o corregir la información técnica de un reporte: los cinco campos de RF-15.
 *
 * <p>Un campo nulo deja el valor como está y uno en blanco lo borra. La distinción la aplica el
 * agregado, y está documentada en {@code ServiceReport.fill}.
 */
public record FillServiceReportCommand(
        UUID id,
        String fallaReportada,
        String diagnostico,
        String procedimientos,
        String observaciones,
        ServiceResult resultado) {
}
