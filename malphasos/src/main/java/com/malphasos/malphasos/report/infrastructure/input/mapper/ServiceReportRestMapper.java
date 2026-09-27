package com.malphasos.malphasos.report.infrastructure.input.mapper;

import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.infrastructure.input.model.response.ServiceReportResponse;
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
                        lectura.idPuntoVerificacion(),
                        lectura.secuencia(),
                        lectura.valorPatron(),
                        lectura.valorEquipo(),
                        lectura.unidad()))
                .toList();

        return ServiceReportResponse.builder()
                .id(reporte.getId())
                .idOrdenTrabajo(reporte.getIdOrdenTrabajo())
                .idEquipoCliente(reporte.getIdEquipoCliente())
                .estado(reporte.getEstado())
                .fallaReportada(reporte.getFallaReportada())
                .diagnostico(reporte.getDiagnostico())
                .procedimientos(reporte.getProcedimientos())
                .observaciones(reporte.getObservaciones())
                .resultado(reporte.getResultado())
                .finalizado(reporte.getFinalizado())
                .lecturas(lecturas)
                .estadoActivo(reporte.isEstadoActivo())
                .build();
    }

    public List<ServiceReportResponse> toList(List<ServiceReport> reportes) {
        return reportes.stream().map(this::toResponse).toList();
    }
}
