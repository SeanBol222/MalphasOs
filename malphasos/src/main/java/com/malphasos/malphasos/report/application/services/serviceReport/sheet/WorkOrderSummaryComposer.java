package com.malphasos.malphasos.report.application.services.serviceReport.sheet;

import static com.malphasos.malphasos.report.application.services.serviceReport.sheet.SheetFormat.fechaCorta;
import static com.malphasos.malphasos.report.application.services.serviceReport.sheet.SheetFormat.oRaya;

import com.malphasos.malphasos.report.application.model.OrderApproval;
import com.malphasos.malphasos.report.application.model.WorkOrderSummarySheet;
import com.malphasos.malphasos.report.domain.serviceReport.Handover;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrderDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compone el resumen de una orden (2026-10-31) desde lo que la orden dice —su documento: el congelado si
 * se ejecutó— y sus reportes, que se piden todos cerrados.
 *
 * <p>Cada equipo es una fila del documento, en su orden —por área, hoja de vida y serie—, y lo que se
 * hizo sale de su reporte <b>activo</b> y no del número que el documento guardó: un reporte cerrado se
 * puede sustituir con la orden ya ejecutada, y el resumen tiene que nombrar el informe que vale.
 */
public final class WorkOrderSummaryComposer {

    private static final Map<String, String> SERVICIO = Map.of(
            "PREVENTIVO", "Preventivo", "CORRECTIVO", "Correctivo", "CALIBRACION", "Calibración");
    static final Map<String, String> PERIODICIDAD = Map.of(
            "MENSUAL", "mensual", "TRIMESTRAL", "trimestral", "SEMESTRAL", "semestral", "ANUAL", "anual");
    private static final Map<ServiceResult, String> RESULTADO = Map.of(
            ServiceResult.OPERATIVO, "Operativo",
            ServiceResult.OPERATIVO_CON_RESTRICCIONES, "Operativo con restricciones",
            ServiceResult.FUERA_DE_SERVICIO, "Fuera de servicio");

    private WorkOrderSummaryComposer() {
    }

    /**
     * @param reportes los activos de la orden, todos cerrados: lo comprueba el servicio
     * @param empresa el nombre de quien presta el servicio, o nulo si no está configurada
     */
    public static WorkOrderSummarySheet compose(
            UUID idOrden, WorkOrderDocument orden, List<ServiceReport> reportes, String empresa, DocumentImage logo) {
        return compose(idOrden, orden, reportes, empresa, logo, ServiceReport::getRecibe);
    }

    /**
     * @param quienRecibe quien recibió cada reporte, ya resuelto: lo escrito, o la ficha de la persona
     *     registrada que firmó (V55)
     */
    public static WorkOrderSummarySheet compose(
            UUID idOrden, WorkOrderDocument orden, List<ServiceReport> reportes, String empresa, DocumentImage logo,
            Function<ServiceReport, Handover> quienRecibe) {
        return compose(idOrden, orden, reportes, empresa, logo, quienRecibe, null);
    }

    /** @param aprobacion la vigente de la orden (V54), o nula: entonces la línea del supervisor se firma a mano */
    public static WorkOrderSummarySheet compose(
            UUID idOrden, WorkOrderDocument orden, List<ServiceReport> reportes, String empresa, DocumentImage logo,
            Function<ServiceReport, Handover> quienRecibe, OrderApproval aprobacion) {
        Map<UUID, ServiceReport> porEquipo = reportes.stream()
                .collect(Collectors.toMap(ServiceReport::getIdEquipoCliente, Function.identity()));

        List<WorkOrderSummarySheet.Fila> filas = new ArrayList<>();
        for (WorkOrderDocument.Equipo equipo : orden.equipos()) {
            ServiceReport reporte = porEquipo.get(equipo.id());
            if (reporte == null) {
                // Un equipo que el documento congeló y ya no tiene reporte activo: se retiró de la orden.
                continue;
            }
            ServiceResult resultado = reporte.getResultado();
            filas.add(new WorkOrderSummarySheet.Fila(
                    filas.size() + 1,
                    oRaya(equipo.area()),
                    oRaya(equipo.tipo()),
                    oRaya(unir(" ", equipo.marca(), equipo.modelo())),
                    oRaya(equipo.serie()),
                    oRaya(equipo.numeroHojaVida()),
                    reporte.numero(),
                    reporte.getFinalizado() == null ? SheetFormat.SIN_DATO : fechaCorta(reporte.getFinalizado().toLocalDate()),
                    resultado == null ? SheetFormat.SIN_DATO : RESULTADO.get(resultado),
                    nombreDe(quienRecibe.apply(reporte)),
                    resultado != null && resultado != ServiceResult.OPERATIVO));
        }

        return new WorkOrderSummarySheet(
                idOrden,
                orden.numero(),
                empresa,
                logo == null ? null : logo.dataUri(),
                servicio(orden),
                cliente(orden),
                oRaya(orden.ingeniero()),
                totales(reportes),
                filas,
                SheetFormat.lineaDeAprobacion(aprobacion));
    }

    /** «Orden N.º 1DC6F5 · Preventivo anual · Ejecutada el 18 oct 2026», o «En curso» si no se ejecutó. */
    static String servicio(WorkOrderDocument orden) {
        String tipo = SERVICIO.getOrDefault(orden.tipoServicio(), orden.tipoServicio());
        // Map.of no admite buscar nulo: lanza. Un correctivo no tiene periodicidad.
        String cada = orden.periodicidad() == null ? null : PERIODICIDAD.get(orden.periodicidad());

        return unir(" · ",
                "Orden N.º " + orden.numero(),
                (cada == null ? tipo : tipo + " " + cada) + (orden.garantia() ? " en garantía" : ""),
                orden.fechaEjecucion() == null ? "En curso" : "Ejecutada el " + fechaCorta(orden.fechaEjecucion()));
    }

    /** «Clínica S.A.S. · NIT 900… · Sede Norte, Bogotá»: lo comparte el cronograma. */
    static String cliente(WorkOrderDocument orden) {
        return unir(" · ",
                orden.cliente().nombre(),
                orden.cliente().documento() == null ? null : "NIT " + orden.cliente().documento(),
                unir(", ", orden.sede().nombre(), orden.sede().ciudad()));
    }

    private static WorkOrderSummarySheet.Totales totales(List<ServiceReport> reportes) {
        Map<ServiceResult, Long> cuenta = reportes.stream()
                .filter(reporte -> reporte.getResultado() != null)
                .collect(Collectors.groupingBy(ServiceReport::getResultado, Collectors.counting()));

        return new WorkOrderSummarySheet.Totales(
                reportes.size(),
                cuenta.getOrDefault(ServiceResult.OPERATIVO, 0L).intValue(),
                cuenta.getOrDefault(ServiceResult.OPERATIVO_CON_RESTRICCIONES, 0L).intValue(),
                cuenta.getOrDefault(ServiceResult.FUERA_DE_SERVICIO, 0L).intValue());
    }

    static String unir(String separador, String... partes) {
        return Stream.of(partes).filter(parte -> parte != null && !parte.isBlank())
                .collect(Collectors.joining(separador));
    }

    private static String nombreDe(Handover recibe) {
        return recibe == null ? SheetFormat.SIN_DATO : recibe.nombre();
    }
}
