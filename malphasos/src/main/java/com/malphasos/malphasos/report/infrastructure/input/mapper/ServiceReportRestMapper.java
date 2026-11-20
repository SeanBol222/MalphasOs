package com.malphasos.malphasos.report.infrastructure.input.mapper;

import com.malphasos.malphasos.report.application.model.ReportCatalog;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeCheck;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ReportStep;
import com.malphasos.malphasos.report.domain.serviceReport.UsedReferenceStandard;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.infrastructure.input.model.response.HandoverResponse;
import com.malphasos.malphasos.report.infrastructure.input.model.response.IntakeCheckResponse;
import com.malphasos.malphasos.report.infrastructure.input.model.response.ReportAccessoryResponse;
import com.malphasos.malphasos.report.infrastructure.input.model.response.ReportStepResponse;
import com.malphasos.malphasos.report.infrastructure.input.model.response.ServiceReportResponse;
import com.malphasos.malphasos.report.infrastructure.input.model.response.UsedReferenceStandardResponse;
import com.malphasos.malphasos.report.infrastructure.input.model.response.VerificationReadingResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Traduce el agregado a la respuesta del API, solo en esa dirección.
 *
 * <p>A mano y no con MapStruct: las lecturas que salen son <b>las vigentes</b>, no todas las que el
 * agregado guarda, y esa no es una correspondencia de campos que un generador pueda adivinar. Y desde V51
 * junta dos fuentes: el reporte, con lo que es suyo, y su catálogo —{@link ReportCatalog}—, con lo que dice
 * de sus pasos, verificaciones y patrones sin guardarlo.
 */
@Component
public class ServiceReportRestMapper {

    public ServiceReportResponse toResponse(ServiceReport reporte, ReportCatalog catalogo) {
        List<VerificationReadingResponse> lecturas = reporte.lecturasActivas().stream()
                .map(lectura -> {
                    ReportDocument.Verificacion verificacion = catalogo.verificacion(lectura.idVerificacion())
                            .orElse(null);
                    java.math.BigDecimal tolerancia = verificacion == null ? null : verificacion.tolerancia();

                    return new VerificationReadingResponse(
                            lectura.id(),
                            lectura.idVerificacion(),
                            lectura.idPuntoVerificacion(),
                            lectura.secuencia(),
                            lectura.valorPatron(),
                            lectura.valorEquipo(),
                            verificacion == null ? null : verificacion.unidad(),
                            tolerancia,
                            lectura.error(),
                            lectura.conforme(tolerancia));
                })
                .toList();
        Map<UUID, Integer> posicion = new java.util.HashMap<>();
        for (int i = 0; i < catalogo.pasos().size(); i++) {
            posicion.put(catalogo.pasos().get(i).idProtocolo(), i);
        }

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
                        .map(usado -> patron(usado.idVerificacion(), usado.desdeOrden(),
                                catalogo.patron(usado.idVerificacion(), usado.idEquipoPatron()).orElseThrow(() ->
                                        new IllegalStateException("El catalogo del reporte " + reporte.numero()
                                                + " no trae la ficha del patron " + usado.idEquipoPatron()))))
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
                .idPersonaRecibe(reporte.getIdRecibe())
                .firmadoRecibe(reporte.getFirmaRecibe() == null ? null : reporte.getFirmaRecibe().firmada())
                .resultado(reporte.getResultado())
                .finalizado(reporte.getFinalizado())
                .lecturas(lecturas)
                .pasos(reporte.getPasos().stream()
                        .sorted(Comparator.comparing((ReportStep paso) ->
                                posicion.getOrDefault(paso.idProtocolo(), Integer.MAX_VALUE)))
                        .map(paso -> {
                            ReportDocument.Paso texto = catalogo.paso(paso.idProtocolo()).orElse(null);

                            return new ReportStepResponse(
                                    texto == null ? null : texto.orden(),
                                    paso.idProtocolo(),
                                    texto == null ? null : texto.nombre(),
                                    texto == null ? null : texto.descripcion(),
                                    texto == null ? null : texto.indicacion(),
                                    paso.estado(),
                                    paso.nota());
                        })
                        .toList())
                .estadoActivo(reporte.isEstadoActivo())
                .build();
    }

    private static IntakeCheckResponse ingreso(IntakeCheck verificacion) {
        return verificacion == null ? null : new IntakeCheckResponse(verificacion.estado(), verificacion.observacion());
    }

    private static UsedReferenceStandardResponse patron(
            UUID idVerificacion, boolean desdeOrden, UsedReferenceStandard ficha) {
        return new UsedReferenceStandardResponse(idVerificacion, ficha.id(), ficha.nombre(), ficha.marca(),
                ficha.serie(), ficha.certificado(), ficha.vencimiento(), ficha.modelo(), ficha.codigoInterno(),
                ficha.rango(), ficha.resolucion(), ficha.fechaCalibracion(), ficha.empresaCalibradora(),
                desdeOrden);
    }
}
