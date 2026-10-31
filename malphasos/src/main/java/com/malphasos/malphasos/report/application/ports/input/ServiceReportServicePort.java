package com.malphasos.malphasos.report.application.ports.input;

import com.malphasos.malphasos.report.application.model.WorkOrderReportProgress;
import com.malphasos.malphasos.report.application.model.ReportCatalog;
import com.malphasos.malphasos.report.application.model.ReportDocumentView;
import com.malphasos.malphasos.report.application.model.ReportArchive;
import com.malphasos.malphasos.report.application.model.ReportPdf;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.DiscardServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FillServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FinishServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.MarkReportStepCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordVerificationCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.ReplaceServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordHandoverCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordIntakeCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RemoveReferenceStandardCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.SignReceiptCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.UseReferenceStandardCommand;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.List;
import java.util.UUID;

/**
 * Lo que se puede hacer con un reporte de servicio desde fuera de este módulo.
 *
 * <p>No hay un {@code findAll}: un reporte solo tiene sentido dentro de su orden o dentro del
 * historial de su equipo, y una lista de todos los reportes del sistema no responde a ninguna
 * pregunta del dominio. Las dos consultas que sí responden son las dos que están aquí.
 */
public interface ServiceReportServicePort {

    /** Los reportes de una orden, que es como RF-09 pide poder llegar a ellos. */
    /**
     * Los reportes de una orden, si la orden entra en el alcance.
     *
     * <p>No comprueba nada por su cuenta: le pasa el alcance al módulo de órdenes, que es el dueño
     * de ese dato.
     */
    List<ServiceReport> findByWorkOrder(UUID idOrdenTrabajo, ReadScope alcance);

    /** El historial de un equipo: sobre él se construirá la hoja de vida (RF-26, RF-27). */
    /** El historial de un equipo, si el equipo entra en el alcance. */
    List<ServiceReport> findByEquipment(UUID idEquipoCliente, ReadScope alcance);

    /**
     * Un reporte, si cuelga de un cliente del alcance.
     *
     * @throws com.malphasos.malphasos.report.domain.exception.ServiceReportNotFoundException si no
     *     existe o si queda fuera del alcance
     */
    ServiceReport findById(UUID id, ReadScope alcance);

    /**
     * Lo que el reporte dice de sus pasos, verificaciones y patrones y no guarda en sus filas (V51): del
     * documento si está cerrado, del catálogo si es borrador. Se pide de un reporte ya leído con su alcance.
     */
    ReportCatalog catalogOf(ServiceReport reporte);

    /**
     * El avance de los reportes de cada orden del alcance que tenga alguno vivo: lo que el listado de
     * órdenes enseña en cada fila. Una orden sin reportes no aparece; para quien lee, son cero.
     */
    List<WorkOrderReportProgress> progressByWorkOrder(ReadScope alcance);

    ServiceReport fill(FillServiceReportCommand command);

    ServiceReport recordVerification(RecordVerificationCommand command);

    /** Registra cómo llegó el equipo: las verificaciones al ingreso y sus accesorios (V31). */
    ServiceReport recordIntake(RecordIntakeCommand command);

    /** Registra quién recibió el equipo al entregarlo, o lo borra (V31). */
    ServiceReport recordHandover(RecordHandoverCommand command);

    /**
     * Quien recibe firma el recibido, antes o después de cerrar (V39). Devuelve los reportes firmados: el
     * pedido primero y, si se aplica a la orden, los de esa orden que no tenían firma.
     */
    List<ServiceReport> signReceipt(SignReceiptCommand command);

    /** Pone un patrón en una verificación del reporte, copiando lo que dice hoy el catálogo. */
    ServiceReport useReferenceStandard(UseReferenceStandardCommand command);

    /** Quita un patrón de una verificación del reporte: lo retira, no lo borra. */
    ServiceReport removeReferenceStandard(RemoveReferenceStandardCommand command);

    /** Marca un paso del protocolo: hecho, no aplica o pendiente otra vez. */
    ServiceReport markStep(MarkReportStepCommand command);

    ServiceReport finish(FinishServiceReportCommand command);

    /**
     * El informe de un reporte (2026-10-17): el congelado al cerrar, o compuesto en vivo si es borrador.
     * Con el mismo alcance que el propio reporte.
     */
    ReportDocumentView document(UUID id, ReadScope alcance);

    /**
     * El informe en PDF (RF-17), compuesto en el servidor desde lo mismo que {@link #document}: el congelado
     * si está cerrado, el de ahora si es un borrador, que sale marcado como tal.
     */
    ReportPdf pdf(UUID id, ReadScope alcance);

    /**
     * Los informes en PDF de todos los reportes activos de una orden, si la orden entra en el alcance y
     * todos están cerrados (2026-10-31): es lo que se entrega al terminar.
     *
     * @throws IllegalStateException si la orden no tiene reportes o alguno sigue en borrador
     */
    ReportArchive pdfsOfWorkOrder(UUID idOrdenTrabajo, ReadScope alcance);

    /**
     * El resumen de una orden en PDF: una fila por equipo atendido, con su informe y su resultado
     * (2026-10-31). Con la misma condición que {@link #pdfsOfWorkOrder}, del que es la primera hoja.
     *
     * @throws IllegalStateException si la orden no tiene reportes o alguno sigue en borrador
     */
    ReportPdf workOrderSummary(UUID idOrdenTrabajo, ReadScope alcance);

    /**
     * El cronograma de una orden en PDF: sus equipos y el próximo mantenimiento de cada uno en los doce meses
     * siguientes (2026-10-31). Con la misma condición que {@link #pdfsOfWorkOrder}, en el que va tras el
     * resumen, y solo para una orden con periodicidad.
     *
     * @throws IllegalStateException si la orden no tiene reportes, alguno sigue en borrador o la orden no
     *     tiene periodicidad
     */
    ReportPdf workOrderSchedule(UUID idOrdenTrabajo, ReadScope alcance);

    /**
     * Congela, con los datos de hoy, los reportes cerrados antes de que el informe se congelara, y los
     * marca como tardíos. Se llama al arrancar; con todos congelados no hace nada.
     *
     * @return cuántos congeló
     */
    int freezeClosedWithoutDocument();

    /**
     * Sustituye un reporte cerrado: lo retira y abre el borrador del mismo equipo (V50). Es la única forma
     * de corregir uno cerrado, también con la orden ejecutada.
     *
     * @return el borrador nuevo
     */
    ServiceReport replace(ReplaceServiceReportCommand command);

    /** Retira un borrador, que es sacar su equipo de la orden (V50). Uno cerrado no se retira: se sustituye. */
    void discard(DiscardServiceReportCommand command);
}
