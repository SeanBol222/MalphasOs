package com.malphasos.malphasos.report.infrastructure.input.mapper;

import com.malphasos.malphasos.report.domain.serviceReport.IntakeCheck;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.infrastructure.input.model.response.HandoverResponse;
import com.malphasos.malphasos.report.infrastructure.input.model.response.IntakeCheckResponse;
import com.malphasos.malphasos.report.infrastructure.input.model.response.ReportAccessoryResponse;
import com.malphasos.malphasos.report.infrastructure.input.model.response.ReportStepResponse;
import com.malphasos.malphasos.report.infrastructure.input.model.response.ServiceReportResponse;
import com.malphasos.malphasos.report.infrastructure.input.model.response.UsedReferenceStandardResponse;
import com.malphasos.malphasos.report.infrastructure.input.model.response.VerificationReadingResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Traduce el agregado a la respuesta del API, solo en esa dirección.
 *
 * <p>A mano y no con MapStruct: las lecturas que salen son <b>las vigentes</b>, no todas las que el
 * agregado guarda, y esa no es una correspondencia de campos que un generador pueda adivinar.
 */
@Component
public class ServiceReportRestMapper {

    public ServiceReportResponse toResponse(ServiceReport reporte) {
        List<VerificationReadingResponse> lecturas = reporte.lecturasActivas().stream()
                .map(lectura -> new VerificationReadingResponse(
                        lectura.id(),
                        lectura.idVerificacion(),
                        lectura.idPuntoVerificacion(),
                        lectura.secuencia(),
                        lectura.valorPatron(),
                        lectura.valorEquipo(),
                        lectura.unidad(),
                        lectura.tolerancia(),
                        lectura.error(),
                        lectura.conforme()))
                .toList();

        return ServiceReportResponse.builder()
                .id(reporte.getId())
                .numero(reporte.numero())
                .idOrdenTrabajo(reporte.getIdOrdenTrabajo())
                .idEquipoCliente(reporte.getIdEquipoCliente())
                .estado(reporte.getEstado())
                .fallaReportada(reporte.getFallaReportada())
                .diagnostico(reporte.getDiagnostico())
                .procedimientos(reporte.getProcedimientos())
                .observaciones(reporte.getObservaciones())
                .causa(reporte.getCausa())
                .repuestos(reporte.getRepuestos())
                .patrones(reporte.patronesActivos().stream()
                        .map(usado -> new UsedReferenceStandardResponse(
                                usado.idVerificacion(),
                                usado.patron().id(),
                                usado.patron().nombre(),
                                usado.patron().marca(),
                                usado.patron().serie(),
                                usado.patron().certificado(),
                                usado.patron().vencimiento(),
                                usado.patron().modelo(),
                                usado.patron().codigoInterno(),
                                usado.patron().rango(),
                                usado.patron().resolucion(),
                                usado.patron().fechaCalibracion(),
                                usado.patron().empresaCalibradora()))
                        .toList())
                .ingresoFisico(ingreso(reporte.getIngresoFisico()))
                .ingresoOperativo(ingreso(reporte.getIngresoOperativo()))
                .accesorios(reporte.accesoriosActivos().stream()
                        .map(accesorio -> new ReportAccessoryResponse(accesorio.orden(), accesorio.nombre(),
                                accesorio.conforme(), accesorio.observacion()))
                        .toList())
                .recibe(reporte.getRecibe() == null
                        ? null
                        : new HandoverResponse(reporte.getRecibe().nombre(), reporte.getRecibe().cedula(),
                                reporte.getRecibe().cargo()))
                .firmadoRecibe(reporte.getFirmaRecibe() == null ? null : reporte.getFirmaRecibe().firmada())
                .resultado(reporte.getResultado())
                .finalizado(reporte.getFinalizado())
                .lecturas(lecturas)
                .pasos(reporte.getPasos().stream()
                        .map(paso -> new ReportStepResponse(
                                paso.orden(),
                                paso.idProtocolo(),
                                paso.nombre(),
                                paso.descripcion(),
                                paso.indicacion(),
                                paso.estado(),
                                paso.nota()))
                        .toList())
                .estadoActivo(reporte.isEstadoActivo())
                .build();
    }

    private static IntakeCheckResponse ingreso(IntakeCheck verificacion) {
        return verificacion == null ? null : new IntakeCheckResponse(verificacion.estado(), verificacion.observacion());
    }

    public List<ServiceReportResponse> toList(List<ServiceReport> reportes) {
        return reportes.stream().map(this::toResponse).toList();
    }
}
