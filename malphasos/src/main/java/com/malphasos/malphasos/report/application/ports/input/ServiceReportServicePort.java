package com.malphasos.malphasos.report.application.ports.input;

import com.malphasos.malphasos.report.application.services.serviceReport.commands.DiscardServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FillServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FinishServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.OpenServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordVerificationCommand;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
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
    List<ServiceReport> findByWorkOrder(UUID idOrdenTrabajo);

    /** El historial de un equipo: sobre él se construirá la hoja de vida (RF-26, RF-27). */
    List<ServiceReport> findByEquipment(UUID idEquipoCliente);

    ServiceReport findById(UUID id);

    ServiceReport open(OpenServiceReportCommand command);

    ServiceReport fill(FillServiceReportCommand command);

    ServiceReport recordVerification(RecordVerificationCommand command);

    ServiceReport finish(FinishServiceReportCommand command);

    void discard(DiscardServiceReportCommand command);
}
