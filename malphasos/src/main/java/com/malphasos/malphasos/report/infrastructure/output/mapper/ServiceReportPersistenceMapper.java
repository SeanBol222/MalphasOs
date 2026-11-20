package com.malphasos.malphasos.report.infrastructure.output.mapper;

import com.malphasos.malphasos.report.domain.serviceReport.Handover;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeCheck;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeState;
import com.malphasos.malphasos.report.domain.serviceReport.ReceiptSignature;
import com.malphasos.malphasos.report.domain.serviceReport.ReportAccessory;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ReportStep;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.StepState;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationReading;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationStandard;
import com.malphasos.malphasos.report.infrastructure.output.entities.ReportContentJson;
import com.malphasos.malphasos.report.infrastructure.output.entities.ServiceReportEntity;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Traduce entre el reporte y su fila.
 *
 * <p>A mano y no con MapStruct: el agregado se construye por {@code rehydrate} y no ofrece setters ni
 * builder a propósito.
 *
 * <p><b>Desde V59 lo que el reporte dice va en un solo documento</b>, {@code t_contenido}, con la forma de
 * {@link ReportContentJson}. Hasta entonces eran cuatro tablas hijas que este mapper conciliaba fila por
 * fila, con un orden de escritura obligatorio para no chocar contra un índice único parcial; con el documento
 * no hay filas que conciliar ni orden que imponer: se escribe entero.
 *
 * <p><b>Solo se guarda lo vigente.</b> El agregado sigue reteniendo en memoria lo que se retira —la lectura
 * corregida, el accesorio quitado— porque sus reglas lo usan mientras dura la operación, pero lo retirado no
 * llega al documento. Decidido por el usuario con V59: lo retirado lo era dentro de un borrador y el informe
 * nunca lo imprimió.
 *
 * <p><b>Los identificadores de las piezas no se guardan: se derivan.</b> El documento identifica cada pieza
 * por lo que ya la hace única —un paso por su protocolo, un patrón por su verificación y él mismo, una lectura
 * por su verificación, su punto y su número, un accesorio por su posición— y el identificador que el agregado
 * y el API esperan se calcula de eso y del reporte con {@link UUID#nameUUIDFromBytes}. Así es el mismo en cada
 * lectura del reporte sin ocupar sitio en el documento.
 *
 * <p>Los arreglos se escriben en un orden fijo —los identificadores comparados como texto, que es como los
 * ordena V59— para que guardar dos veces lo mismo dé el mismo documento.
 */
@Component
public class ServiceReportPersistenceMapper {

    /**
     * Para los dos documentos, el informe congelado (V37) y el contenido (V59). Propio y no el de Spring: lo
     * que se guarda no puede cambiar de forma porque alguien configure la serialización del API.
     */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Comparator<UUID> COMO_TEXTO = Comparator.comparing(UUID::toString);

    public ServiceReport toDomain(ServiceReportEntity entity) {
        ReportContentJson contenido = leer(entity);
        UUID id = entity.getId();

        List<VerificationReading> lecturas = new ArrayList<>();
        List<VerificationStandard> patrones = new ArrayList<>();
        for (ReportContentJson.Verificacion verificacion : contenido.verificaciones()) {
            for (ReportContentJson.Patron patron : verificacion.patrones()) {
                patrones.add(new VerificationStandard(
                        derivado(id, "patron", verificacion.id(), patron.id()),
                        verificacion.id(), patron.id(), patron.desdeOrden(), true));
            }
            for (ReportContentJson.Lectura lectura : verificacion.lecturas()) {
                lecturas.add(VerificationReading.rehydrate(
                        derivado(id, "lectura", verificacion.id(), lectura.punto(), lectura.secuencia()),
                        verificacion.id(), lectura.punto(), lectura.secuencia(),
                        lectura.patron(), lectura.equipo(), true));
            }
        }

        List<ReportAccessory> accesorios = new ArrayList<>();
        for (int i = 0; i < contenido.accesorios().size(); i++) {
            ReportContentJson.Accesorio accesorio = contenido.accesorios().get(i);
            accesorios.add(new ReportAccessory(derivado(id, "accesorio", i + 1), i + 1, accesorio.nombre(),
                    accesorio.conforme(), accesorio.observacion(), true));
        }

        List<ReportStep> pasos = contenido.pasos().stream()
                .map(paso -> new ReportStep(derivado(id, "paso", paso.protocolo()), paso.protocolo(),
                        StepState.valueOf(paso.estado()), paso.nota()))
                .toList();

        ReportContentJson.Ingreso ingreso = contenido.ingreso();
        ReportContentJson.Recibe recibe = contenido.recibe();

        return ServiceReport.rehydrate(
                id,
                entity.getIdOrdenTrabajo(),
                entity.getIdEquipoCliente(),
                ReportState.valueOf(entity.getEstado()),
                contenido.falla(),
                contenido.diagnostico(),
                contenido.procedimientos(),
                contenido.observaciones(),
                contenido.causa(),
                contenido.repuestos(),
                patrones,
                ingreso == null ? null : aIngreso(ingreso.fisico()),
                ingreso == null ? null : aIngreso(ingreso.operativo()),
                accesorios,
                recibe == null ? null : new Handover(recibe.nombre(), recibe.cedula(), recibe.cargo()),
                entity.getResultado() == null ? null : ServiceResult.valueOf(entity.getResultado()),
                entity.getFinalizado(),
                lecturas,
                pasos,
                entity.isEstadoActivo(),
                entity.getDocumento() == null ? null : JSON.readValue(entity.getDocumento(), ReportDocument.class),
                entity.isDocumentoTardio(),
                entity.getHuellaFirmaRecibe() == null
                        ? null
                        : new ReceiptSignature(entity.getHuellaFirmaRecibe(), entity.getFirmaRecibe()),
                entity.getIdRecibe());
    }

    public List<ServiceReport> toDomainList(List<ServiceReportEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    /**
     * Vuelca el reporte sobre su fila. Recibe la existente cuando la hay, para que Hibernate la ponga al día
     * en vez de intentar insertarla otra vez.
     */
    public ServiceReportEntity toEntity(ServiceReport reporte, ServiceReportEntity existente) {
        ServiceReportEntity entity = existente != null ? existente : new ServiceReportEntity();

        entity.setId(reporte.getId());
        entity.setIdOrdenTrabajo(reporte.getIdOrdenTrabajo());
        entity.setIdEquipoCliente(reporte.getIdEquipoCliente());
        entity.setEstado(reporte.getEstado().name());
        entity.setResultado(reporte.getResultado() == null ? null : reporte.getResultado().name());
        entity.setFinalizado(reporte.getFinalizado());
        entity.setEstadoActivo(reporte.isEstadoActivo());
        entity.setDocumento(reporte.getDocumento() == null ? null : JSON.writeValueAsString(reporte.getDocumento()));
        entity.setDocumentoTardio(reporte.isDocumentoTardio());
        ReceiptSignature firma = reporte.getFirmaRecibe();
        entity.setHuellaFirmaRecibe(firma == null ? null : firma.huella());
        entity.setFirmaRecibe(firma == null ? null : firma.firmada());
        entity.setIdRecibe(reporte.getIdRecibe());
        entity.setContenido(JSON.writeValueAsString(contenido(reporte)));

        return entity;
    }

    /** El documento de un reporte, con lo vigente y en el orden fijo. */
    static ReportContentJson contenido(ServiceReport reporte) {
        Handover recibe = reporte.getRecibe();

        return new ReportContentJson(
                ReportContentJson.VERSION,
                reporte.getFallaReportada(),
                reporte.getDiagnostico(),
                reporte.getCausa(),
                reporte.getRepuestos(),
                reporte.getProcedimientos(),
                reporte.getObservaciones(),
                new ReportContentJson.Ingreso(
                        aEstado(reporte.getIngresoFisico()), aEstado(reporte.getIngresoOperativo())),
                recibe == null ? null : new ReportContentJson.Recibe(recibe.nombre(), recibe.cedula(), recibe.cargo()),
                reporte.getPasos().stream()
                        .sorted(Comparator.comparing(ReportStep::idProtocolo, COMO_TEXTO))
                        .map(paso -> new ReportContentJson.Paso(paso.idProtocolo(), paso.estado().name(), paso.nota()))
                        .toList(),
                reporte.accesoriosActivos().stream()
                        .map(a -> new ReportContentJson.Accesorio(a.nombre(), a.conforme(), a.observacion()))
                        .toList(),
                verificaciones(reporte));
    }

    /** Las verificaciones con algo registrado: un patrón o una lectura vigentes. */
    private static List<ReportContentJson.Verificacion> verificaciones(ServiceReport reporte) {
        Map<UUID, List<ReportContentJson.Patron>> patrones = new TreeMap<>(COMO_TEXTO);
        Map<UUID, List<ReportContentJson.Lectura>> lecturas = new TreeMap<>(COMO_TEXTO);

        reporte.getPatrones().stream()
                .filter(VerificationStandard::estadoActivo)
                .sorted(Comparator.comparing(VerificationStandard::idEquipoPatron, COMO_TEXTO))
                .forEach(usado -> patrones.computeIfAbsent(usado.idVerificacion(), v -> new ArrayList<>())
                        .add(new ReportContentJson.Patron(usado.idEquipoPatron(), usado.desdeOrden())));

        reporte.getLecturas().stream()
                .filter(VerificationReading::estadoActivo)
                .sorted(Comparator.comparing(VerificationReading::idPuntoVerificacion,
                                Comparator.nullsFirst(COMO_TEXTO))
                        .thenComparingInt(VerificationReading::secuencia))
                .forEach(lectura -> lecturas.computeIfAbsent(lectura.idVerificacion(), v -> new ArrayList<>())
                        .add(new ReportContentJson.Lectura(lectura.idPuntoVerificacion(), lectura.secuencia(),
                                lectura.valorPatron(), lectura.valorEquipo())));

        TreeMap<UUID, Boolean> ids = new TreeMap<>(COMO_TEXTO);
        patrones.keySet().forEach(v -> ids.put(v, true));
        lecturas.keySet().forEach(v -> ids.put(v, true));

        return ids.keySet().stream()
                .map(v -> new ReportContentJson.Verificacion(
                        v, patrones.getOrDefault(v, List.of()), lecturas.getOrDefault(v, List.of())))
                .toList();
    }

    private static ReportContentJson leer(ServiceReportEntity entity) {
        ReportContentJson contenido = JSON.readValue(entity.getContenido(), ReportContentJson.class);

        // Una versión que este código no conoce no se interpreta a ciegas: se dice.
        if (contenido.version() != ReportContentJson.VERSION) {
            throw new IllegalStateException("El contenido del reporte " + entity.getId() + " es de la version "
                    + contenido.version() + " y este codigo lee la " + ReportContentJson.VERSION);
        }

        return contenido;
    }

    private static IntakeCheck aIngreso(ReportContentJson.Estado estado) {
        return estado == null ? null : new IntakeCheck(IntakeState.valueOf(estado.estado()), estado.observacion());
    }

    private static ReportContentJson.Estado aEstado(IntakeCheck ingreso) {
        return ingreso == null ? null : new ReportContentJson.Estado(ingreso.estado().name(), ingreso.observacion());
    }

    /** El identificador de una pieza del reporte, derivado de lo que la hace única dentro de él. */
    static UUID derivado(UUID idReporte, String pieza, Object... partes) {
        StringBuilder clave = new StringBuilder(idReporte.toString()).append('/').append(pieza);
        for (Object parte : partes) {
            clave.append('/').append(parte);
        }

        return UUID.nameUUIDFromBytes(clave.toString().getBytes(StandardCharsets.UTF_8));
    }
}
