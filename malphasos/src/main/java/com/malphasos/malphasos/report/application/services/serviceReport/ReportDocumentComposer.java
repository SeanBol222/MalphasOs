package com.malphasos.malphasos.report.application.services.serviceReport;

import com.malphasos.malphasos.company.application.ports.input.ServiceCompanyServicePort;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyDetails;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import com.malphasos.malphasos.equipment.application.model.AssignedProtocolDetail;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheet;
import com.malphasos.malphasos.equipment.application.ports.input.ProtocolServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ReferenceStandardServicePort;
import com.malphasos.malphasos.equipment.domain.protocol.Protocol;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandardSheet;
import com.malphasos.malphasos.equipment.application.ports.input.LifeSheetServicePort;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import com.malphasos.malphasos.equipment.domain.model.TechnicalSheet;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.person.application.ports.input.PersonSignatureServicePort;
import com.malphasos.malphasos.report.application.model.ReportCatalog;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ReportStep;
import com.malphasos.malphasos.report.domain.serviceReport.UsedReferenceStandard;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationStandard;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Compone lo que el informe imprime y no es del reporte, leyéndolo de donde vive hoy: la orden, la hoja
 * de vida del equipo, su tipo, la empresa y el ingeniero con su firma (2026-10-17).
 *
 * <p>Lo usan los tres caminos del informe: el borrador, que lo compone en vivo cada vez; el cierre, que
 * lo congela; y los reportes cerrados antes de que existiera, que se congelan tarde. Que sea una sola
 * pieza es lo que garantiza que el borrador y el cerrado digan lo mismo el día del cierre.
 *
 * <p><b>Desde V51 compone también la parte que sale del catálogo</b> —{@link #catalog}—: el texto y la
 * posición de cada paso, la unidad y la tolerancia de cada verificación y la ficha de cada patrón. Es lo que
 * un borrador lee en vivo y lo que el cierre congela con el resto.
 *
 * <p>Sin restricción de alcance: quien llega aquí ya pasó por la comprobación del propio reporte.
 */
@Component
@RequiredArgsConstructor
public class ReportDocumentComposer {

    private final WorkOrderServicePort workOrderServicePort;
    private final LifeSheetServicePort lifeSheetServicePort;
    private final ServiceCompanyServicePort serviceCompanyServicePort;
    private final PersonCommunicationPort personCommunicationPort;
    private final PersonSignatureServicePort personSignatureServicePort;
    private final ProtocolServicePort protocolServicePort;
    private final ReferenceStandardServicePort referenceStandardServicePort;

    /** Los pasos van por la prioridad de su protocolo; los que el tipo ya no tiene, al final y por nombre. */
    static final Comparator<ReportDocument.Paso> ORDEN_DE_LOS_PASOS = Comparator
            .comparing(ReportDocument.Paso::orden, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ReportDocument.Paso::nombre);

    /**
     * Los patrones van por verificación y por patrón, comparando los identificadores como texto: es lo que
     * hace el relleno de V51, y una prueba compara los dos documentos.
     */
    static final Comparator<VerificationStandard>
            ORDEN_DE_LOS_PATRONES = Comparator
                    .comparing((VerificationStandard u) ->
                            u.idVerificacion().toString())
                    .thenComparing(u -> u.idEquipoPatron().toString());

    /**
     * El documento, y aparte las dos imágenes que referencia por su huella —el logo de la empresa y la
     * firma del ingeniero—, tal como están ahora. Nulas si no hay.
     */
    public record Composed(ReportDocument documento, DocumentImage logo, DocumentImage firmaIngeniero) {
    }

    /**
     * @param tipo el tipo del equipo del reporte: lo resuelve el servicio, que ya camina el catálogo
     */
    public Composed compose(ServiceReport reporte, EquipmentType tipo) {
        WorkOrder orden = workOrderServicePort.findById(reporte.getIdOrdenTrabajo(), ReadScope.unrestricted());
        LifeSheet hoja = lifeSheetServicePort.findByEquipment(reporte.getIdEquipoCliente(), ReadScope.unrestricted());
        Optional<ServiceCompany> empresa = serviceCompanyServicePort.find();
        DocumentImage logo = empresa.flatMap(ServiceCompany::logo)
                .map(l -> new DocumentImage(l.contenido(), l.tipo()))
                .orElse(null);
        DocumentImage firma = orden.getIdIngeniero() == null
                ? null
                : personSignatureServicePort.find(orden.getIdIngeniero())
                        .map(f -> new DocumentImage(f.imagen(), "image/png"))
                        .orElse(null);

        ReportCatalog catalogo = catalog(reporte, tipo);

        return new Composed(new ReportDocument(
                ReportDocument.VERSION,
                orden(orden),
                cliente(hoja.identificacion()),
                equipo(hoja.identificacion(), hoja.tecnica()),
                catalogo.verificaciones(),
                empresa.map(e -> empresa(e.getDatos(), serviceCompanyServicePort.cityName(e), logo)).orElse(null),
                orden.getIdIngeniero() == null
                        ? null
                        : personCommunicationPort.findById(orden.getIdIngeniero()).nombreCompleto(),
                firma == null ? null : firma.huella(),
                catalogo.pasos(),
                catalogo.patrones()),
                logo, firma);
    }

    /**
     * Lo que el reporte dice de sus pasos, verificaciones y patrones, leído hoy del catálogo (V51). Es lo
     * que un borrador muestra, y lo que el cierre congela.
     *
     * <p>Cada paso lleva el texto de su protocolo y la prioridad y la indicación que tiene en el tipo; uno
     * que el tipo ya no tiene asignado conserva su texto y va al final. Las verificaciones son todas las del
     * tipo, retiradas incluidas, porque una lectura vieja puede ser de una. Y cada patrón activo lleva la
     * ficha que el catálogo tiene de él.
     */
    public ReportCatalog catalog(ServiceReport reporte, EquipmentType tipo) {
        Map<UUID, AssignedProtocolDetail> asignados = reporte.getPasos().isEmpty()
                ? Map.of()
                : protocolServicePort.protocolsOf(tipo.getId()).stream()
                        .collect(Collectors.toMap(a -> a.protocolo().getId(), Function.identity()));

        return new ReportCatalog(
                reporte.getPasos().stream()
                        .map(paso -> paso(paso, asignados.get(paso.idProtocolo())))
                        .sorted(ORDEN_DE_LOS_PASOS)
                        .toList(),
                tipo.getVerificaciones().stream().map(ReportDocumentComposer::verificacion).toList(),
                reporte.patronesActivos().stream()
                        .sorted(ORDEN_DE_LOS_PATRONES)
                        .map(usado -> new ReportDocument.Patron(usado.idVerificacion(),
                                ficha(referenceStandardServicePort.findById(usado.idEquipoPatron()))))
                        .toList());
    }

    private ReportDocument.Paso paso(ReportStep paso, AssignedProtocolDetail asignado) {
        Protocol protocolo = asignado != null ? asignado.protocolo() : protocolServicePort.findById(paso.idProtocolo());

        return new ReportDocument.Paso(paso.idProtocolo(), asignado == null ? null : asignado.prioridad(),
                protocolo.getNombre(), protocolo.getDescripcion(), asignado == null ? null : asignado.indicacion());
    }

    /** La ficha de un patrón como la imprime el informe, tal como el catálogo la tiene hoy. */
    static UsedReferenceStandard ficha(ReferenceStandard patron) {
        ReferenceStandardSheet ficha = patron.getFicha();

        return new UsedReferenceStandard(patron.getId(), patron.getNombre(), patron.getMarca(), patron.getSerie(),
                patron.getCertificado(), patron.getVencimientoCertificado(), ficha.modelo(), ficha.codigoInterno(),
                ficha.rango(), ficha.resolucion(), ficha.fechaCalibracion(), ficha.empresaCalibradora());
    }

    private static ReportDocument.Orden orden(WorkOrder orden) {
        return new ReportDocument.Orden(orden.numero(), orden.getTipoServicio().name(), orden.isGarantia(),
                orden.getPeriodicidad() == null ? null : orden.getPeriodicidad().name(),
                orden.getFechaMantenimiento());
    }

    private static ReportDocument.Cliente cliente(LifeSheet.Identificacion identificacion) {
        return new ReportDocument.Cliente(identificacion.cliente(), identificacion.documentoCliente(),
                identificacion.sede(), identificacion.direccionSede(), identificacion.ciudadSede(),
                identificacion.areaServicio(), identificacion.responsables(), identificacion.telefonosCliente(),
                identificacion.correosCliente());
    }

    private static ReportDocument.Equipo equipo(LifeSheet.Identificacion identificacion, LifeSheet.Tecnica tecnica) {
        TechnicalSheet ficha = tecnica.fichaTecnica() == null ? TechnicalSheet.EMPTY : tecnica.fichaTecnica();

        return new ReportDocument.Equipo(tecnica.tipoEquipo(), tecnica.marca(), tecnica.modelo(),
                identificacion.serie(), identificacion.numeroHojaVida(), identificacion.numeroInventario(),
                identificacion.codigoInterno(), tecnica.tecnologiaPredominante(), tecnica.uso(),
                ficha.riesgo() == null ? null : ficha.riesgo().name(), ficha.alimentacion(), ficha.voltaje(),
                ficha.potencia(), ficha.amperaje(), ficha.frecuencia());
    }

    private static ReportDocument.Verificacion verificacion(TypeVerification verificacion) {
        return new ReportDocument.Verificacion(verificacion.id(), verificacion.magnitud().nombre(),
                verificacion.unidad().simbolo(), verificacion.cantidadDatos(), verificacion.tolerancia(),
                verificacion.puntos().stream()
                        .map(punto -> new ReportDocument.Punto(punto.id(), punto.valor()))
                        .toList());
    }

    private static ReportDocument.Empresa empresa(CompanyDetails datos, String ciudad, DocumentImage logo) {
        return new ReportDocument.Empresa(datos.nombre(), datos.nit(), datos.direccion(), ciudad,
                datos.movil(), datos.garantia(), datos.aclaracion(), datos.codigoInforme(),
                logo == null ? null : logo.huella());
    }
}
