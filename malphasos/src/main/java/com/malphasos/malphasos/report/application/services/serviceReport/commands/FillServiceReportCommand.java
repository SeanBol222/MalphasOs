package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import java.util.UUID;

/**
 * Registrar o corregir la información técnica de un reporte: los cinco campos de RF-15, más la causa
 * y los repuestos de un correctivo (V24, 2026-10-14).
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
        String causa,
        String repuestos,
        ServiceResult resultado) {

    /** Los cinco campos de RF-15, sin la causa ni los repuestos de un correctivo: no los cambia. */
    public FillServiceReportCommand(
            UUID id,
            String fallaReportada,
            String diagnostico,
            String procedimientos,
            String observaciones,
            ServiceResult resultado) {
        this(id, fallaReportada, diagnostico, procedimientos, observaciones, null, null, resultado);
    }
}
