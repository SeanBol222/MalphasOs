package com.malphasos.malphasos.report.application.services.serviceReport;

import com.malphasos.malphasos.company.application.ports.input.ServiceCompanyServicePort;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyDetails;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheet;
import com.malphasos.malphasos.equipment.application.ports.input.LifeSheetServicePort;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import com.malphasos.malphasos.equipment.domain.model.TechnicalSheet;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.person.application.ports.input.PersonSignatureServicePort;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import java.util.Optional;
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

        return new Composed(new ReportDocument(
                ReportDocument.VERSION,
                orden(orden),
                cliente(hoja.identificacion()),
                equipo(hoja.identificacion(), hoja.tecnica()),
                tipo.getVerificaciones().stream().map(ReportDocumentComposer::verificacion).toList(),
                empresa.map(e -> empresa(e.getDatos(), serviceCompanyServicePort.cityName(e), logo)).orElse(null),
                orden.getIdIngeniero() == null
                        ? null
                        : personCommunicationPort.findById(orden.getIdIngeniero()).nombreCompleto(),
                firma == null ? null : firma.huella()),
                logo, firma);
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
                verificacion.unidad().simbolo(), verificacion.cantidadDatos(),
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
