package com.malphasos.malphasos.report.application.services.serviceReport;

import com.malphasos.malphasos.company.application.ports.input.ServiceCompanyServicePort;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentTypeServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ModelServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ProtocolServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ReferenceStandardServicePort;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.report.application.model.WorkOrderReportProgress;
import com.malphasos.malphasos.report.application.model.WorkOrderScheduleSheet;
import com.malphasos.malphasos.report.application.model.WorkOrderSummarySheet;
import com.malphasos.malphasos.report.application.ports.input.ServiceReportServicePort;
import com.malphasos.malphasos.report.application.ports.output.ServiceReportPersistencePort;
import com.malphasos.malphasos.report.application.model.ReportCatalog;
import com.malphasos.malphasos.report.application.model.ReportDocumentView;
import com.malphasos.malphasos.report.application.ports.output.WorkOrderSchedulePdfPort;
import com.malphasos.malphasos.report.application.ports.output.WorkOrderSummaryPdfPort;
import com.malphasos.malphasos.report.application.services.serviceReport.sheet.WorkOrderScheduleComposer;
import com.malphasos.malphasos.report.application.services.serviceReport.sheet.WorkOrderSummaryComposer;
import com.malphasos.malphasos.shared.application.ports.output.DocumentImageStorePort;
import com.malphasos.malphasos.report.application.ports.output.ReportPdfPort;
import com.malphasos.malphasos.report.application.model.ReportArchive;
import com.malphasos.malphasos.report.application.model.ReportPdf;
import com.malphasos.malphasos.report.application.services.serviceReport.sheet.ReportSheetComposer;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.DiscardServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FillServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FinishServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.MarkReportStepCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.ReplaceServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordHandoverCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordIntakeCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordVerificationCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RemoveReferenceStandardCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.ReceiptScope;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.SignReceiptCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.UseReferenceStandardCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.VerificationReadingCommand;
import com.malphasos.malphasos.report.domain.exception.ServiceReportNotFoundException;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.Handover;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeCheck;
import com.malphasos.malphasos.report.domain.serviceReport.ReportAccessory;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationReading;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.RemoveEquipmentFromWorkOrderCommand;
import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrderDocument;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta los reportes de servicio.
 *
 * <p>Aquí viven las <b>reglas cruzadas</b> que ni el esquema ni el agregado pueden defender. El
 * agregado solo decide con lo que el propio reporte tiene delante.
 *
 * <p><b>Desde {@code V50} un reporte es también lo que pone a su equipo en la orden</b>: no hay tabla
 * puente, y un equipo está en una orden si y solo si tiene en ella un reporte activo. Por eso aquí no se
 * abre ningún reporte —el borrador nace al añadir el equipo, en {@link ServiceReportDrafts}—, retirar un
 * borrador es sacar el equipo de la orden, y uno cerrado no se retira: se sustituye.
 *
 * <p><b>Este módulo consulta a la orden de trabajo, y es el primero que lo hace.</b> Hasta ahora
 * {@code work-order} era el módulo con más vecinos y nadie lo consultaba a él. Sigue sin haber
 * ciclos: reporte → orden → cliente, equipo y persona.
 *
 * <p><b>Para saber cómo se verifica un equipo hay que caminar cuatro eslabones</b> —unidad → modelo →
 * equipo del catálogo → tipo—, porque así es la cadena del catálogo. Se camina en
 * {@link #tipoDelEquipo(UUID)} y en un solo sitio, para que el coste esté a la vista y no repartido.
 * (Hay un segundo desde {@code V50}: {@link ServiceReportDrafts} recorre los tres primeros para copiar
 * los protocolos al abrir un borrador, y no puede depender de este servicio.)
 */
@Service
@RequiredArgsConstructor
public class ServiceReportService implements ServiceReportServicePort {

    private final ServiceReportPersistencePort serviceReportPersistencePort;
    private final WorkOrderServicePort workOrderServicePort;
    private final ClientEquipmentServicePort clientEquipmentServicePort;
    private final ModelServicePort modelServicePort;
    private final EquipmentServicePort equipmentServicePort;
    private final EquipmentTypeServicePort equipmentTypeServicePort;
    private final ProtocolServicePort protocolServicePort;
    private final ReferenceStandardServicePort referenceStandardServicePort;
    private final EventDispatcherPort eventDispatcherPort;
    private final ReportDocumentComposer reportDocumentComposer;
    private final DocumentImageStorePort documentImageStorePort;
    private final ReportPdfPort reportPdfPort;
    private final WorkOrderSummaryPdfPort workOrderSummaryPdfPort;
    private final WorkOrderSchedulePdfPort workOrderSchedulePdfPort;
    private final ServiceCompanyServicePort serviceCompanyServicePort;
    private final ServiceReportDrafts serviceReportDrafts;

    // ---------------------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<ServiceReport> findByWorkOrder(UUID idOrdenTrabajo, ReadScope alcance) {
        // Toda la comprobación es pasarle el alcance a quien es dueño de la orden.
        workOrderServicePort.findById(idOrdenTrabajo, alcance);

        return serviceReportPersistencePort.findByWorkOrder(idOrdenTrabajo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceReport> findByEquipment(UUID idEquipoCliente, ReadScope alcance) {
        clientEquipmentServicePort.findById(idEquipoCliente, alcance);

        return serviceReportPersistencePort.findByEquipment(idEquipoCliente);
    }

    /**
     * El avance de los reportes por orden, acotado al alcance.
     *
     * <p>Con alcance restringido se pregunta al módulo de órdenes cuáles se ven y se filtra aquí: el
     * dueño del dato de a qué cliente pertenece una orden es ese módulo, no este.
     */
    @Override
    @Transactional(readOnly = true)
    public List<WorkOrderReportProgress> progressByWorkOrder(ReadScope alcance) {
        List<WorkOrderReportProgress> avance = serviceReportPersistencePort.progressByWorkOrder();

        if (alcance.coversEverything()) {
            return avance;
        }

        Set<UUID> visibles = workOrderServicePort.findAll(alcance).stream()
                .map(WorkOrder::getId)
                .collect(Collectors.toSet());

        return avance.stream().filter(fila -> visibles.contains(fila.idOrdenTrabajo())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceReport findById(UUID id, ReadScope alcance) {
        ServiceReport reporte = serviceReportPersistencePort.findById(id)
                .orElseThrow(() -> new ServiceReportNotFoundException(id));

        // Esta es la única de las tres que no puede delegar del todo. Delegar significaría pedir la
        // orden CON el alcance, y entonces el error que sale es «esa orden no existe» cuando lo que
        // se pidió fue un reporte: la respuesta contaría de qué es el identificador que no se puede
        // ver. Se resuelve el dueño y se lanza el error del recurso pedido.
        if (!alcance.coversEverything()) {
            UUID idCliente = workOrderServicePort
                    .findById(reporte.getIdOrdenTrabajo(), ReadScope.unrestricted())
                    .getIdCliente();

            if (!alcance.covers(idCliente)) {
                throw new ServiceReportNotFoundException(id);
            }
        }

        return reporte;
    }

    // ---------------------------------------------------------------------------
    // Sustitución
    // ---------------------------------------------------------------------------

    /**
     * Sustituye un reporte cerrado: lo retira y abre el borrador del mismo equipo en la misma orden, en un
     * solo paso (decidido por el usuario el 2026-10-28, con {@code V50}).
     *
     * <p>En un solo paso porque sin la tabla puente el equipo está en la orden por su reporte: retirar y
     * abrir en dos llamadas lo dejaría fuera de la orden entre una y otra. Es la única forma de corregir un
     * reporte cerrado, como desde {@code V14}: al cerrarse, la línea del sustituto reemplaza a la del
     * sustituido en la hoja de vida.
     *
     * <p><b>Se puede con la orden ya ejecutada</b>, también decidido ese día: en este negocio se corrige
     * después. El documento de la orden no cambia —dice el número del reporte que había al ejecutarla—, y
     * su ficha avisa de que ese equipo se sustituyó después.
     *
     * @return el borrador nuevo
     */
    @Override
    @Transactional
    public ServiceReport replace(ReplaceServiceReportCommand command) {
        ServiceReport cerrado = findById(command.id(), ReadScope.unrestricted());

        if (!cerrado.isEstadoActivo()) {
            throw new IllegalStateException("El reporte " + cerrado.numero() + " ya esta retirado");
        }
        if (cerrado.getEstado() != ReportState.FINALIZADO) {
            throw new IllegalStateException("El reporte " + cerrado.numero() + " esta en borrador: se corrige"
                    + " escribiendo en el, no sustituyendolo");
        }

        WorkOrder orden = workOrderServicePort.findById(cerrado.getIdOrdenTrabajo(), ReadScope.unrestricted());
        if (!orden.isEstadoActivo()) {
            throw new IllegalStateException("No se sustituye un reporte de una orden cancelada: " + orden.getId());
        }

        cerrado.deactivate();
        // El adaptador vuelca la retirada antes de insertar el borrador: si no, el índice único parcial vería
        // por un instante dos reportes activos del mismo equipo en la misma orden.
        persistAndPublish(cerrado);

        return serviceReportDrafts.open(orden.getId(), orden.getTipoServicio(), cerrado.getIdEquipoCliente());
    }

    // ---------------------------------------------------------------------------
    // Contenido
    // ---------------------------------------------------------------------------

    @Override
    @Transactional
    public ServiceReport fill(FillServiceReportCommand command) {
        ServiceReport reporte = paraEscribir(command.id());

        reporte.fill(
                command.fallaReportada(),
                command.diagnostico(),
                command.procedimientos(),
                command.observaciones(),
                command.causa(),
                command.repuestos(),
                command.resultado());

        return persistAndPublish(reporte);
    }

    /**
     * Registra las lecturas, después de comprobarlas contra la configuración del tipo de equipo.
     *
     * <p>Es la regla que justifica el módulo de reportes hablando con el de equipos: el tipo declara
     * <b>cómo</b> se verifica —modalidad, puntos y cuántas lecturas por punto— y aquí se comprueba que
     * lo registrado encaje. Sin esto cabría un reporte con lecturas en un punto que ese tipo nunca
     * declaró, o con veinte lecturas donde el tipo pide tres.
     *
     * <p>La unidad no se acepta del llamante cuando hay punto: se copia del punto. Ver
     * {@link VerificationReadingCommand}.
     */
    @Override
    @Transactional
    public ServiceReport recordVerification(RecordVerificationCommand command) {
        ServiceReport reporte = paraEscribir(command.id());
        EquipmentType tipo = tipoDelEquipo(reporte.getIdEquipoCliente());

        reporte.recordVerification(traducirLecturas(tipo, command.lecturas()));

        return persistAndPublish(reporte);
    }

    /**
     * Registra cómo llegó el equipo (V31). No consulta otros módulos: las dos verificaciones al ingreso
     * son fijas y los accesorios, una lista libre del propio reporte.
     */
    @Override
    @Transactional
    public ServiceReport recordIntake(RecordIntakeCommand command) {
        ServiceReport reporte = paraEscribir(command.id());
        List<RecordIntakeCommand.Accessory> pedidos =
                command.accesorios() == null ? List.of() : command.accesorios();

        reporte.recordIntake(
                new IntakeCheck(command.fisico(), command.observacionFisico()),
                new IntakeCheck(command.operativo(), command.observacionOperativo()),
                IntStream.range(0, pedidos.size())
                        .mapToObj(i -> ReportAccessory.of(i + 1, pedidos.get(i).nombre(),
                                pedidos.get(i).conforme(), pedidos.get(i).observacion()))
                        .toList());

        return persistAndPublish(reporte);
    }

    /** Registra quién recibió el equipo (V31). Los tres campos en blanco lo borran. */
    @Override
    @Transactional
    public ServiceReport recordHandover(RecordHandoverCommand command) {
        ServiceReport reporte = paraEscribir(command.id());
        boolean vacio = Stream.of(command.nombre(), command.cedula(), command.cargo())
                .allMatch(texto -> texto == null || texto.isBlank());

        reporte.recordHandover(vacio ? null : new Handover(command.nombre(), command.cedula(), command.cargo()));

        return persistAndPublish(reporte);
    }

    /**
     * Quien recibe firma el recibido (V39, RF-18). La imagen se guarda una vez, por su huella, y cada
     * reporte firmado apunta a ella.
     *
     * <p><b>Con un alcance mayor, RF-21</b>: la misma firma, con el mismo nombre, en los reportes activos
     * de esa orden que aún no tengan, abiertos o cerrados —todos, o solo los del área del equipo pedido
     * (2026-10-30)—. Los que ya tienen la suya no se tocan —firmado, no se cambia—, y los retirados
     * tampoco. El pedido sí tiene que poder firmarse: si ya está firmado, falla aunque se aplique a más,
     * porque quien firma cree estar firmando ese.
     *
     * <p>El área es la de hoy de cada equipo, y es la misma que tenía al empezar: desde {@code V50} un
     * equipo no se traslada mientras está en una orden sin ejecutar.
     */
    @Override
    @Transactional
    public List<ServiceReport> signReceipt(SignReceiptCommand command) {
        ServiceReport pedido = paraEscribir(command.id());
        Handover recibe = new Handover(command.nombre(), command.cedula(), command.cargo());
        DocumentImage firma = DocumentImage.firma(command.firma());
        LocalDateTime ahora = LocalDateTime.now();

        List<ServiceReport> aFirmar = new ArrayList<>();
        aFirmar.add(pedido);
        if (command.alcance() != ReceiptScope.REPORTE) {
            Set<UUID> equiposDelArea = command.alcance() == ReceiptScope.AREA
                    ? equiposDelAreaDe(pedido.getIdEquipoCliente())
                    : null;
            serviceReportPersistencePort.findByWorkOrder(pedido.getIdOrdenTrabajo()).stream()
                    .filter(otro -> !otro.getId().equals(pedido.getId()))
                    .filter(ServiceReport::isEstadoActivo)
                    .filter(otro -> !otro.firmadoPorQuienRecibe())
                    .filter(otro -> equiposDelArea == null || equiposDelArea.contains(otro.getIdEquipoCliente()))
                    .forEach(aFirmar::add);
        }

        // Primero el pedido, que es el que puede fallar: así no se guarda la imagen de una firma rechazada.
        pedido.signReceipt(recibe, firma.huella(), ahora);
        documentImageStorePort.store(firma);

        List<ServiceReport> firmados = new ArrayList<>();
        firmados.add(persistAndPublish(pedido));
        for (ServiceReport otro : aFirmar.subList(1, aFirmar.size())) {
            otro.signReceipt(recibe, firma.huella(), ahora);
            firmados.add(persistAndPublish(otro));
        }

        return firmados;
    }

    /**
     * Los equipos que están hoy en el área de uno, en una consulta. Sin restricción, como
     * {@link #tipoDelEquipo(UUID)}: quien llega aquí ya pasó por la comprobación del reporte pedido, y la
     * lista solo filtra reportes de esa misma orden.
     */
    private Set<UUID> equiposDelAreaDe(UUID idEquipoCliente) {
        UUID area = clientEquipmentServicePort.findById(idEquipoCliente, ReadScope.unrestricted())
                .getIdAreaServicio();

        return clientEquipmentServicePort.findByServiceArea(area, ReadScope.unrestricted()).stream()
                .map(ClientEquipment::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Pone un patrón en una verificación del reporte, copiando lo que el catálogo dice hoy de él, ficha
     * incluida (V30). La verificación tiene que ser una activa del tipo de este equipo: es lo mismo que
     * se exige a las lecturas, y por el mismo camino.
     *
     * <p>Uno retirado no se puede elegir: dejó de usarse. Uno vencido sí, a propósito —se rechaza al
     * cerrar, contra el día del cierre—, porque elegirlo no certifica nada todavía, y el ingeniero puede
     * tenerlo delante con el certificado renovado en papel y aún sin cargar.
     */
    @Override
    @Transactional
    public ServiceReport useReferenceStandard(UseReferenceStandardCommand command) {
        ServiceReport reporte = paraEscribir(command.id());
        requireActiveVerification(tipoDelEquipo(reporte.getIdEquipoCliente()), command.idVerificacion());
        ReferenceStandard patron = referenceStandardServicePort.findById(command.idEquipoPatron());

        if (!patron.isEstadoActivo()) {
            throw new IllegalArgumentException("El patron " + patron.getNombre() + " (" + patron.getSerie()
                    + ") esta retirado: no se pueden tomar lecturas con el");
        }

        reporte.useReferenceStandard(command.idVerificacion(), patron.getId());

        return persistAndPublish(reporte);
    }

    /**
     * Quita un patrón de una verificación. No consulta el tipo ni el catálogo: quitar lo que no está no
     * hace nada, y lo que está ya pasó por esas comprobaciones al ponerlo.
     */
    @Override
    @Transactional
    public ServiceReport removeReferenceStandard(RemoveReferenceStandardCommand command) {
        ServiceReport reporte = paraEscribir(command.id());
        reporte.removeReferenceStandard(command.idVerificacion(), command.idEquipoPatron());

        return persistAndPublish(reporte);
    }

    @Override
    @Transactional
    public ServiceReport markStep(MarkReportStepCommand command) {
        ServiceReport reporte = paraEscribir(command.id());
        reporte.markStep(command.idProtocolo(), command.estado(), command.nota());

        return persistAndPublish(reporte);
    }

    // ---------------------------------------------------------------------------
    // Cierre
    // ---------------------------------------------------------------------------

    /**
     * Cierra el reporte tras comprobar que la verificación está completa.
     *
     * <p>El agregado ya exige procedimientos y resultado. Lo que se añade aquí es lo que solo se
     * sabe preguntando al tipo de equipo: si ese tipo se verifica, el reporte no se cierra con la
     * tabla a medias, porque un certificado con dos de las tres lecturas no vale para nada.
     */
    @Override
    @Transactional
    public ServiceReport finish(FinishServiceReportCommand command) {
        ServiceReport reporte = paraEscribir(command.id());

        requireCompleteVerification(reporte);
        requireStandardsForReadings(reporte);

        // Un correctivo exigia ademas la falla y el diagnostico desde el 2026-10-14; desde el 2026-10-27
        // no, pedido por el usuario, y el cierre es el mismo para los dos tipos de servicio.
        reporte.finish(congelar(reporte));

        return persistAndPublish(reporte);
    }

    /**
     * Lo que el reporte dice de sus pasos, verificaciones y patrones y no guarda (V51): de su documento si
     * está cerrado, del catálogo de hoy si es borrador. Sin restricción: se pide de un reporte que ya pasó
     * por su lectura acotada.
     */
    @Override
    @Transactional(readOnly = true)
    public ReportCatalog catalogOf(ServiceReport reporte) {
        return reporte.getDocumento() != null
                ? ReportCatalog.of(reporte.getDocumento())
                : reportDocumentComposer.catalog(reporte, tipoDelEquipo(reporte.getIdEquipoCliente()));
    }

    @Override
    @Transactional(readOnly = true)
    public ReportDocumentView document(UUID id, ReadScope alcance) {
        return documentOf(findById(id, alcance));
    }

    private ReportDocumentView documentOf(ServiceReport reporte) {
        if (reporte.getDocumento() != null) {
            ReportDocument documento = reporte.getDocumento();

            return new ReportDocumentView(reporte, documento,
                    imagen(documento.empresa() == null ? null : documento.empresa().huellaDelLogo()),
                    imagen(documento.huellaFirmaIngeniero()), firmaRecibe(reporte), true);
        }

        // Un borrador, o uno cerrado que aún no se congeló tarde: en vivo, sin guardar nada.
        ReportDocumentComposer.Composed compuesto =
                reportDocumentComposer.compose(reporte, tipoDelEquipo(reporte.getIdEquipoCliente()));

        return new ReportDocumentView(
                reporte, compuesto.documento(), compuesto.logo(), compuesto.firmaIngeniero(), firmaRecibe(reporte),
                false);
    }

    /** La firma de quien recibe no se congela al cerrar: se lee del reporte, que la guarda desde que firma. */
    private DocumentImage firmaRecibe(ServiceReport reporte) {
        return reporte.getFirmaRecibe() == null ? null : imagen(reporte.getFirmaRecibe().huella());
    }

    /** El mismo informe que {@link #document}, en PDF: la hoja la compone el servidor y la convierte el puerto. */
    @Override
    @Transactional(readOnly = true)
    public ReportPdf pdf(UUID id, ReadScope alcance) {
        return pdfOf(findById(id, alcance));
    }

    private ReportPdf pdfOf(ServiceReport reporte) {
        ReportDocumentView vista = documentOf(reporte);

        return new ReportPdf("informe-" + vista.reporte().numero() + ".pdf",
                reportPdfPort.render(ReportSheetComposer.compose(vista)));
    }

    /**
     * Los informes de una orden, uno por reporte activo y por orden de número, y delante el resumen de la
     * orden y su cronograma (2026-10-31). Solo cuando están todos cerrados: un borrador sale marcado como
     * tal, y lo que se descarga junto es lo que se entrega. El cronograma, solo si la orden tiene
     * periodicidad: sin ella no hay próximo mantenimiento.
     *
     * <p>Se comprueba antes de convertir ninguno, porque cada conversión es una llamada al conversor.
     */
    @Override
    @Transactional(readOnly = true)
    public ReportArchive pdfsOfWorkOrder(UUID idOrdenTrabajo, ReadScope alcance) {
        WorkOrder orden = workOrderServicePort.findById(idOrdenTrabajo, alcance);
        List<ServiceReport> cerrados = cerradosDe(orden);
        WorkOrderDocument documento = workOrderServicePort.documentOf(orden);

        List<ReportPdf> archivos = new ArrayList<>();
        archivos.add(resumenDe(orden, documento, cerrados));
        if (WorkOrderScheduleComposer.applies(documento)) {
            archivos.add(cronogramaDe(orden, documento, cerrados));
        }
        cerrados.forEach(reporte -> archivos.add(pdfOf(reporte)));

        return new ReportArchive("informes-orden-" + orden.numero() + ".zip", archivos);
    }

    /** Los reportes activos de una orden por número, si los hay y están todos cerrados. */
    private List<ServiceReport> cerradosDe(WorkOrder orden) {
        List<ServiceReport> activos = serviceReportPersistencePort.findByWorkOrder(orden.getId()).stream()
                .filter(ServiceReport::isEstadoActivo)
                .sorted(java.util.Comparator.comparing(ServiceReport::numero))
                .toList();

        if (activos.isEmpty()) {
            throw new IllegalStateException("La orden " + orden.numero() + " no tiene reportes que descargar");
        }
        long abiertos = activos.stream().filter(reporte -> reporte.getEstado() != ReportState.FINALIZADO).count();
        if (abiertos > 0) {
            throw new IllegalStateException("La orden " + orden.numero() + " tiene " + abiertos
                    + " reporte(s) sin cerrar: los informes se descargan juntos cuando estan todos cerrados");
        }

        return activos;
    }

    /** La empresa se lee hoy, como el membrete de un borrador: el resumen no se congela, se compone. */
    private ReportPdf resumenDe(WorkOrder orden, WorkOrderDocument documento, List<ServiceReport> cerrados) {
        Optional<ServiceCompany> empresa = serviceCompanyServicePort.find();
        WorkOrderSummarySheet hoja = WorkOrderSummaryComposer.compose(orden.getId(), documento, cerrados,
                empresa.map(e -> e.getDatos().nombre()).orElse(null), logoDe(empresa));

        return new ReportPdf("orden-" + orden.numero() + ".pdf", workOrderSummaryPdfPort.render(hoja));
    }

    /** Como el resumen: la empresa de hoy. Lanza si la orden no tiene periodicidad. */
    private ReportPdf cronogramaDe(WorkOrder orden, WorkOrderDocument documento, List<ServiceReport> cerrados) {
        Optional<ServiceCompany> empresa = serviceCompanyServicePort.find();
        WorkOrderScheduleSheet hoja = WorkOrderScheduleComposer.compose(orden.getId(), documento, cerrados,
                empresa.map(e -> e.getDatos().nombre()).orElse(null), logoDe(empresa));

        return new ReportPdf("cronograma-orden-" + orden.numero() + ".pdf", workOrderSchedulePdfPort.render(hoja));
    }

    private static DocumentImage logoDe(Optional<ServiceCompany> empresa) {
        return empresa.flatMap(ServiceCompany::logo)
                .map(imagen -> new DocumentImage(imagen.contenido(), imagen.tipo()))
                .orElse(null);
    }

    @Override
    @Transactional
    public int freezeClosedWithoutDocument() {
        List<ServiceReport> pendientes = serviceReportPersistencePort.findFinishedWithoutDocument();
        for (ServiceReport reporte : pendientes) {
            reporte.freezeLate(congelar(reporte));
            serviceReportPersistencePort.save(reporte);
        }

        return pendientes.size();
    }

    /** Compone el documento de ahora y guarda las versiones del logo y de la firma que lleva, si lleva. */
    private ReportDocument congelar(ServiceReport reporte) {
        ReportDocumentComposer.Composed compuesto =
                reportDocumentComposer.compose(reporte, tipoDelEquipo(reporte.getIdEquipoCliente()));
        if (compuesto.logo() != null) {
            documentImageStorePort.store(compuesto.logo());
        }
        if (compuesto.firmaIngeniero() != null) {
            documentImageStorePort.store(compuesto.firmaIngeniero());
        }

        return compuesto.documento();
    }

    /** Una imagen congelada por su huella; nula sin huella. */
    private DocumentImage imagen(String huella) {
        return huella == null ? null : documentImageStorePort.find(huella).orElse(null);
    }

    /**
     * Retira un borrador, que desde {@code V50} es sacar su equipo de la orden: lo hace el servicio de
     * órdenes, con su regla —una orden ejecutada ya no cambia de equipos—. Uno cerrado no se retira, se
     * sustituye: retirarlo dejaría el equipo fuera de una orden en la que sí se le prestó el servicio.
     */
    @Override
    @Transactional
    public void discard(DiscardServiceReportCommand command) {
        ServiceReport reporte = findById(command.id(), ReadScope.unrestricted());

        if (!reporte.isEstadoActivo()) {
            return;
        }
        if (reporte.getEstado() == ReportState.FINALIZADO) {
            throw new IllegalStateException("El reporte " + reporte.numero() + " esta cerrado: no se retira,"
                    + " se sustituye");
        }

        workOrderServicePort.removeEquipment(
                new RemoveEquipmentFromWorkOrderCommand(reporte.getIdOrdenTrabajo(), reporte.getIdEquipoCliente()));
    }

    // ---------------------------------------------------------------------------

    /**
     * Persiste y publica lo que el agregado decidió que ocurrió.
     *
     * <p>Se publican los eventos del agregado recibido y no los del devuelto por el almacén: el
     * segundo se rehidrata y por eso viene sin ninguno.
     */
    private ServiceReport persistAndPublish(ServiceReport reporte) {
        ServiceReport guardado = serviceReportPersistencePort.save(reporte);
        reporte.pullEvents().forEach(eventDispatcherPort::dispatch);

        return guardado;
    }

    /**
     * El reporte, para escribir en él: con su orden empezada (V50).
     *
     * <p>Un reporte cuenta lo que se hizo, y en una orden todavía en {@code CREADA} no se ha hecho nada.
     * Hasta {@code V50} eso se comprobaba al abrirlo; desde entonces el borrador nace al añadir el equipo,
     * aunque la orden no haya empezado, y la regla pasa a cada escritura. Una orden ya {@code EJECUTADA}
     * sí admite escribir —el sustituto de un reporte cerrado se rellena después—; una cancelada, no.
     *
     * <p>Las escrituras recuperan el reporte sin restricción: el alcance acota lecturas, y escribir en un
     * reporte exige {@code report.write}.
     */
    private ServiceReport paraEscribir(UUID id) {
        ServiceReport reporte = findById(id, ReadScope.unrestricted());
        WorkOrder orden = workOrderServicePort.findById(reporte.getIdOrdenTrabajo(), ReadScope.unrestricted());

        if (!orden.isEstadoActivo()) {
            throw new IllegalStateException(
                    "No se escribe en un reporte de una orden cancelada: " + orden.getId());
        }
        if (orden.getEstadoEjecucion() == ExecutionState.CREADA) {
            throw new IllegalStateException("La orden " + orden.numero() + " no ha empezado: un reporte cuenta"
                    + " lo que se hizo, y todavia no se ha hecho nada");
        }

        return reporte;
    }

    /**
     * Traduce las lecturas recibidas y las comprueba contra la configuración del tipo.
     *
     * <p><b>Cinco reglas</b>, y cada una tapa un reporte que de otro modo saldría impreso diciendo algo
     * falso: que el tipo se verifique, que la lectura señale una <b>verificación activa de ese tipo</b>,
     * que señale un punto activo <b>de esa verificación</b> —o ninguno, si la modalidad de esa
     * verificación es variable—, que su número no pase de las lecturas que esa verificación declara, y
     * que la unidad sea la de la verificación y no la que venga escrita.
     *
     * <p><b>Todo se razona por verificación desde el 2026-10-03.</b> Antes se razonaba por tipo, porque
     * un tipo tenía una sola modalidad; ahora un termohigrómetro puede tener la temperatura con patrón
     * constante y la humedad con patrón y equipo variables, de modo que «¿lleva punto esta lectura?» no
     * tiene una respuesta para todo el aparato.
     */
    private List<VerificationReading> traducirLecturas(
            EquipmentType tipo, List<VerificationReadingCommand> lecturas) {

        if (!tipo.isVerificable()) {
            throw new IllegalArgumentException("El tipo de equipo " + tipo.getNombre()
                    + " no se verifica: no hay lecturas que registrarle");
        }
        if (lecturas == null || lecturas.isEmpty()) {
            throw new IllegalArgumentException(
                    "Registrar una verificacion sin lecturas no es registrar nada");
        }

        return lecturas.stream().map(lectura -> traducirLectura(tipo, lectura)).toList();
    }

    private VerificationReading traducirLectura(
            EquipmentType tipo, VerificationReadingCommand lectura) {

        TypeVerification verificacion = requireActiveVerification(tipo, lectura.idVerificacion());

        return verificacion.modalidad() == VerificationMode.PATRON_EQUIPO_VARIABLE
                ? sinPunto(tipo, verificacion, lectura)
                : enPunto(tipo, verificacion, lectura);
    }

    /**
     * Que la verificación señalada sea una de las que el tipo declara hoy.
     *
     * <p>Activa y no cualquiera: un reporte nuevo no se puede llenar contra una configuración retirada,
     * aunque los reportes viejos sigan apuntando a ella para poder imprimirse.
     */
    private TypeVerification requireActiveVerification(EquipmentType tipo, UUID idVerificacion) {
        if (idVerificacion == null) {
            throw new IllegalArgumentException("El tipo " + tipo.getNombre() + " se verifica en "
                    + tipo.verificacionesActivas().size()
                    + " magnitudes: toda lectura declara a cual pertenece");
        }

        return tipo.verificacionesActivas().stream()
                .filter(activa -> activa.id().equals(idVerificacion))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La verificacion " + idVerificacion
                        + " no es una verificacion activa del tipo " + tipo.getNombre()));
    }

    /** Con patrón y equipo variables no hay punto; la unidad sale igualmente de la verificación. */
    private VerificationReading sinPunto(
            EquipmentType tipo, TypeVerification verificacion, VerificationReadingCommand lectura) {

        if (lectura.idPuntoVerificacion() != null) {
            throw new IllegalArgumentException("La verificacion de "
                    + verificacion.magnitud().nombre() + " del tipo " + tipo.getNombre()
                    + " se hace con patron y equipo variables: no tiene puntos, y se recibio el "
                    + lectura.idPuntoVerificacion());
        }

        return VerificationReading.of(verificacion.id(), null, lectura.secuencia(),
                lectura.valorPatron(), lectura.valorEquipo());
    }

    /** Con una modalidad constante toda lectura se toma en un punto declarado de esa verificación. */
    private VerificationReading enPunto(
            EquipmentType tipo, TypeVerification verificacion, VerificationReadingCommand lectura) {

        if (lectura.idPuntoVerificacion() == null) {
            throw new IllegalArgumentException("La verificacion de "
                    + verificacion.magnitud().nombre() + " del tipo " + tipo.getNombre()
                    + " se hace en " + verificacion.puntosActivos().size()
                    + " puntos: toda lectura declara en cual se tomo");
        }

        // De esa verificacion, no del tipo: con el modelo anterior un punto de temperatura habria
        // servido para una lectura de humedad, porque los puntos colgaban del aparato entero.
        VerificationPoint punto = verificacion.puntosActivos().stream()
                .filter(activo -> activo.id().equals(lectura.idPuntoVerificacion()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El punto "
                        + lectura.idPuntoVerificacion() + " no es un punto activo de la verificacion de "
                        + verificacion.magnitud().nombre() + " del tipo " + tipo.getNombre()));

        if (lectura.secuencia() > verificacion.cantidadDatos()) {
            throw new IllegalArgumentException("La verificacion de "
                    + verificacion.magnitud().nombre() + " declara " + verificacion.cantidadDatos()
                    + " lecturas por punto, y se recibio la numero " + lectura.secuencia());
        }

        // La unidad no viaja en el comando ni en la lectura: es la de la verificacion, desde V51 leida del
        // tipo mientras el reporte es borrador y del documento al cerrarlo.
        return VerificationReading.of(verificacion.id(), punto.id(), lectura.secuencia(),
                lectura.valorPatron(), lectura.valorEquipo());
    }

    /**
     * La verificación está completa, o el equipo quedó fuera de servicio.
     *
     * <p><b>La excepción no es una concesión, es el caso normal de un equipo averiado</b>: a un equipo
     * que no enciende no se le puede tomar una lectura, y exigirlas obligaría a inventárselas para poder
     * cerrar el reporte que precisamente dice que está fuera de servicio.
     *
     * <p><b>Se exige por verificación, y eso es más estricto que antes.</b> Con una modalidad constante
     * se piden todas las lecturas de todos los puntos activos de esa verificación; con patrón y equipo
     * variables, al menos una <b>de esa verificación</b>, porque cuántas tomar lo decide el ingeniero.
     * Con el modelo anterior, un termohigrómetro con las dos magnitudes variables se habría podido
     * cerrar con una sola lectura de temperatura y ninguna de humedad.
     */
    private void requireCompleteVerification(ServiceReport reporte) {
        EquipmentType tipo = tipoDelEquipo(reporte.getIdEquipoCliente());

        if (!tipo.isVerificable() || reporte.getResultado() == ServiceResult.FUERA_DE_SERVICIO) {
            return;
        }

        List<VerificationReading> tomadas = reporte.lecturasActivas();

        for (TypeVerification verificacion : tipo.verificacionesActivas()) {
            List<VerificationReading> suyas = tomadas.stream()
                    .filter(lectura -> lectura.idVerificacion().equals(verificacion.id()))
                    .toList();

            if (verificacion.modalidad() == VerificationMode.PATRON_EQUIPO_VARIABLE) {
                if (suyas.isEmpty()) {
                    throw new IllegalStateException("El tipo " + tipo.getNombre() + " verifica "
                            + verificacion.magnitud().nombre()
                            + ", y este reporte no tiene ninguna lectura de esa magnitud");
                }

                continue;
            }

            for (VerificationPoint punto : verificacion.puntosActivos()) {
                long enEsePunto = suyas.stream()
                        .filter(lectura ->
                                Objects.equals(lectura.idPuntoVerificacion(), punto.id()))
                        .count();

                if (enEsePunto < verificacion.cantidadDatos()) {
                    throw new IllegalStateException("El punto " + punto.valor() + " "
                            + verificacion.unidad().simbolo() + " de "
                            + verificacion.magnitud().nombre() + " tiene " + enEsePunto + " de las "
                            + verificacion.cantidadDatos() + " lecturas que el tipo "
                            + tipo.getNombre() + " declara");
                }
            }
        }
    }

    /**
     * Cada verificación con lecturas dice contra qué patrón se tomaron (V30). El agregado lo exige
     * también al cerrar, pero solo conoce el identificador de la verificación; aquí se tiene el tipo
     * delante y el mensaje puede decir «temperatura» en vez de un UUID.
     */
    private void requireStandardsForReadings(ServiceReport reporte) {
        List<VerificationReading> tomadas = reporte.lecturasActivas();
        if (tomadas.isEmpty()) {
            return;
        }

        EquipmentType tipo = tipoDelEquipo(reporte.getIdEquipoCliente());
        for (TypeVerification verificacion : tipo.getVerificaciones()) {
            boolean medida = tomadas.stream().anyMatch(l -> l.idVerificacion().equals(verificacion.id()));
            boolean conPatron = reporte.patronesActivos().stream()
                    .anyMatch(usado -> usado.idVerificacion().equals(verificacion.id()));

            if (medida && !conPatron) {
                throw new IllegalStateException("No se puede cerrar el reporte: la verificacion de "
                        + verificacion.magnitud().nombre() + " tiene lecturas y no dice contra que patron"
                        + " se tomaron");
            }
        }
    }

    /**
     * El tipo de equipo de una unidad, caminando la cadena del catálogo.
     *
     * <p>Cuatro consultas: unidad → modelo → equipo del catálogo → tipo. Así está construido el
     * catálogo —«equipo» es a la vez categoría y máquina— y ninguna de las tres tablas intermedias
     * guarda un atajo hacia el tipo. Se deja en un solo método para que el coste sea visible y para
     * que el día que haga falta un atajo se sepa exactamente dónde ponerlo.
     */
    private EquipmentType tipoDelEquipo(UUID idEquipoCliente) {
        // Sin restriccion: resuelve la forma de la tabla de verificacion, no datos de un cliente, y
        // quien entra a este camino ya paso por la comprobacion de su propio recurso.
        ClientEquipment unidad = clientEquipmentServicePort.findById(idEquipoCliente, ReadScope.unrestricted());
        UUID idEquipoCatalogo = modelServicePort.findById(unidad.getIdModelo()).getIdEquipo();
        UUID idTipo = equipmentServicePort.findById(idEquipoCatalogo).getIdTipoEquipo();

        return equipmentTypeServicePort.findById(idTipo);
    }
}
