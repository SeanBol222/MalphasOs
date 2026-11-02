package com.malphasos.malphasos.report.application.services.serviceReport.sheet;

import static com.malphasos.malphasos.report.application.services.serviceReport.sheet.SheetFormat.fechaCorta;
import static com.malphasos.malphasos.report.application.services.serviceReport.sheet.SheetFormat.oRaya;

import com.malphasos.malphasos.report.application.model.OrderApproval;
import com.malphasos.malphasos.report.application.model.WorkOrderScheduleSheet;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrderDocument;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compone el cronograma de una orden (2026-10-31) desde su documento y sus reportes cerrados, con las
 * mismas filas que el resumen —las del documento que tienen reporte activo, en su orden—.
 *
 * <p>El día del servicio es el de ejecución de la orden, y si el documento no lo tiene —una orden congelada
 * tarde, o sin ejecutar todavía—, el programado. El próximo es ese día más la periodicidad, con la misma
 * regla que el informe de cada equipo imprime en «próximo servicio»: así los dos no se contradicen.
 */
public final class WorkOrderScheduleComposer {

    /** Las abreviaturas del plan. No las de Java: en es-CO septiembre es «sept», y la columna es estrecha. */
    private static final String[] MESES =
            {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};
    private static final int MESES_DEL_PLAN = 12;
    private static final String FUERA_DE_SERVICIO = "Fuera de servicio";

    private WorkOrderScheduleComposer() {
    }

    /** Si la orden se programa: solo una con periodicidad tiene un próximo mantenimiento. */
    public static boolean applies(WorkOrderDocument orden) {
        return orden.periodicidad() != null && ReportSheetComposer.MESES.containsKey(orden.periodicidad());
    }

    /**
     * @param reportes los activos de la orden, todos cerrados: lo comprueba el servicio
     * @throws IllegalStateException si la orden no tiene periodicidad: ver {@link #applies}
     */
    public static WorkOrderScheduleSheet compose(
            UUID idOrden, WorkOrderDocument orden, List<ServiceReport> reportes, String empresa, DocumentImage logo) {
        return compose(idOrden, orden, reportes, empresa, logo, null);
    }

    /** @param aprobacion la vigente de la orden (V54), o nula: entonces la línea del supervisor se firma a mano */
    public static WorkOrderScheduleSheet compose(
            UUID idOrden, WorkOrderDocument orden, List<ServiceReport> reportes, String empresa, DocumentImage logo,
            OrderApproval aprobacion) {
        if (!applies(orden)) {
            throw new IllegalStateException("La orden " + orden.numero()
                    + " no tiene periodicidad: no hay próximo mantenimiento que programar");
        }
        LocalDate realizado = orden.fechaEjecucion() != null ? orden.fechaEjecucion() : orden.fechaMantenimiento();
        if (realizado == null) {
            throw new IllegalStateException("La orden " + orden.numero() + " no tiene fecha de servicio");
        }
        LocalDate proximo = ReportSheetComposer.proximoServicio(realizado, orden.periodicidad());
        // Los meses de la periodicidad y no los que hay entre las dos fechas: del 31 de agosto al 28 de
        // febrero van cinco meses completos, y el plan es semestral.
        int cada = ReportSheetComposer.MESES.get(orden.periodicidad());

        Map<UUID, ServiceReport> porEquipo = reportes.stream()
                .collect(Collectors.toMap(ServiceReport::getIdEquipoCliente, Function.identity()));
        List<WorkOrderScheduleSheet.Fila> filas = new ArrayList<>();
        for (WorkOrderDocument.Equipo equipo : orden.equipos()) {
            ServiceReport reporte = porEquipo.get(equipo.id());
            if (reporte == null) {
                continue;
            }
            boolean fuera = reporte.getResultado() == ServiceResult.FUERA_DE_SERVICIO;
            filas.add(new WorkOrderScheduleSheet.Fila(
                    filas.size() + 1,
                    oRaya(equipo.tipo()),
                    oRaya(WorkOrderSummaryComposer.unir(" ", equipo.marca(), equipo.modelo())),
                    oRaya(equipo.area()),
                    oRaya(equipo.serie()),
                    oRaya(equipo.numeroHojaVida()),
                    marcas(cada, fuera),
                    fuera ? FUERA_DE_SERVICIO : fechaCorta(proximo),
                    fuera));
        }

        return new WorkOrderScheduleSheet(
                idOrden,
                orden.numero(),
                empresa,
                logo == null ? null : logo.dataUri(),
                WorkOrderSummaryComposer.servicio(orden),
                WorkOrderSummaryComposer.cliente(orden),
                oRaya(orden.ingeniero()),
                fechaCorta(realizado),
                fechaCorta(proximo),
                WorkOrderSummaryComposer.PERIODICIDAD.get(orden.periodicidad()),
                meses(realizado),
                filas,
                SheetFormat.lineaDeAprobacion(aprobacion));
    }

    /** El mes del servicio y los doce siguientes, con el año en el primero y en cada enero. */
    private static List<WorkOrderScheduleSheet.Mes> meses(LocalDate desde) {
        List<WorkOrderScheduleSheet.Mes> meses = new ArrayList<>();
        for (int i = 0; i <= MESES_DEL_PLAN; i++) {
            LocalDate mes = desde.plusMonths(i);
            boolean conAnio = i == 0 || mes.getMonthValue() == 1;
            meses.add(new WorkOrderScheduleSheet.Mes(
                    MESES[mes.getMonthValue() - 1], conAnio ? String.valueOf(mes.getYear()) : null));
        }

        return meses;
    }

    /** Realizado en el primer mes y programado cada {@code cada} meses; nada más si quedó fuera de servicio. */
    private static List<WorkOrderScheduleSheet.Marca> marcas(int cada, boolean fuera) {
        List<WorkOrderScheduleSheet.Marca> marcas = new ArrayList<>();
        for (int i = 0; i <= MESES_DEL_PLAN; i++) {
            marcas.add(i == 0 ? WorkOrderScheduleSheet.Marca.REALIZADO
                    : !fuera && i % cada == 0 ? WorkOrderScheduleSheet.Marca.PROGRAMADO
                    : WorkOrderScheduleSheet.Marca.NADA);
        }

        return marcas;
    }
}
