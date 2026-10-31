package com.malphasos.malphasos.report.application.sheet;

import com.malphasos.malphasos.report.UnDocumento;
import com.malphasos.malphasos.report.domain.serviceReport.Handover;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrderDocument;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Una orden de tres equipos en dos áreas, con sus reportes cerrados, para el resumen de la orden
 * (2026-10-31). El documento los lista en su orden —área, hoja de vida—; los reportes llegan en otro.
 */
public final class UnaOrdenAtendida {

    public static final UUID ORDEN = UUID.fromString("1dc6f500-0000-4000-8000-000000000000");
    public static final UUID MONITOR = UUID.randomUUID();
    public static final UUID BALANZA = UUID.randomUUID();
    public static final UUID BOMBA = UUID.randomUUID();

    private UnaOrdenAtendida() {
    }

    public static WorkOrderDocument documento(LocalDate ejecutada) {
        return new WorkOrderDocument(WorkOrderDocument.VERSION, "1DC6F5", "PREVENTIVO", false, "ANUAL",
                LocalDate.of(2026, 10, 14), ejecutada,
                new WorkOrderDocument.Cliente("Clínica de Ejemplo S.A.S.", "900000000-0"),
                new WorkOrderDocument.Sede("Sede Norte", "Calle 1", "Bogotá"),
                "Ingeniera de Prueba",
                List.of(
                        new WorkOrderDocument.Equipo(BOMBA, "Hospitalización", "Bomba de infusión", "Marca B",
                                "Modelo B", "SN-2", "HV-CLE-0002", "VIEJO1"),
                        new WorkOrderDocument.Equipo(BALANZA, "Laboratorio", "Balanza", "Kern", "PCB",
                                "SN-3", "HV-CLE-0003", "BBBBBB"),
                        new WorkOrderDocument.Equipo(MONITOR, "Urgencias", "Monitor", "Mindray", null,
                                "SN-1", "HV-CLE-0001", "AAAAAA")));
    }

    /** Un reporte cerrado; con {@code recibe}, firmado por esa persona. */
    public static ServiceReport cerrado(String id, UUID equipo, ServiceResult resultado, String recibe) {
        ServiceReport reporte = ServiceReport.rehydrate(UUID.fromString(id), ORDEN, equipo, ReportState.FINALIZADO,
                null, null, "Limpieza", null, null, null, List.of(), null, null, List.of(), null, resultado,
                LocalDateTime.of(2026, 10, 16, 15, 30), List.of(), List.of(), true, UnDocumento.CUALQUIERA, false);
        if (recibe != null) {
            reporte.signReceipt(new Handover(recibe, null, null), "a".repeat(64), LocalDateTime.of(2026, 10, 17, 9, 0));
        }

        return reporte;
    }

    /** Los tres, en otro orden que el documento; la bomba con el reporte que sustituyó a VIEJO1. */
    public static List<ServiceReport> reportes() {
        return List.of(
                cerrado("aaaaaa00-0000-4000-8000-000000000000", MONITOR, ServiceResult.OPERATIVO, "Ana Pérez"),
                cerrado("bbbbbb00-0000-4000-8000-000000000000", BALANZA, ServiceResult.OPERATIVO, null),
                cerrado("cccccc00-0000-4000-8000-000000000000", BOMBA, ServiceResult.FUERA_DE_SERVICIO, "Luis Gómez"));
    }
}
