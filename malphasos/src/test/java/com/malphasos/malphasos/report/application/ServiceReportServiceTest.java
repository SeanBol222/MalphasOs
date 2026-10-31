package com.malphasos.malphasos.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.company.application.ports.input.ServiceCompanyServicePort;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentTypeServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ModelServicePort;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
import com.malphasos.malphasos.equipment.domain.magnitude.Magnitude;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.equipment.domain.model.TechnicalSheet;
import com.malphasos.malphasos.report.UnDocumento;
import com.malphasos.malphasos.report.application.model.ReportDocumentView;
import com.malphasos.malphasos.report.application.model.WorkOrderSummarySheet;
import com.malphasos.malphasos.report.application.ports.output.WorkOrderSummaryPdfPort;
import com.malphasos.malphasos.shared.application.ports.output.DocumentImageStorePort;
import com.malphasos.malphasos.report.application.ports.output.ReportPdfPort;
import com.malphasos.malphasos.report.application.model.ReportArchive;
import com.malphasos.malphasos.report.application.model.ReportPdf;
import com.malphasos.malphasos.report.application.model.ReportSheet;
import com.malphasos.malphasos.report.application.ports.output.ServiceReportPersistencePort;
import com.malphasos.malphasos.report.application.services.serviceReport.ReportDocumentComposer;
import com.malphasos.malphasos.report.application.services.serviceReport.ServiceReportDrafts;
import com.malphasos.malphasos.report.application.services.serviceReport.ServiceReportService;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FillServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FinishServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.DiscardServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.MarkReportStepCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.ReplaceServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordHandoverCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordIntakeCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordVerificationCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RemoveReferenceStandardCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.ReceiptScope;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.SignReceiptCommand;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportReceiptSignedEvent;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.VerificationReadingCommand;
import com.malphasos.malphasos.report.domain.exception.ServiceReportNotFoundException;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import com.malphasos.malphasos.report.domain.serviceReport.Handover;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeCheck;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeState;
import com.malphasos.malphasos.report.domain.serviceReport.ReportAccessory;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.equipment.application.ports.input.ProtocolServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ReferenceStandardServicePort;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandardSheet;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.UseReferenceStandardCommand;
import com.malphasos.malphasos.report.application.model.WorkOrderReportProgress;
import com.malphasos.malphasos.report.domain.serviceReport.UsedReferenceStandard;
import com.malphasos.malphasos.equipment.application.model.AssignedProtocolDetail;
import com.malphasos.malphasos.equipment.domain.protocol.Protocol;
import com.malphasos.malphasos.report.domain.serviceReport.ReportStep;
import com.malphasos.malphasos.report.domain.serviceReport.StepState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationReading;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.RemoveEquipmentFromWorkOrderCommand;
import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderNotFoundException;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Las reglas cruzadas del reporte: las que exigen preguntar a la orden de trabajo o al catálogo de
 * equipos, y que por eso no caben ni en el esquema ni en el agregado.
 *
 * <p>Todas las consultas que una regla atraviesa van estubadas. Es la lección de
 * {@code EquipmentChainServiceTest}, donde una guarda pasó meses comparando {@code null} contra
 * {@code null} sin que Mockito estricto dijera nada: vigila los estubados que sobran, no las llamadas
 * sin estubar.
 */
@ExtendWith(MockitoExtension.class)
class ServiceReportServiceTest {

    private static final UUID ORDEN = UUID.randomUUID();
    private static final UUID EQUIPO = UUID.randomUUID();
    private static final UUID AREA = UUID.randomUUID();
    private static final UUID MODELO = UUID.randomUUID();
    private static final UUID EQUIPO_CATALOGO = UUID.randomUUID();
    private static final UUID TIPO = UUID.randomUUID();

    @Mock private ServiceReportPersistencePort serviceReportPersistencePort;
    @Mock private WorkOrderServicePort workOrderServicePort;
    @Mock private ClientEquipmentServicePort clientEquipmentServicePort;
    @Mock private ModelServicePort modelServicePort;
    @Mock private EquipmentServicePort equipmentServicePort;
    @Mock private EquipmentTypeServicePort equipmentTypeServicePort;
    @Mock private EventDispatcherPort eventDispatcherPort;
    @Mock private ProtocolServicePort protocolServicePort;
    @Mock private ReferenceStandardServicePort referenceStandardServicePort;
    @Mock private ReportDocumentComposer reportDocumentComposer;
    @Mock private DocumentImageStorePort documentImageStorePort;
    @Mock private ReportPdfPort reportPdfPort;
    @Mock private WorkOrderSummaryPdfPort workOrderSummaryPdfPort;
    @Mock private ServiceCompanyServicePort serviceCompanyServicePort;
    @Mock private ServiceReportDrafts serviceReportDrafts;

    @InjectMocks private ServiceReportService service;

    // ---- Dobles de los otros modulos ----

    private static WorkOrder unaOrden(ExecutionState estado, boolean activa) {
        return unaOrden(ServiceType.PREVENTIVO, estado, activa);
    }

    private static WorkOrder unaOrden(ServiceType servicio, ExecutionState estado, boolean activa) {
        return WorkOrder.rehydrate(
                ORDEN,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                Periodicity.ANUAL,
                servicio,
                estado,
                UUID.randomUUID(),
                activa);
    }

    /** La orden en la que todo se puede reportar: empezada y activa. */
    private static WorkOrder unaOrdenEnEjecucion() {
        return unaOrden(ExecutionState.EN_EJECUCION, true);
    }

    /**
     * Desde V50 toda escritura mira la orden del reporte: tiene que haber empezado. Por defecto lo está, y
     * con {@code lenient} porque las lecturas no la miran; las pruebas de esa regla la cambian.
     */
    @BeforeEach
    void laOrdenHaEmpezado() {
        lenient().when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted())).thenReturn(unaOrdenEnEjecucion());
    }

    private static final UUID ID_PRESION = UUID.randomUUID();
    private static final UUID ID_TEMPERATURA = UUID.randomUUID();

    private static final Magnitude PRESION =
            new Magnitude(ID_PRESION, "presion", "Presión", true);
    private static final MeasurementUnit MMHG = new MeasurementUnit(
            UUID.randomUUID(), ID_PRESION, "mmHg", "milímetro de mercurio", true);
    private static final Magnitude TEMPERATURA =
            new Magnitude(ID_TEMPERATURA, "temperatura", "Temperatura", true);
    private static final MeasurementUnit GRADOS = new MeasurementUnit(
            UUID.randomUUID(), ID_TEMPERATURA, "°C", "grado Celsius", true);

    /** Un tipo con una sola verificación, de presión, o sin ninguna si la modalidad llega nula. */
    private static EquipmentType unTipo(
            VerificationMode modalidad, Integer cantidadDatos, List<VerificationPoint> puntos) {

        return tipoCon(modalidad == null
                ? List.of()
                : List.of(TypeVerification.of(PRESION, MMHG, modalidad, cantidadDatos, puntos)));
    }

    private static EquipmentType tipoCon(List<TypeVerification> verificaciones) {
        return EquipmentType.rehydrate(TIPO, "Tensiometro", "Definicion", "Cuidados", "Electronica",
                null, null, verificaciones, 150_000L, true);
    }

    /** La única verificación activa del tipo, que es a la que apuntan casi todas las lecturas. */
    private static TypeVerification laVerificacionDe(EquipmentType tipo) {
        return tipo.verificacionesActivas().getFirst();
    }

    /** Un patrón con el certificado vigente un año más: el que se usa cuando el patrón no es lo que se prueba. */
    private static final UsedReferenceStandard PATRON_VIGENTE = new UsedReferenceStandard(UUID.randomUUID(),
            "Juego de pesas", "Kern", "KF1-1", "CAL-1", LocalDate.now().plusYears(1));

    private static VerificationPoint unPunto(String valor) {
        return VerificationPoint.of(new BigDecimal(valor));
    }

    private static ServiceReport unReporte() {
        ServiceReport reporte = ServiceReport.open(ORDEN, EQUIPO);
        // Cerrar exige el ingreso desde el diseño único del informe; lo que se prueba aquí es lo demás.
        reporte.recordIntake(new IntakeCheck(IntakeState.CONFORME, null),
                new IntakeCheck(IntakeState.CONFORME, null), List.of());
        reporte.pullEvents();

        return reporte;
    }

    /**
     * Estuba la cadena del catálogo hasta el tipo: unidad → modelo → equipo → tipo.
     *
     * <p>Son cuatro consultas y las cuatro hacen falta. Si alguna se dejara sin estubar, Mockito
     * devolvería {@code null} y la regla que se quiere probar lanzaría un
     * {@code NullPointerException} en vez de ejercerse.
     */
    private void estubarCadenaDelCatalogo(EquipmentType tipo) {
        when(clientEquipmentServicePort.findById(EQUIPO, ReadScope.unrestricted())).thenReturn(
                ClientEquipment.rehydrate(EQUIPO, "SN-1", MODELO, AREA, null, null, null, null, null, null, true));
        when(modelServicePort.findById(MODELO)).thenReturn(
                Model.rehydrate(MODELO, "IdeaPad 3", null, UUID.randomUUID(), EQUIPO_CATALOGO, TechnicalSheet.EMPTY, true));
        when(equipmentServicePort.findById(EQUIPO_CATALOGO)).thenReturn(
                Equipment.rehydrate(EQUIPO_CATALOGO, TIPO, UUID.randomUUID(), true));
        when(equipmentTypeServicePort.findById(TIPO)).thenReturn(tipo);
    }

    /** Los protocolos del tipo del equipo, con la cadena del catálogo estubada hasta llegar a él. */
    private void estubarProtocolos(AssignedProtocolDetail... protocolos) {
        estubarCadenaDelCatalogo(unTipo(null, null, List.of()));
        when(protocolServicePort.protocolsOf(TIPO)).thenReturn(List.of(protocolos));
    }

    private void estubarGuardado() {
        when(serviceReportPersistencePort.save(any(ServiceReport.class)))
                .thenAnswer(llamada -> llamada.getArgument(0));
    }

    private void estubarReporte(ServiceReport reporte) {
        when(serviceReportPersistencePort.findById(reporte.getId())).thenReturn(Optional.of(reporte));
    }

    /**
     * Un reporte cuenta lo que se hizo (V50): desde que el borrador nace al añadir el equipo, aunque la
     * orden no haya empezado, la regla que se comprobaba al abrirlo pasa a <b>cada escritura</b>. Se prueban
     * todas, porque basta con que una se salte la comprobación para escribir en una orden sin empezar.
     */
    @Nested
    @DisplayName("Al escribir, la orden tiene que haber empezado")
    class LaOrdenHaDeHaberEmpezado {

        private List<org.assertj.core.api.ThrowableAssert.ThrowingCallable> todasLasEscrituras(UUID id) {
            return List.of(
                    () -> service.fill(new FillServiceReportCommand(id, null, null, "Limpieza", null, null, null,
                            ServiceResult.OPERATIVO)),
                    () -> service.recordVerification(new RecordVerificationCommand(id, List.of())),
                    () -> service.recordIntake(new RecordIntakeCommand(id, IntakeState.CONFORME, null,
                            IntakeState.CONFORME, null, List.of())),
                    () -> service.recordHandover(new RecordHandoverCommand(id, "Ana", null, null)),
                    () -> service.signReceipt(new SignReceiptCommand(id, "Ana", null, null, new byte[] {1}, ReceiptScope.REPORTE)),
                    () -> service.useReferenceStandard(new UseReferenceStandardCommand(id, UUID.randomUUID(),
                            UUID.randomUUID())),
                    () -> service.removeReferenceStandard(new RemoveReferenceStandardCommand(id, UUID.randomUUID(),
                            UUID.randomUUID())),
                    () -> service.markStep(new MarkReportStepCommand(id, UUID.randomUUID(), StepState.HECHO, null)),
                    () -> service.finish(new FinishServiceReportCommand(id)));
        }

        @Test
        @DisplayName("ninguna de las nueve escrituras entra en un borrador de una orden sin empezar")
        void ningunaSinEmpezar() {
            ServiceReport borrador = unReporte();
            estubarReporte(borrador);
            when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted()))
                    .thenReturn(unaOrden(ExecutionState.CREADA, true));

            for (var escritura : todasLasEscrituras(borrador.getId())) {
                assertThatIllegalStateException().isThrownBy(escritura).withMessageContaining("no ha empezado");
            }
            verify(serviceReportPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("ni en una de una orden cancelada")
        void ningunaCancelada() {
            ServiceReport borrador = unReporte();
            estubarReporte(borrador);
            when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted()))
                    .thenReturn(unaOrden(ExecutionState.EN_EJECUCION, false));

            for (var escritura : todasLasEscrituras(borrador.getId())) {
                assertThatIllegalStateException().isThrownBy(escritura).withMessageContaining("cancelada");
            }
            verify(serviceReportPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("una orden ya ejecutada si admite escribir: el sustituto de un cerrado se rellena despues")
        void ejecutadaAdmite() {
            ServiceReport borrador = unReporte();
            estubarReporte(borrador);
            when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted()))
                    .thenReturn(unaOrden(ExecutionState.EJECUTADA, true));
            estubarGuardado();

            assertThatCode(() -> service.recordHandover(new RecordHandoverCommand(borrador.getId(), "Ana", null, null)))
                    .doesNotThrowAnyException();
        }
    }

    /** Sustituir un reporte cerrado: retirarlo y abrir el borrador del mismo equipo en un paso (V50). */
    @Nested
    @DisplayName("Al sustituir un reporte cerrado")
    class AlSustituir {

        private ServiceReport unCerrado() {
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Limpieza", null, null, null, ServiceResult.OPERATIVO);
            reporte.finish(UnDocumento.CUALQUIERA);
            reporte.pullEvents();
            estubarReporte(reporte);
            return reporte;
        }

        @Test
        @DisplayName("retira el cerrado y abre el borrador del mismo equipo, con el tipo de servicio de la orden")
        void retiraYAbre() {
            ServiceReport cerrado = unCerrado();
            ServiceReport borrador = ServiceReport.open(ORDEN, EQUIPO);
            when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted()))
                    .thenReturn(unaOrden(ServiceType.CORRECTIVO, ExecutionState.EJECUTADA, true));
            estubarGuardado();
            when(serviceReportDrafts.open(ORDEN, ServiceType.CORRECTIVO, EQUIPO)).thenReturn(borrador);

            assertThat(service.replace(new ReplaceServiceReportCommand(cerrado.getId()))).isSameAs(borrador);

            // Retirado y guardado antes de abrir el borrador: si no, el indice unico parcial veria dos activos.
            var orden = org.mockito.Mockito.inOrder(serviceReportPersistencePort, serviceReportDrafts);
            orden.verify(serviceReportPersistencePort).save(cerrado);
            orden.verify(serviceReportDrafts).open(ORDEN, ServiceType.CORRECTIVO, EQUIPO);
            assertThat(cerrado.isEstadoActivo()).isFalse();
        }

        @Test
        @DisplayName("un borrador no se sustituye: se corrige escribiendo en el")
        void borradorNo() {
            ServiceReport borrador = unReporte();
            estubarReporte(borrador);

            assertThatIllegalStateException()
                    .isThrownBy(() -> service.replace(new ReplaceServiceReportCommand(borrador.getId())))
                    .withMessageContaining("borrador");
            verify(serviceReportDrafts, never()).open(any(), any(), any());
            verify(serviceReportPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("ni uno ya retirado, ni uno de una orden cancelada")
        void retiradoOCanceladaNo() {
            ServiceReport retirado = unCerrado();
            retirado.deactivate();

            assertThatIllegalStateException()
                    .isThrownBy(() -> service.replace(new ReplaceServiceReportCommand(retirado.getId())))
                    .withMessageContaining("retirado");

            ServiceReport cerrado = unCerrado();
            when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted()))
                    .thenReturn(unaOrden(ExecutionState.EJECUTADA, false));

            assertThatIllegalStateException()
                    .isThrownBy(() -> service.replace(new ReplaceServiceReportCommand(cerrado.getId())))
                    .withMessageContaining("cancelada");
            verify(serviceReportDrafts, never()).open(any(), any(), any());
        }
    }

    /** Retirar un borrador es sacar su equipo de la orden (V50); un cerrado no se retira. */
    @Nested
    @DisplayName("Al retirar un reporte")
    class AlRetirar {

        @Test
        @DisplayName("un borrador sale con su equipo: lo hace el servicio de ordenes, con su regla")
        void borradorSacaElEquipo() {
            ServiceReport borrador = unReporte();
            estubarReporte(borrador);

            service.discard(new DiscardServiceReportCommand(borrador.getId()));

            verify(workOrderServicePort).removeEquipment(new RemoveEquipmentFromWorkOrderCommand(ORDEN, EQUIPO));
            verify(serviceReportPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("un cerrado no se retira, se sustituye")
        void cerradoNo() {
            ServiceReport cerrado = unReporte();
            cerrado.fill(null, null, "Limpieza", null, null, null, ServiceResult.OPERATIVO);
            cerrado.finish(UnDocumento.CUALQUIERA);
            estubarReporte(cerrado);

            assertThatIllegalStateException()
                    .isThrownBy(() -> service.discard(new DiscardServiceReportCommand(cerrado.getId())))
                    .withMessageContaining("se sustituye");
            verify(workOrderServicePort, never()).removeEquipment(any());
        }

        @Test
        @DisplayName("retirar uno ya retirado no hace nada")
        void retiradoNoHaceNada() {
            ServiceReport retirado = unReporte();
            retirado.deactivate();
            estubarReporte(retirado);

            service.discard(new DiscardServiceReportCommand(retirado.getId()));

            verify(workOrderServicePort, never()).removeEquipment(any());
        }
    }

    @Nested
    @DisplayName("Al registrar la verificacion")
    class AlVerificar {

        private static final UUID PUNTO_AJENO = UUID.randomUUID();

        @Test
        @DisplayName("un borrador dice sus pasos, verificaciones y patrones leyendo hoy el catalogo (V51)")
        void elCatalogoDeUnBorrador() {
            // Desde V51 la lectura no copia la unidad ni la tolerancia: un borrador las lee del tipo, por el
            // compositor, que es el mismo que las congela al cerrar.
            ServiceReport reporte = unReporte();
            EquipmentType tipo = unTipo(VerificationMode.PATRON_EQUIPO_VARIABLE, null, List.of());
            estubarCadenaDelCatalogo(tipo);
            com.malphasos.malphasos.report.application.model.ReportCatalog vivo =
                    new com.malphasos.malphasos.report.application.model.ReportCatalog(List.of(), List.of(), List.of());
            when(reportDocumentComposer.catalog(reporte, tipo)).thenReturn(vivo);

            assertThat(service.catalogOf(reporte)).isSameAs(vivo);
        }

        @Test
        @DisplayName("uno cerrado lo dice su documento, sin tocar el catalogo: la tolerancia de hoy no lo cambia")
        void elCatalogoDeUnCerrado() {
            UUID verificacion = UUID.randomUUID();
            ReportDocument base = UnDocumento.CUALQUIERA;
            ReportDocument documento = new ReportDocument(base.version(), base.orden(), base.cliente(), base.equipo(),
                    List.of(new ReportDocument.Verificacion(verificacion, "Presión", "mmHg", 1, new BigDecimal("0.5"),
                            List.of())),
                    null, null, null, List.of(), List.of());
            ServiceReport cerrado = ServiceReport.rehydrate(UUID.randomUUID(), ORDEN, EQUIPO, ReportState.FINALIZADO,
                    null, null, "Limpieza", null, null, null, List.of(), null, null, List.of(), null,
                    ServiceResult.OPERATIVO, java.time.LocalDateTime.of(2026, 10, 1, 9, 0), List.of(), List.of(), true,
                    documento, false);

            assertThat(service.catalogOf(cerrado).verificacion(verificacion)).get()
                    .extracting(ReportDocument.Verificacion::tolerancia).isEqualTo(new BigDecimal("0.5"));
            verify(reportDocumentComposer, never()).catalog(any(), any());
            verify(clientEquipmentServicePort, never()).findById(any(), any());
        }

        @Test
        @DisplayName("acepta las lecturas de un punto de su verificacion")
        void aceptaLasLecturasDelPunto() {
            ServiceReport reporte = unReporte();
            VerificationPoint punto = unPunto("50");
            EquipmentType tipo = unTipo(VerificationMode.PATRON_CONSTANTE, 2, List.of(punto));
            UUID verificacion = laVerificacionDe(tipo).id();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);
            estubarGuardado();

            service.recordVerification(new RecordVerificationCommand(reporte.getId(), List.of(
                    new VerificationReadingCommand(
                            verificacion, punto.id(), 1, new BigDecimal("50"), new BigDecimal("50.2")),
                    new VerificationReadingCommand(
                            verificacion, punto.id(), 2, new BigDecimal("50"), new BigDecimal("49.8")))));

            assertThat(reporte.lecturasActivas()).hasSize(2);
            // La unidad no viaja en el comando ni en la lectura (V51): es la de la verificacion.
            assertThat(reporte.lecturasActivas()).extracting(VerificationReading::idVerificacion)
                    .containsOnly(verificacion);
        }

        @Test
        @DisplayName("un tipo con dos magnitudes acepta lecturas de cada una, con su propia unidad")
        void aceptaDosMagnitudes() {
            // El caso que el modelo anterior no sabia expresar: con una sola modalidad y una sola
            // cantidad por tipo, las lecturas de temperatura y de presion eran indistinguibles.
            ServiceReport reporte = unReporte();
            VerificationPoint mmhg = unPunto("50");
            VerificationPoint celsius = unPunto("37");
            EquipmentType tipo = tipoCon(List.of(
                    TypeVerification.of(PRESION, MMHG, VerificationMode.PATRON_CONSTANTE, 1,
                            List.of(mmhg)),
                    TypeVerification.of(TEMPERATURA, GRADOS, VerificationMode.EQUIPO_CONSTANTE, 1,
                            List.of(celsius))));
            UUID dePresion = tipo.verificacionesActivas().getFirst().id();
            UUID deTemperatura = tipo.verificacionesActivas().get(1).id();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);
            estubarGuardado();

            service.recordVerification(new RecordVerificationCommand(reporte.getId(), List.of(
                    new VerificationReadingCommand(
                            dePresion, mmhg.id(), 1, new BigDecimal("50"), new BigDecimal("50.2")),
                    new VerificationReadingCommand(
                            deTemperatura, celsius.id(), 1, new BigDecimal("37"), new BigDecimal("36.8")))));

            assertThat(reporte.lecturasActivas()).extracting(VerificationReading::idVerificacion)
                    .containsExactlyInAnyOrder(dePresion, deTemperatura);
        }

        @Test
        @DisplayName("un tipo que no se verifica no admite lecturas")
        void rechazaUnTipoQueNoSeVerifica() {
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(unTipo(null, null, List.of()));

            assertThatIllegalArgumentException().isThrownBy(() -> service.recordVerification(
                            new RecordVerificationCommand(reporte.getId(), List.of(
                                    new VerificationReadingCommand(
                                            UUID.randomUUID(), null, 1, BigDecimal.ONE, BigDecimal.TWO)))))
                    .withMessageContaining("no se verifica");
        }

        @Test
        @DisplayName("una verificacion que no es de ese tipo se rechaza")
        void rechazaUnaVerificacionAjena() {
            // La foranea de dato_verificacion comprueba que la verificacion exista y que el punto sea
            // suyo, pero no que sea del tipo del equipo reportado: son cuatro saltos desde
            // equipo_cliente hasta tipo_equipo. Eso sigue siendo del servicio.
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(
                    unTipo(VerificationMode.PATRON_CONSTANTE, 1, List.of(unPunto("50"))));

            assertThatIllegalArgumentException().isThrownBy(() -> service.recordVerification(
                            new RecordVerificationCommand(reporte.getId(), List.of(
                                    new VerificationReadingCommand(
                                            UUID.randomUUID(), null, 1, BigDecimal.ONE, BigDecimal.TWO)))))
                    .withMessageContaining("no es una verificacion activa");
        }

        @Test
        @DisplayName("una lectura sin verificacion se rechaza")
        void exigeLaVerificacion() {
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(
                    unTipo(VerificationMode.PATRON_CONSTANTE, 1, List.of(unPunto("50"))));

            assertThatIllegalArgumentException().isThrownBy(() -> service.recordVerification(
                            new RecordVerificationCommand(reporte.getId(), List.of(
                                    new VerificationReadingCommand(
                                            null, null, 1, BigDecimal.ONE, BigDecimal.TWO)))))
                    .withMessageContaining("declara a cual pertenece");
        }

        @Test
        @DisplayName("un punto que no es de esa verificacion se rechaza")
        void rechazaUnPuntoAjeno() {
            ServiceReport reporte = unReporte();
            EquipmentType tipo = unTipo(VerificationMode.PATRON_CONSTANTE, 1, List.of(unPunto("50")));
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);

            assertThatIllegalArgumentException().isThrownBy(() -> service.recordVerification(
                            new RecordVerificationCommand(reporte.getId(), List.of(
                                    new VerificationReadingCommand(laVerificacionDe(tipo).id(),
                                            PUNTO_AJENO, 1, BigDecimal.ONE, BigDecimal.TWO)))))
                    .withMessageContaining("no es un punto activo");
        }

        @Test
        @DisplayName("el punto de OTRA verificacion del mismo tipo tampoco vale")
        void rechazaElPuntoDeLaOtraMagnitud() {
            // Con el modelo anterior esto era imposible de detectar: los puntos colgaban del aparato
            // entero, de modo que un punto de temperatura era un punto valido para una lectura de
            // presion y el reporte salia impreso con el valor en la columna equivocada.
            ServiceReport reporte = unReporte();
            VerificationPoint mmhg = unPunto("50");
            VerificationPoint celsius = unPunto("37");
            EquipmentType tipo = tipoCon(List.of(
                    TypeVerification.of(PRESION, MMHG, VerificationMode.PATRON_CONSTANTE, 1,
                            List.of(mmhg)),
                    TypeVerification.of(TEMPERATURA, GRADOS, VerificationMode.EQUIPO_CONSTANTE, 1,
                            List.of(celsius))));
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);

            assertThatIllegalArgumentException().isThrownBy(() -> service.recordVerification(
                            new RecordVerificationCommand(reporte.getId(), List.of(
                                    new VerificationReadingCommand(
                                            tipo.verificacionesActivas().getFirst().id(),
                                            celsius.id(), 1, BigDecimal.ONE, BigDecimal.TWO)))))
                    .withMessageContaining("no es un punto activo");
        }

        @Test
        @DisplayName("un punto retirado tampoco vale para una lectura nueva")
        void rechazaUnPuntoRetirado() {
            ServiceReport reporte = unReporte();
            VerificationPoint retirado = unPunto("50").deactivated();
            // rehydrate y no of: of exige al menos un punto ACTIVO con modalidad constante, que es
            // justamente la regla que impide construir este estado por la puerta principal. Leer de la
            // base no valida, y es lo que permite montar la fila que ya existe y hay que rechazar.
            EquipmentType tipo = tipoCon(List.of(TypeVerification.rehydrate(UUID.randomUUID(),
                    PRESION, MMHG, VerificationMode.PATRON_CONSTANTE, 1, List.of(retirado), true)));
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);

            assertThatIllegalArgumentException().isThrownBy(() -> service.recordVerification(
                    new RecordVerificationCommand(reporte.getId(), List.of(
                            new VerificationReadingCommand(laVerificacionDe(tipo).id(),
                                    retirado.id(), 1, BigDecimal.ONE, BigDecimal.TWO)))));
        }

        @Test
        @DisplayName("una verificacion retirada no vale para una lectura nueva")
        void rechazaUnaVerificacionRetirada() {
            // Los reportes viejos siguen apuntando a ella para poder imprimirse, pero un reporte nuevo
            // no se llena contra una configuracion que ya no esta vigente.
            ServiceReport reporte = unReporte();
            TypeVerification retirada = TypeVerification.rehydrate(UUID.randomUUID(), PRESION, MMHG,
                    VerificationMode.PATRON_CONSTANTE, 1, List.of(unPunto("50")), false);
            EquipmentType tipo = tipoCon(List.of(retirada,
                    TypeVerification.of(TEMPERATURA, GRADOS, VerificationMode.EQUIPO_CONSTANTE, 1,
                            List.of(unPunto("37")))));
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);

            assertThatIllegalArgumentException().isThrownBy(() -> service.recordVerification(
                            new RecordVerificationCommand(reporte.getId(), List.of(
                                    new VerificationReadingCommand(retirada.id(),
                                            retirada.puntos().getFirst().id(), 1,
                                            BigDecimal.ONE, BigDecimal.TWO)))))
                    .withMessageContaining("no es una verificacion activa");
        }

        @Test
        @DisplayName("con modalidad constante toda lectura declara su punto")
        void exigeElPuntoConModalidadConstante() {
            ServiceReport reporte = unReporte();
            EquipmentType tipo = unTipo(VerificationMode.EQUIPO_CONSTANTE, 1, List.of(unPunto("50")));
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);

            assertThatIllegalArgumentException().isThrownBy(() -> service.recordVerification(
                            new RecordVerificationCommand(reporte.getId(), List.of(
                                    new VerificationReadingCommand(laVerificacionDe(tipo).id(),
                                            null, 1, BigDecimal.ONE, BigDecimal.TWO)))))
                    .withMessageContaining("declara en cual se tomo");
        }

        @Test
        @DisplayName("mas lecturas por punto de las que esa verificacion declara se rechazan")
        void rechazaMasLecturasDeLasDeclaradas() {
            ServiceReport reporte = unReporte();
            VerificationPoint punto = unPunto("50");
            EquipmentType tipo = unTipo(VerificationMode.PATRON_CONSTANTE, 2, List.of(punto));
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);

            assertThatIllegalArgumentException().isThrownBy(() -> service.recordVerification(
                            new RecordVerificationCommand(reporte.getId(), List.of(
                                    new VerificationReadingCommand(laVerificacionDe(tipo).id(),
                                            punto.id(), 3, BigDecimal.ONE, BigDecimal.TWO)))))
                    .withMessageContaining("declara 2 lecturas por punto");
        }

        @Test
        @DisplayName("con patron y equipo variables no hay punto, y la unidad sale igualmente del tipo")
        void conModalidadVariableNoHayPunto() {
            ServiceReport reporte = unReporte();
            EquipmentType tipo = unTipo(VerificationMode.PATRON_EQUIPO_VARIABLE, null, List.of());
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);
            estubarGuardado();

            service.recordVerification(new RecordVerificationCommand(reporte.getId(), List.of(
                    new VerificationReadingCommand(laVerificacionDe(tipo).id(), null, 1,
                            new BigDecimal("1"), new BigDecimal("1.1")))));

            assertThat(reporte.lecturasActivas()).hasSize(1);
            assertThat(reporte.lecturasActivas().getFirst().idPuntoVerificacion()).isNull();
        }

        @Test
        @DisplayName("con patron y equipo variables, declarar un punto es un error")
        void conModalidadVariableElPuntoSobra() {
            ServiceReport reporte = unReporte();
            EquipmentType tipo = unTipo(VerificationMode.PATRON_EQUIPO_VARIABLE, null, List.of());
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);

            assertThatIllegalArgumentException().isThrownBy(() -> service.recordVerification(
                            new RecordVerificationCommand(reporte.getId(), List.of(
                                    new VerificationReadingCommand(laVerificacionDe(tipo).id(),
                                            UUID.randomUUID(), 1, BigDecimal.ONE, BigDecimal.TWO)))))
                    .withMessageContaining("no tiene puntos");
        }

        @Test
        @DisplayName("un reporte que no existe da 'no existe' y no toca el catalogo")
        void reporteInexistente() {
            UUID inventado = UUID.randomUUID();
            when(serviceReportPersistencePort.findById(inventado)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.recordVerification(
                            new RecordVerificationCommand(inventado, List.of())))
                    .isInstanceOf(ServiceReportNotFoundException.class);
            verify(equipmentTypeServicePort, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("Al cerrar")
    class AlCerrar {

        /**
         * El cierre consultaba la orden para saber si era un correctivo (2026-10-14 al 2026-10-27). Ya
         * no la necesita; el estubado se queda lenient para las pruebas que la consultan por otra razón.
         */
        @org.junit.jupiter.api.BeforeEach
        void laOrdenEsPreventiva() {
            lenient().when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted()))
                    .thenReturn(unaOrdenEnEjecucion());
            // Cerrar congela el informe (2026-10-17): lo que se congela lo prueba su propia sección.
            // Desde V51 el cierre comprueba el vencimiento de cada patrón en el documento: lleva el vigente.
            lenient().when(reportDocumentComposer.compose(any(), any())).thenAnswer(llamada -> {
                ServiceReport aCerrar = llamada.getArgument(0);
                ReportDocument base = UnDocumento.CUALQUIERA;
                return new ReportDocumentComposer.Composed(new ReportDocument(base.version(), base.orden(),
                        base.cliente(), base.equipo(), base.verificaciones(), base.empresa(), base.ingeniero(),
                        base.huellaFirmaIngeniero(), base.pasos(),
                        aCerrar.patronesActivos().stream()
                                .map(usado -> new ReportDocument.Patron(usado.idVerificacion(), PATRON_VIGENTE))
                                .toList()),
                        null, null);
            });
        }

        private ServiceReport unReporteSinVerificacion() {
            EquipmentType tipo = unTipo(null, null, List.of());
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Cambio de fusible", null, ServiceResult.OPERATIVO);
            reporte.pullEvents();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);
            return reporte;
        }

        @Test
        @DisplayName("un correctivo sin falla ni diagnostico se cierra: desde el 2026-10-27 no se exigen")
        void correctivoSinFalla() {
            // Los exigia desde el 2026-10-14; el usuario los quito para no frenar el cierre.
            ServiceReport reporte = unReporteSinVerificacion();
            lenient().when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted()))
                    .thenReturn(unaOrden(ServiceType.CORRECTIVO, ExecutionState.EN_EJECUCION, true));
            estubarGuardado();

            service.finish(new FinishServiceReportCommand(reporte.getId()));

            assertThat(reporte.getEstado()).isEqualTo(ReportState.FINALIZADO);
        }

        @Test
        @DisplayName("un preventivo sin falla ni diagnostico se cierra: eso es de un correctivo")
        void preventivoSinFalla() {
            ServiceReport reporte = unReporteSinVerificacion();
            estubarGuardado();

            service.finish(new FinishServiceReportCommand(reporte.getId()));

            assertThat(reporte.getEstado()).isEqualTo(ReportState.FINALIZADO);
        }

        @Test
        @DisplayName("cierra cuando estan todas las lecturas de todos los puntos")
        void cierraConLaVerificacionCompleta() {
            VerificationPoint punto = unPunto("50");
            EquipmentType tipo = unTipo(VerificationMode.PATRON_CONSTANTE, 2, List.of(punto));
            UUID verificacion = laVerificacionDe(tipo).id();
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Limpieza", null, ServiceResult.OPERATIVO);
            reporte.recordVerification(List.of(
                    VerificationReading.of(verificacion, punto.id(), 1,
                            new BigDecimal("50"), new BigDecimal("50.2")),
                    VerificationReading.of(verificacion, punto.id(), 2,
                            new BigDecimal("50"), new BigDecimal("49.9"))));
            reporte.useReferenceStandard(verificacion, PATRON_VIGENTE.id());
            reporte.pullEvents();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);
            estubarGuardado();

            service.finish(new FinishServiceReportCommand(reporte.getId()));

            assertThat(reporte.getEstado()).isEqualTo(ReportState.FINALIZADO);
        }

        @Test
        @DisplayName("no cierra con la tabla a medias: dos de tres lecturas no valen")
        void noCierraConLaVerificacionIncompleta() {
            VerificationPoint punto = unPunto("50");
            EquipmentType tipo = unTipo(VerificationMode.PATRON_CONSTANTE, 3, List.of(punto));
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Limpieza", null, ServiceResult.OPERATIVO);
            reporte.recordVerification(List.of(
                    VerificationReading.of(laVerificacionDe(tipo).id(), punto.id(), 1,
                            new BigDecimal("50"), new BigDecimal("50.2"))));
            reporte.pullEvents();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);

            assertThatIllegalStateException()
                    .isThrownBy(() -> service.finish(new FinishServiceReportCommand(reporte.getId())))
                    .withMessageContaining("1 de las 3 lecturas");
            assertThat(reporte.getEstado()).isEqualTo(ReportState.BORRADOR);
        }

        @Test
        @DisplayName("no cierra si falta entero un punto de los declarados")
        void noCierraSiFaltaUnPunto() {
            VerificationPoint uno = unPunto("50");
            VerificationPoint otro = unPunto("150");
            EquipmentType tipo = unTipo(VerificationMode.PATRON_CONSTANTE, 1, List.of(uno, otro));
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Limpieza", null, ServiceResult.OPERATIVO);
            reporte.recordVerification(List.of(
                    VerificationReading.of(laVerificacionDe(tipo).id(), uno.id(), 1,
                            new BigDecimal("50"), new BigDecimal("50.2"))));
            reporte.pullEvents();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);

            assertThatIllegalStateException()
                    .isThrownBy(() -> service.finish(new FinishServiceReportCommand(reporte.getId())))
                    .withMessageContaining("150");
        }

        @Test
        @DisplayName("con dos magnitudes, no cierra si falta entera la verificacion de una")
        void noCierraSiFaltaUnaMagnitudEntera() {
            // ESTA ES LA QUE FALTABA, y lo destapo una mutacion el 2026-10-04: quitar el filtro por
            // verificacion de requireCompleteVerification -- de modo que las lecturas de temperatura
            // contaran como lecturas de humedad -- dejaba la bateria entera en verde, 842 pruebas y
            // cero fallos. Ninguna prueba de cierre tenia DOS verificaciones, asi que separar o no
            // separar daba el mismo resultado en todas.
            //
            // El caso es el termohigrometro con las dos magnitudes variables: con el filtro quitado,
            // una sola lectura de presion satisfacia tambien a la de temperatura y el reporte se
            // cerraba diciendo que se habia verificado algo que nadie midio.
            VerificationPoint dePresion = unPunto("50");
            VerificationPoint deTemperatura = unPunto("37");
            EquipmentType tipo = tipoCon(List.of(
                    TypeVerification.of(PRESION, MMHG, VerificationMode.PATRON_CONSTANTE, 1,
                            List.of(dePresion)),
                    TypeVerification.of(TEMPERATURA, GRADOS, VerificationMode.EQUIPO_CONSTANTE, 1,
                            List.of(deTemperatura))));
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Limpieza", null, ServiceResult.OPERATIVO);
            // Solo las de presion. Las de temperatura no se tomaron.
            reporte.recordVerification(List.of(VerificationReading.of(
                    tipo.verificacionesActivas().getFirst().id(), dePresion.id(), 1,
                    new BigDecimal("50"), new BigDecimal("50.2"))));
            reporte.pullEvents();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);

            assertThatIllegalStateException()
                    .isThrownBy(() -> service.finish(new FinishServiceReportCommand(reporte.getId())))
                    .withMessageContaining("Temperatura");
            assertThat(reporte.getEstado()).isEqualTo(ReportState.BORRADOR);
        }

        @Test
        @DisplayName("con dos magnitudes VARIABLES, no cierra con lecturas de una sola")
        void noCierraConUnaSolaMagnitudVariableMedida() {
            // ESTE ES EL CASO QUE DE VERDAD DISTINGUE, y la primera prueba que escribi para esto no lo
            // tocaba: con modalidad CONSTANTE el identificador del punto ya separa las lecturas de una
            // magnitud de las de otra, asi que quitar el filtro por verificacion no cambiaba el
            // resultado. La mutacion siguio viva y lo dijo.
            //
            // Sin puntos no hay nada que discrimine: la comprobacion es «esta verificacion tiene al
            // menos una lectura», y sin filtrar por verificacion la lectura de presion satisface
            // tambien a la de temperatura. El reporte se cerraria afirmando que se verifico una
            // magnitud que nadie midio.
            EquipmentType tipo = tipoCon(List.of(
                    TypeVerification.of(PRESION, MMHG, VerificationMode.PATRON_EQUIPO_VARIABLE,
                            null, List.of()),
                    TypeVerification.of(TEMPERATURA, GRADOS, VerificationMode.PATRON_EQUIPO_VARIABLE,
                            null, List.of())));
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Barrido", null, ServiceResult.OPERATIVO);
            // Solo la de presion.
            reporte.recordVerification(List.of(VerificationReading.of(
                    tipo.verificacionesActivas().getFirst().id(), null, 1,
                    new BigDecimal("1"), new BigDecimal("1.1"))));
            reporte.pullEvents();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);

            assertThatIllegalStateException()
                    .isThrownBy(() -> service.finish(new FinishServiceReportCommand(reporte.getId())))
                    .withMessageContaining("Temperatura");
            assertThat(reporte.getEstado()).isEqualTo(ReportState.BORRADOR);
        }

        @Test
        @DisplayName("con dos magnitudes variables, una lectura de cada una si cierra")
        void cierraConUnaLecturaDeCadaMagnitudVariable() {
            // La contraparte: con patron y equipo variables basta una lectura POR VERIFICACION, no una
            // en total. Sin esta, la prueba de arriba se podria satisfacer exigiendo demasiado.
            EquipmentType tipo = tipoCon(List.of(
                    TypeVerification.of(PRESION, MMHG, VerificationMode.PATRON_EQUIPO_VARIABLE,
                            null, List.of()),
                    TypeVerification.of(TEMPERATURA, GRADOS, VerificationMode.PATRON_EQUIPO_VARIABLE,
                            null, List.of())));
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Barrido", null, ServiceResult.OPERATIVO);
            reporte.recordVerification(List.of(
                    VerificationReading.of(tipo.verificacionesActivas().getFirst().id(), null, 1,
                            new BigDecimal("1"), new BigDecimal("1.1")),
                    VerificationReading.of(tipo.verificacionesActivas().get(1).id(), null, 1,
                            new BigDecimal("37"), new BigDecimal("36.8"))));
            reporte.useReferenceStandard(tipo.verificacionesActivas().getFirst().id(), PATRON_VIGENTE.id());
            reporte.useReferenceStandard(tipo.verificacionesActivas().get(1).id(), PATRON_VIGENTE.id());
            reporte.pullEvents();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);
            estubarGuardado();

            assertThatCode(() -> service.finish(new FinishServiceReportCommand(reporte.getId())))
                    .doesNotThrowAnyException();
            assertThat(reporte.getEstado()).isEqualTo(ReportState.FINALIZADO);
        }

        @Test
        @DisplayName("un equipo fuera de servicio se cierra sin lecturas: no se le puede medir nada")
        void unEquipoFueraDeServicioSeCierraSinLecturas() {
            // No es una concesion: a un equipo que no enciende no se le toma una lectura, y exigirlas
            // obligaria a inventarselas para poder cerrar el reporte que dice que esta averiado.
            ServiceReport reporte = unReporte();
            reporte.fill("No enciende", "Fuente quemada", "Se retira para taller", null,
                    ServiceResult.FUERA_DE_SERVICIO);
            reporte.pullEvents();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(
                    unTipo(VerificationMode.PATRON_CONSTANTE, 3, List.of(unPunto("50"))));
            estubarGuardado();

            service.finish(new FinishServiceReportCommand(reporte.getId()));

            assertThat(reporte.getEstado()).isEqualTo(ReportState.FINALIZADO);
            assertThat(reporte.lecturasActivas()).isEmpty();
        }

        @Test
        @DisplayName("un tipo que no se verifica se cierra sin lecturas")
        void unTipoQueNoSeVerificaSeCierraSinLecturas() {
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Limpieza", null, ServiceResult.OPERATIVO);
            reporte.pullEvents();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(unTipo(null, null, List.of()));
            estubarGuardado();

            assertThatCode(() -> service.finish(new FinishServiceReportCommand(reporte.getId())))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("con patron y equipo variables basta una lectura, porque no hay cuantas fijadas")
        void conModalidadVariableBastaUnaLectura() {
            EquipmentType tipo = unTipo(VerificationMode.PATRON_EQUIPO_VARIABLE, null, List.of());
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Barrido completo", null, ServiceResult.OPERATIVO);
            reporte.recordVerification(List.of(VerificationReading.of(
                    laVerificacionDe(tipo).id(), null, 1,
                    new BigDecimal("1"), new BigDecimal("1.1"))));
            reporte.useReferenceStandard(laVerificacionDe(tipo).id(), PATRON_VIGENTE.id());
            reporte.pullEvents();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);
            estubarGuardado();

            assertThatCode(() -> service.finish(new FinishServiceReportCommand(reporte.getId())))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("con patron y equipo variables, ninguna lectura no basta")
        void conModalidadVariableNingunaLecturaNoBasta() {
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Barrido completo", null, ServiceResult.OPERATIVO);
            reporte.pullEvents();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(
                    unTipo(VerificationMode.PATRON_EQUIPO_VARIABLE, null, List.of()));

            assertThatIllegalStateException()
                    .isThrownBy(() -> service.finish(new FinishServiceReportCommand(reporte.getId())))
                    .withMessageContaining("ninguna lectura");
        }
    }

    @Nested
    @DisplayName("Al llenarlo y al retirarlo")
    class LoDemas {

        @Test
        @DisplayName("el avance por orden sin restriccion no pregunta a las ordenes")
        void avanceSinRestriccion() {
            List<WorkOrderReportProgress> avance = List.of(
                    new WorkOrderReportProgress(ORDEN, 2, 1),
                    new WorkOrderReportProgress(UUID.randomUUID(), 0, 1));
            when(serviceReportPersistencePort.progressByWorkOrder()).thenReturn(avance);

            assertThat(service.progressByWorkOrder(ReadScope.unrestricted())).isEqualTo(avance);
            verify(workOrderServicePort, never()).findAll(any());
        }

        @Test
        @DisplayName("con alcance de cliente, solo el avance de las ordenes que ese cliente ve")
        void avanceAcotado() {
            // Un usuario de cliente no puede saber cuantos reportes lleva la orden de otro: que una
            // orden existe y en que va ya es un dato del otro cliente.
            ReadScope alcance = ReadScope.ofClients(List.of(UUID.randomUUID()));
            WorkOrderReportProgress suya = new WorkOrderReportProgress(ORDEN, 2, 1);
            when(serviceReportPersistencePort.progressByWorkOrder()).thenReturn(List.of(
                    suya, new WorkOrderReportProgress(UUID.randomUUID(), 3, 0)));
            when(workOrderServicePort.findAll(alcance)).thenReturn(List.of(unaOrdenEnEjecucion()));

            assertThat(service.progressByWorkOrder(alcance)).containsExactly(suya);
        }

        @Test
        @DisplayName("poner un patron lo pone en su verificacion, por su identificador: la ficha no se copia (V51)")
        void poneElPatron() {
            EquipmentType tipo = unTipo(VerificationMode.PATRON_EQUIPO_VARIABLE, null, List.of());
            UUID verificacion = laVerificacionDe(tipo).id();
            ServiceReport reporte = unReporte();
            ReferenceStandardSheet ficha = new ReferenceStandardSheet("MS-F1", "BB-PAT-003", "1 mg a 2 kg",
                    "0,1 mg", LocalDate.of(2026, 4, 30), "Metrologia Andina");
            ReferenceStandard patron = ReferenceStandard.rehydrate(UUID.randomUUID(), "Juego de pesas",
                    "Kern", "KF1-2024-118", "CAL-2026-0412", LocalDate.of(2027, 4, 30), ficha, true);
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);
            estubarGuardado();
            when(referenceStandardServicePort.findById(patron.getId())).thenReturn(patron);

            service.useReferenceStandard(
                    new UseReferenceStandardCommand(reporte.getId(), verificacion, patron.getId()));

            assertThat(reporte.patronesActivos()).singleElement().satisfies(usado -> {
                assertThat(usado.idVerificacion()).isEqualTo(verificacion);
                assertThat(usado.idEquipoPatron()).isEqualTo(patron.getId());
            });
            verify(serviceReportPersistencePort).save(reporte);
        }

        @Test
        @DisplayName("en una verificacion que no es del tipo del equipo no se pone, y no se guarda nada")
        void verificacionAjena() {
            EquipmentType tipo = unTipo(VerificationMode.PATRON_EQUIPO_VARIABLE, null, List.of());
            ServiceReport reporte = unReporte();
            ReferenceStandard patron = ReferenceStandard.rehydrate(UUID.randomUUID(), "Termometro", null,
                    "T-9", "CAL-9", LocalDate.now().plusYears(1), ReferenceStandardSheet.VACIA, true);
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);
            // Si la guarda no estuviera, el patron se pondria: se deja disponible para que no sea eso.
            lenient().when(referenceStandardServicePort.findById(patron.getId())).thenReturn(patron);

            assertThatThrownBy(() -> service.useReferenceStandard(
                            new UseReferenceStandardCommand(reporte.getId(), UUID.randomUUID(), patron.getId())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no es una verificacion activa");
            assertThat(reporte.getPatrones()).isEmpty();
            verify(serviceReportPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("un patron retirado no se puede poner, y no se guarda nada")
        void patronRetirado() {
            EquipmentType tipo = unTipo(VerificationMode.PATRON_EQUIPO_VARIABLE, null, List.of());
            ServiceReport reporte = unReporte();
            ReferenceStandard patron = ReferenceStandard.rehydrate(UUID.randomUUID(), "Termometro", null,
                    "T-9", "CAL-9", LocalDate.now().plusYears(1), ReferenceStandardSheet.VACIA, false);
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);
            when(referenceStandardServicePort.findById(patron.getId())).thenReturn(patron);

            assertThatThrownBy(() -> service.useReferenceStandard(new UseReferenceStandardCommand(
                            reporte.getId(), laVerificacionDe(tipo).id(), patron.getId())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("retirado");
            assertThat(reporte.getPatrones()).isEmpty();
            verify(serviceReportPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("uno vencido si se puede poner: lo que no deja es cerrar")
        void patronVencidoSePone() {
            // Decision tomada a proposito: la vigencia se juzga el dia del cierre, que es el del servicio.
            EquipmentType tipo = unTipo(VerificationMode.PATRON_EQUIPO_VARIABLE, null, List.of());
            ServiceReport reporte = unReporte();
            ReferenceStandard patron = ReferenceStandard.rehydrate(UUID.randomUUID(), "Termometro", null,
                    "T-9", "CAL-9", LocalDate.now().minusDays(1), ReferenceStandardSheet.VACIA, true);
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);
            estubarGuardado();
            when(referenceStandardServicePort.findById(patron.getId())).thenReturn(patron);

            service.useReferenceStandard(new UseReferenceStandardCommand(
                    reporte.getId(), laVerificacionDe(tipo).id(), patron.getId()));

            assertThat(reporte.patronesActivos()).singleElement()
                    .satisfies(usado -> assertThat(usado.idEquipoPatron()).isEqualTo(patron.getId()));
        }

        @Test
        @DisplayName("quitar un patron lo retira y guarda, sin consultar el catalogo")
        void quitaElPatron() {
            UUID verificacion = UUID.randomUUID();
            ServiceReport reporte = unReporte();
            reporte.useReferenceStandard(verificacion, PATRON_VIGENTE.id());
            reporte.pullEvents();
            estubarReporte(reporte);
            estubarGuardado();

            service.removeReferenceStandard(
                    new RemoveReferenceStandardCommand(reporte.getId(), verificacion, PATRON_VIGENTE.id()));

            assertThat(reporte.patronesActivos()).isEmpty();
            verify(serviceReportPersistencePort).save(reporte);
            verify(referenceStandardServicePort, never()).findById(any());
        }

        @Test
        @DisplayName("al cerrar, una verificacion con lecturas y sin patron se nombra por su magnitud")
        void cierreSinPatronDiceLaMagnitud() {
            EquipmentType tipo = unTipo(VerificationMode.PATRON_EQUIPO_VARIABLE, null, List.of());
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Barrido", null, ServiceResult.OPERATIVO);
            reporte.recordVerification(List.of(VerificationReading.of(laVerificacionDe(tipo).id(), null, 1,
                    new BigDecimal("1"), new BigDecimal("1.1"))));
            reporte.pullEvents();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(tipo);
            lenient().when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted()))
                    .thenReturn(unaOrdenEnEjecucion());

            assertThatThrownBy(() -> service.finish(new FinishServiceReportCommand(reporte.getId())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("de Presión tiene lecturas");
            assertThat(reporte.getEstado()).isEqualTo(ReportState.BORRADOR);
            verify(serviceReportPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("registrar el ingreso pone a los accesorios el orden de la lista, sin consultar otros modulos")
        void registraElIngreso() {
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);
            estubarGuardado();

            service.recordIntake(new RecordIntakeCommand(reporte.getId(), IntakeState.NO_CONFORME, "Rayado",
                    IntakeState.CONFORME, null, List.of(
                            new RecordIntakeCommand.Accessory("Sonda", true, null),
                            new RecordIntakeCommand.Accessory("Cable", false, "Pelado"))));

            assertThat(reporte.getIngresoFisico()).isEqualTo(new IntakeCheck(IntakeState.NO_CONFORME, "Rayado"));
            assertThat(reporte.getIngresoOperativo()).isEqualTo(new IntakeCheck(IntakeState.CONFORME, null));
            assertThat(reporte.accesoriosActivos())
                    .extracting(ReportAccessory::orden, ReportAccessory::nombre, ReportAccessory::observacion)
                    .containsExactly(tuple(1, "Sonda", null), tuple(2, "Cable", "Pelado"));
            verify(serviceReportPersistencePort).save(reporte);
            verify(clientEquipmentServicePort, never()).findById(any(), any());
        }

        @Test
        @DisplayName("sin lista de accesorios se registra sin ninguno")
        void ingresoSinAccesorios() {
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);
            estubarGuardado();

            service.recordIntake(new RecordIntakeCommand(
                    reporte.getId(), IntakeState.CONFORME, null, IntakeState.CONFORME, null, null));

            assertThat(reporte.accesoriosActivos()).isEmpty();
            assertThat(reporte.getIngresoFisico()).isNotNull();
        }

        @Test
        @DisplayName("quien recibe se registra; con los tres campos en blanco se borra")
        void quienRecibe() {
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);
            estubarGuardado();

            service.recordHandover(new RecordHandoverCommand(reporte.getId(), "Ana Pérez", "52123456", "Jefe"));
            assertThat(reporte.getRecibe()).isEqualTo(new Handover("Ana Pérez", "52123456", "Jefe"));

            service.recordHandover(new RecordHandoverCommand(reporte.getId(), " ", null, ""));
            assertThat(reporte.getRecibe()).isNull();
        }

        @Test
        @DisplayName("una cedula sin nombre no se acepta: no dice quien recibio")
        void cedulaSinNombre() {
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);

            assertThatThrownBy(() -> service.recordHandover(
                            new RecordHandoverCommand(reporte.getId(), null, "52123456", null)))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(serviceReportPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("llenar no consulta el catalogo: son datos del propio reporte")
        void llenarNoConsultaElCatalogo() {
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);
            estubarGuardado();

            service.fill(new FillServiceReportCommand(reporte.getId(), "No enciende", null,
                    "Cambio de fuente", null, ServiceResult.OPERATIVO));

            assertThat(reporte.getFallaReportada()).isEqualTo("No enciende");
            verify(equipmentTypeServicePort, never()).findById(any());
        }

        @Test
        @DisplayName("el historial de un equipo comprueba antes que el equipo existe")
        void elHistorialCompruebaQueElEquipoExiste() {
            when(clientEquipmentServicePort.findById(EQUIPO, ReadScope.unrestricted())).thenReturn(
                    ClientEquipment.rehydrate(EQUIPO, "SN-1", MODELO, AREA, null, null, null, null, null, null, true));
            when(serviceReportPersistencePort.findByEquipment(EQUIPO)).thenReturn(List.of());

            assertThat(service.findByEquipment(EQUIPO, ReadScope.unrestricted())).isEmpty();
            verify(clientEquipmentServicePort).findById(EQUIPO, ReadScope.unrestricted());
        }
    }

    @Nested
    @DisplayName("El informe congelado (V37, 2026-10-17)")
    class ElInformeCongelado {

        private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 3};

        private ServiceReport cerradoCon(ReportDocument documento) {
            return ServiceReport.rehydrate(UUID.randomUUID(), ORDEN, EQUIPO, ReportState.FINALIZADO, null, null,
                    "Limpieza", null, null, null, List.of(), null, null, List.of(), null, ServiceResult.OPERATIVO,
                    java.time.LocalDateTime.of(2026, 10, 1, 9, 0), List.of(), List.of(), true, documento, false);
        }

        private ReportDocument conLogo(String huella) {
            ReportDocument base = UnDocumento.CUALQUIERA;
            return new ReportDocument(base.version(), base.orden(), base.cliente(), base.equipo(), List.of(),
                    new ReportDocument.Empresa("Ejemplo", null, null, null, null, null, null, null, huella), null,
                    null, List.of(), List.of());
        }

        @Test
        @DisplayName("uno cerrado se sirve congelado, con el logo de su huella, sin componer nada")
        void cerradoSeSirveCongelado() {
            DocumentImage logo = new DocumentImage(PNG, "image/png");
            ServiceReport reporte = cerradoCon(conLogo(logo.huella()));
            estubarReporte(reporte);
            when(documentImageStorePort.find(logo.huella())).thenReturn(Optional.of(logo));

            ReportDocumentView vista = service.document(reporte.getId(), ReadScope.unrestricted());

            assertThat(vista.congelado()).isTrue();
            assertThat(vista.documento()).isEqualTo(reporte.getDocumento());
            assertThat(vista.logo()).isEqualTo(logo);
            verify(reportDocumentComposer, never()).compose(any(), any());
        }

        @Test
        @DisplayName("un borrador se compone en vivo, y no guarda ningún logo: leer no escribe")
        void borradorEnVivo() {
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);
            EquipmentType tipo = unTipo(null, null, List.of());
            estubarCadenaDelCatalogo(tipo);
            DocumentImage logo = new DocumentImage(PNG, "image/png");
            when(reportDocumentComposer.compose(reporte, tipo))
                    .thenReturn(new ReportDocumentComposer.Composed(UnDocumento.CUALQUIERA, logo, null));

            ReportDocumentView vista = service.document(reporte.getId(), ReadScope.unrestricted());

            assertThat(vista.congelado()).isFalse();
            assertThat(vista.documento()).isEqualTo(UnDocumento.CUALQUIERA);
            assertThat(vista.logo()).isEqualTo(logo);
            verify(documentImageStorePort, never()).store(any());
        }

        @Test
        @DisplayName("cerrar congela lo compuesto en ese momento, y guarda su versión del logo")
        void cerrarGuardaElLogo() {
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Limpieza", null, ServiceResult.OPERATIVO);
            estubarReporte(reporte);
            EquipmentType tipo = unTipo(null, null, List.of());
            estubarCadenaDelCatalogo(tipo);
            estubarGuardado();
            DocumentImage logo = new DocumentImage(PNG, "image/png");
            when(reportDocumentComposer.compose(reporte, tipo))
                    .thenReturn(new ReportDocumentComposer.Composed(UnDocumento.CUALQUIERA, logo, null));

            ServiceReport cerrado = service.finish(new FinishServiceReportCommand(reporte.getId()));

            assertThat(cerrado.getDocumento()).isEqualTo(UnDocumento.CUALQUIERA);
            verify(documentImageStorePort).store(logo);
        }

        @Test
        @DisplayName("cerrar guarda también la versión de la firma del ingeniero, y el congelado la recupera por su huella")
        void laFirmaDelIngeniero() {
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Limpieza", null, ServiceResult.OPERATIVO);
            estubarReporte(reporte);
            EquipmentType tipo = unTipo(null, null, List.of());
            estubarCadenaDelCatalogo(tipo);
            estubarGuardado();
            DocumentImage firma = new DocumentImage(PNG, "image/png");
            ReportDocument base = UnDocumento.CUALQUIERA;
            ReportDocument conFirma = new ReportDocument(base.version(), base.orden(), base.cliente(), base.equipo(),
                    List.of(), null, "Ana Pérez", firma.huella(), List.of(), List.of());
            when(reportDocumentComposer.compose(reporte, tipo))
                    .thenReturn(new ReportDocumentComposer.Composed(conFirma, null, firma));

            ServiceReport cerrado = service.finish(new FinishServiceReportCommand(reporte.getId()));
            verify(documentImageStorePort).store(firma);

            when(documentImageStorePort.find(firma.huella())).thenReturn(Optional.of(firma));
            ReportDocumentView vista = service.document(cerrado.getId(), ReadScope.unrestricted());
            assertThat(vista.firmaIngeniero()).isEqualTo(firma);
            assertThat(vista.logo()).isNull();
        }

        @Test
        @DisplayName("al arrancar, los cerrados sin documento se congelan tarde y se guardan")
        void congelarTarde() {
            ServiceReport viejo = cerradoCon(null);
            when(serviceReportPersistencePort.findFinishedWithoutDocument()).thenReturn(List.of(viejo));
            EquipmentType tipo = unTipo(null, null, List.of());
            estubarCadenaDelCatalogo(tipo);
            when(reportDocumentComposer.compose(viejo, tipo))
                    .thenReturn(new ReportDocumentComposer.Composed(UnDocumento.CUALQUIERA, null, null));

            assertThat(service.freezeClosedWithoutDocument()).isEqualTo(1);

            assertThat(viejo.isDocumentoTardio()).isTrue();
            verify(serviceReportPersistencePort).save(viejo);
            verify(documentImageStorePort, never()).store(any());
        }
    }

    @Nested
    @DisplayName("La firma de quien recibe (V39, 2026-10-17)")
    class LaFirmaDeQuienRecibe {

        private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 5};
        private static final DocumentImage FIRMA = new DocumentImage(PNG, "image/png");
        private static final Handover ANA = new Handover("Ana Pérez", "52123456", "Jefe de enfermeria");

        private SignReceiptCommand firmar(ServiceReport reporte, ReceiptScope alcance) {
            return new SignReceiptCommand(
                    reporte.getId(), "Ana Pérez", "52123456", "Jefe de enfermeria", PNG, alcance);
        }

        private ServiceReport deOtroEquipo(UUID equipo) {
            ServiceReport reporte = ServiceReport.open(ORDEN, equipo);
            reporte.pullEvents();
            return reporte;
        }

        private ClientEquipment unidad(UUID id, UUID area) {
            return ClientEquipment.rehydrate(id, "SN-" + id, MODELO, area, null, null, null, null, null, null, true);
        }

        private ServiceReport cerrado() {
            return ServiceReport.rehydrate(UUID.randomUUID(), ORDEN, EQUIPO, ReportState.FINALIZADO, null, null,
                    "Limpieza", null, null, null, List.of(), null, null, List.of(), null, ServiceResult.OPERATIVO,
                    java.time.LocalDateTime.of(2026, 10, 1, 9, 0), List.of(), List.of(), true,
                    UnDocumento.CUALQUIERA, false);
        }

        @Test
        @DisplayName("firmar guarda la imagen una vez, apunta a ella por su huella y publica el hecho")
        void firmar() {
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);
            estubarGuardado();

            List<ServiceReport> firmados = service.signReceipt(firmar(reporte, ReceiptScope.REPORTE));

            assertThat(firmados).containsExactly(reporte);
            assertThat(reporte.getRecibe()).isEqualTo(ANA);
            assertThat(reporte.getFirmaRecibe().huella()).isEqualTo(FIRMA.huella());
            verify(documentImageStorePort).store(FIRMA);
            verify(eventDispatcherPort).dispatch(any(ServiceReportReceiptSignedEvent.class));
            // Solo este: no se mira la orden ni el área.
            verify(serviceReportPersistencePort, never()).findByWorkOrder(any());
            verify(clientEquipmentServicePort, never()).findByServiceArea(any(), any());
        }

        @Test
        @DisplayName("RF-21: a la orden, firma los de la orden sin firma, abiertos o cerrados, y no los demás")
        void aplicarALaOrden() {
            ServiceReport pedido = unReporte();
            ServiceReport abierto = unReporte();
            ServiceReport cerradoSinFirma = cerrado();
            ServiceReport yaFirmado = unReporte();
            yaFirmado.signReceipt(new Handover("Luis Gómez", null, null), "b".repeat(64),
                    java.time.LocalDateTime.of(2026, 10, 2, 8, 0));
            ServiceReport retirado = unReporte();
            retirado.deactivate();
            estubarReporte(pedido);
            estubarGuardado();
            when(serviceReportPersistencePort.findByWorkOrder(ORDEN))
                    .thenReturn(List.of(abierto, pedido, yaFirmado, cerradoSinFirma, retirado));

            List<ServiceReport> firmados = service.signReceipt(firmar(pedido, ReceiptScope.ORDEN));

            assertThat(firmados).containsExactly(pedido, abierto, cerradoSinFirma);
            assertThat(List.of(pedido, abierto, cerradoSinFirma)).allSatisfy(firmado -> {
                assertThat(firmado.getRecibe()).isEqualTo(ANA);
                assertThat(firmado.getFirmaRecibe().huella()).isEqualTo(FIRMA.huella());
                assertThat(firmado.getFirmaRecibe().firmada()).isEqualTo(pedido.getFirmaRecibe().firmada());
            });
            assertThat(yaFirmado.getFirmaRecibe().huella()).isEqualTo("b".repeat(64));
            assertThat(yaFirmado.getRecibe().nombre()).isEqualTo("Luis Gómez");
            assertThat(retirado.getFirmaRecibe()).isNull();
            // Una imagen para los tres.
            verify(documentImageStorePort).store(FIRMA);
            verify(serviceReportPersistencePort, never()).save(yaFirmado);
            verify(serviceReportPersistencePort, never()).save(retirado);
            // Toda la orden: el área no se consulta.
            verify(clientEquipmentServicePort, never()).findByServiceArea(any(), any());
        }

        @Test
        @DisplayName("RF-21: al área, firma los de la orden cuyo equipo está en el área del pedido, y no los de otra")
        void aplicarAlArea() {
            UUID vecino = UUID.randomUUID();
            UUID deOtraArea = UUID.randomUUID();
            ServiceReport pedido = unReporte();
            ServiceReport delArea = deOtroEquipo(vecino);
            ServiceReport ajeno = deOtroEquipo(deOtraArea);
            ServiceReport yaFirmado = deOtroEquipo(vecino);
            yaFirmado.signReceipt(new Handover("Luis Gómez", null, null), "b".repeat(64),
                    java.time.LocalDateTime.of(2026, 10, 2, 8, 0));
            estubarReporte(pedido);
            estubarGuardado();
            when(clientEquipmentServicePort.findById(EQUIPO, ReadScope.unrestricted()))
                    .thenReturn(unidad(EQUIPO, AREA));
            when(clientEquipmentServicePort.findByServiceArea(AREA, ReadScope.unrestricted()))
                    .thenReturn(List.of(unidad(EQUIPO, AREA), unidad(vecino, AREA)));
            when(serviceReportPersistencePort.findByWorkOrder(ORDEN))
                    .thenReturn(List.of(ajeno, pedido, delArea, yaFirmado));

            List<ServiceReport> firmados = service.signReceipt(firmar(pedido, ReceiptScope.AREA));

            assertThat(firmados).containsExactly(pedido, delArea);
            assertThat(delArea.getRecibe()).isEqualTo(ANA);
            assertThat(ajeno.getFirmaRecibe()).isNull();
            assertThat(yaFirmado.getRecibe().nombre()).isEqualTo("Luis Gómez");
            verify(documentImageStorePort).store(FIRMA);
            verify(serviceReportPersistencePort, never()).save(ajeno);
        }

        @Test
        @DisplayName("sin alcance, el comando firma solo el reporte pedido")
        void sinAlcance() {
            assertThat(new SignReceiptCommand(UUID.randomUUID(), "Ana", null, null, PNG, null).alcance())
                    .isEqualTo(ReceiptScope.REPORTE);
        }

        @Test
        @DisplayName("si el pedido ya está firmado falla, aunque se aplique a la orden, y no guarda nada")
        void pedidoYaFirmado() {
            ServiceReport pedido = unReporte();
            pedido.signReceipt(ANA, "b".repeat(64), java.time.LocalDateTime.of(2026, 10, 2, 8, 0));
            ServiceReport otro = unReporte();
            estubarReporte(pedido);
            lenient().when(serviceReportPersistencePort.findByWorkOrder(ORDEN)).thenReturn(List.of(pedido, otro));

            assertThatThrownBy(() -> service.signReceipt(firmar(pedido, ReceiptScope.ORDEN)))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(otro.getFirmaRecibe()).isNull();
            verify(documentImageStorePort, never()).store(any());
            verify(serviceReportPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("una imagen que no es PNG se rechaza antes de guardar nada")
        void noEsPng() {
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);

            assertThatThrownBy(() -> service.signReceipt(new SignReceiptCommand(reporte.getId(), "Ana Pérez", null,
                            null, "<svg/>".getBytes(java.nio.charset.StandardCharsets.UTF_8), ReceiptScope.REPORTE)))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(reporte.getFirmaRecibe()).isNull();
            verify(documentImageStorePort, never()).store(any());
            verify(serviceReportPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("sin nombre no se firma")
        void sinNombre() {
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);

            assertThatThrownBy(() -> service.signReceipt(
                            new SignReceiptCommand(reporte.getId(), " ", null, null, PNG, ReceiptScope.REPORTE)))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(serviceReportPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("el informe congelado trae la firma de quien recibe, leída por su huella")
        void elInformeLaTrae() {
            ServiceReport reporte = cerrado();
            reporte.signReceipt(ANA, FIRMA.huella(), java.time.LocalDateTime.of(2026, 10, 3, 10, 0));
            estubarReporte(reporte);
            when(documentImageStorePort.find(FIRMA.huella())).thenReturn(Optional.of(FIRMA));

            ReportDocumentView vista = service.document(reporte.getId(), ReadScope.unrestricted());

            assertThat(vista.firmaRecibe()).isEqualTo(FIRMA);
            assertThat(vista.congelado()).isTrue();
        }

        @Test
        @DisplayName("el borrador, compuesto en vivo, también la trae")
        void elBorradorLaTrae() {
            ServiceReport reporte = unReporte();
            reporte.signReceipt(ANA, FIRMA.huella(), java.time.LocalDateTime.of(2026, 10, 3, 10, 0));
            estubarReporte(reporte);
            EquipmentType tipo = unTipo(null, null, List.of());
            estubarCadenaDelCatalogo(tipo);
            when(reportDocumentComposer.compose(reporte, tipo))
                    .thenReturn(new ReportDocumentComposer.Composed(UnDocumento.CUALQUIERA, null, null));
            when(documentImageStorePort.find(FIRMA.huella())).thenReturn(Optional.of(FIRMA));

            ReportDocumentView vista = service.document(reporte.getId(), ReadScope.unrestricted());

            assertThat(vista.firmaRecibe()).isEqualTo(FIRMA);
            assertThat(vista.congelado()).isFalse();
        }

        @Test
        @DisplayName("sin firmar, el informe no trae firma de quien recibe")
        void sinFirmar() {
            ServiceReport reporte = cerrado();
            estubarReporte(reporte);

            assertThat(service.document(reporte.getId(), ReadScope.unrestricted()).firmaRecibe()).isNull();
        }
    }

    @Nested
    @DisplayName("El informe en PDF (RF-17, 2026-10-17)")
    class ElPdf {

        @Test
        @DisplayName("un cerrado se convierte desde su copia congelada, con su firma, y se llama por su número")
        void cerrado() {
            DocumentImage firma = new DocumentImage(
                    new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 8}, "image/png");
            ServiceReport reporte = ServiceReport.rehydrate(UUID.randomUUID(), ORDEN, EQUIPO, ReportState.FINALIZADO,
                    null, null, "Limpieza", null, null, null, List.of(), null, null, List.of(), null,
                    ServiceResult.OPERATIVO, java.time.LocalDateTime.of(2026, 10, 1, 9, 0), List.of(), List.of(), true,
                    UnDocumento.CUALQUIERA, false);
            reporte.signReceipt(new Handover("Ana Pérez", null, null), firma.huella(),
                    java.time.LocalDateTime.of(2026, 10, 2, 9, 0));
            estubarReporte(reporte);
            when(documentImageStorePort.find(firma.huella())).thenReturn(Optional.of(firma));
            byte[] bytes = "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            when(reportPdfPort.render(any())).thenReturn(bytes);

            ReportPdf pdf = service.pdf(reporte.getId(), ReadScope.unrestricted());

            assertThat(pdf.nombre()).isEqualTo("informe-" + reporte.numero() + ".pdf");
            assertThat(pdf.contenido()).isEqualTo(bytes);
            ArgumentCaptor<ReportSheet> hoja = ArgumentCaptor.forClass(ReportSheet.class);
            verify(reportPdfPort).render(hoja.capture());
            assertThat(hoja.getValue().borrador()).isFalse();
            assertThat(hoja.getValue().numero()).isEqualTo(reporte.numero());
            assertThat(hoja.getValue().firmas().firmaRecibe()).isEqualTo(firma.dataUri());
            // Congelado: no se compone nada en vivo.
            verify(reportDocumentComposer, never()).compose(any(), any());
        }

        @Test
        @DisplayName("un borrador se compone en vivo y sale marcado como borrador")
        void borrador() {
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);
            EquipmentType tipo = unTipo(null, null, List.of());
            estubarCadenaDelCatalogo(tipo);
            when(reportDocumentComposer.compose(reporte, tipo))
                    .thenReturn(new ReportDocumentComposer.Composed(UnDocumento.CUALQUIERA, null, null));
            when(reportPdfPort.render(any())).thenReturn(new byte[] {1});

            service.pdf(reporte.getId(), ReadScope.unrestricted());

            ArgumentCaptor<ReportSheet> hoja = ArgumentCaptor.forClass(ReportSheet.class);
            verify(reportPdfPort).render(hoja.capture());
            assertThat(hoja.getValue().borrador()).isTrue();
        }

        @Test
        @DisplayName("con alcance acotado, el reporte de otro cliente no existe y no se convierte")
        void otroCliente() {
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);
            WorkOrder orden = unaOrdenEnEjecucion();
            when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted())).thenReturn(orden);

            assertThatThrownBy(() -> service.pdf(reporte.getId(), ReadScope.ofClients(List.of(UUID.randomUUID()))))
                    .isInstanceOf(ServiceReportNotFoundException.class);
            verify(reportPdfPort, never()).render(any());
        }
    }

    @Nested
    @DisplayName("Los informes de una orden en un ZIP (2026-10-31)")
    class LosInformesDeUnaOrden {

        private ServiceReport cerradoDe(UUID equipo) {
            return cerrado(UUID.randomUUID(), equipo);
        }

        private ServiceReport cerrado(UUID id, UUID equipo) {
            return ServiceReport.rehydrate(id, ORDEN, equipo, ReportState.FINALIZADO, null, null,
                    "Limpieza", null, null, null, List.of(), null, null, List.of(), null, ServiceResult.OPERATIVO,
                    java.time.LocalDateTime.of(2026, 10, 1, 9, 0), List.of(), List.of(), true,
                    UnDocumento.CUALQUIERA, false);
        }

        @Test
        @DisplayName("con todos cerrados, un PDF por reporte activo, por número, y el ZIP con el de la orden")
        void todosCerrados() {
            // Identificadores fijos, y llegan al revés: con unos al azar, la mitad de las veces ya vendrían en orden.
            ServiceReport uno = cerrado(UUID.fromString("aaaaaa00-0000-0000-0000-000000000000"), UUID.randomUUID());
            ServiceReport otro = cerrado(UUID.fromString("bbbbbb00-0000-0000-0000-000000000000"), UUID.randomUUID());
            ServiceReport retirado = unReporte();
            retirado.deactivate();
            when(serviceReportPersistencePort.findByWorkOrder(ORDEN)).thenReturn(List.of(otro, retirado, uno));
            when(reportPdfPort.render(any())).thenReturn(new byte[] {1});
            estubarResumen();

            ReportArchive archivo = service.pdfsOfWorkOrder(ORDEN, ReadScope.unrestricted());

            String numero = unaOrdenEnEjecucion().numero();
            assertThat(archivo.nombre()).isEqualTo("informes-orden-" + numero + ".zip");
            // El resumen de la orden va delante (2026-10-31).
            assertThat(archivo.informes()).extracting(ReportPdf::nombre)
                    .containsExactly("orden-" + numero + ".pdf", "informe-AAAAAA.pdf", "informe-BBBBBB.pdf");
            assertThat(archivo.informes().get(0).contenido()).isEqualTo(RESUMEN);
            // Un retirado borrador no impide descargar ni entra: no es de la orden.
            verify(reportPdfPort, org.mockito.Mockito.times(2)).render(any());
        }

        @Test
        @DisplayName("con un reporte en borrador no se descarga, y no se convierte ninguno")
        void unoSinCerrar() {
            when(serviceReportPersistencePort.findByWorkOrder(ORDEN))
                    .thenReturn(List.of(cerradoDe(UUID.randomUUID()), unReporte()));

            assertThatThrownBy(() -> service.pdfsOfWorkOrder(ORDEN, ReadScope.unrestricted()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("1 reporte(s) sin cerrar");
            assertThatThrownBy(() -> service.workOrderSummary(ORDEN, ReadScope.unrestricted()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("1 reporte(s) sin cerrar");
            verify(reportPdfPort, never()).render(any());
            verify(workOrderSummaryPdfPort, never()).render(any());
        }

        @Test
        @DisplayName("una orden sin reportes activos no tiene nada que descargar")
        void sinReportes() {
            ServiceReport retirado = cerradoDe(UUID.randomUUID());
            retirado.deactivate();
            when(serviceReportPersistencePort.findByWorkOrder(ORDEN)).thenReturn(List.of(retirado));

            assertThatThrownBy(() -> service.pdfsOfWorkOrder(ORDEN, ReadScope.unrestricted()))
                    .isInstanceOf(IllegalStateException.class);
            verify(reportPdfPort, never()).render(any());
        }

        private static final byte[] RESUMEN = "%PDF-resumen".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        /** El documento de la orden y su conversión: lo que pide el resumen. */
        private void estubarResumen() {
            when(workOrderServicePort.documentOf(any())).thenReturn(
                    com.malphasos.malphasos.report.application.sheet.UnaOrdenAtendida.documento(null));
            when(workOrderSummaryPdfPort.render(any())).thenReturn(RESUMEN);
        }

        @Test
        @DisplayName("el resumen de la orden sale de su documento y de sus reportes, con la empresa de hoy")
        void elResumen() {
            UUID equipo = UUID.randomUUID();
            ServiceReport uno = cerrado(UUID.fromString("aaaaaa00-0000-0000-0000-000000000000"), equipo);
            when(serviceReportPersistencePort.findByWorkOrder(ORDEN)).thenReturn(List.of(uno));
            estubarResumen();
            ServiceCompany empresa = org.mockito.Mockito.mock(ServiceCompany.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            when(empresa.getDatos().nombre()).thenReturn("Empresa de Prueba");
            when(empresa.logo()).thenReturn(Optional.empty());
            when(serviceCompanyServicePort.find()).thenReturn(Optional.of(empresa));

            ReportPdf pdf = service.workOrderSummary(ORDEN, ReadScope.unrestricted());

            assertThat(pdf.nombre()).isEqualTo("orden-" + unaOrdenEnEjecucion().numero() + ".pdf");
            assertThat(pdf.contenido()).isEqualTo(RESUMEN);
            ArgumentCaptor<WorkOrderSummarySheet> hoja = ArgumentCaptor.forClass(WorkOrderSummarySheet.class);
            verify(workOrderSummaryPdfPort).render(hoja.capture());
            assertThat(hoja.getValue().empresa()).isEqualTo("Empresa de Prueba");
            assertThat(hoja.getValue().idOrden()).isEqualTo(ORDEN);
            assertThat(hoja.getValue().totales().equipos()).isEqualTo(1);
            // El documento es el que da la orden, congelado o de hoy: este módulo no lo compone.
            verify(workOrderServicePort).documentOf(unaOrdenEnEjecucion());
            verify(reportPdfPort, never()).render(any());
        }

        @Test
        @DisplayName("el resumen de una orden ajena no existe, y no se convierte")
        void resumenAjeno() {
            ReadScope soloOtro = ReadScope.ofClients(List.of(UUID.randomUUID()));
            when(workOrderServicePort.findById(ORDEN, soloOtro)).thenThrow(new WorkOrderNotFoundException(ORDEN));
            lenient().when(serviceReportPersistencePort.findByWorkOrder(ORDEN))
                    .thenReturn(List.of(cerradoDe(UUID.randomUUID())));
            lenient().when(workOrderSummaryPdfPort.render(any())).thenReturn(RESUMEN);

            assertThatThrownBy(() -> service.workOrderSummary(ORDEN, soloOtro))
                    .isInstanceOf(WorkOrderNotFoundException.class);
            verify(workOrderSummaryPdfPort, never()).render(any());
        }

        @Test
        @DisplayName("la orden de otro cliente no existe, y no se lee ni se convierte nada")
        void ordenAjena() {
            ReadScope soloOtro = ReadScope.ofClients(List.of(UUID.randomUUID()));
            when(workOrderServicePort.findById(ORDEN, soloOtro)).thenThrow(new WorkOrderNotFoundException(ORDEN));
            // Si la guarda no estuviera, esto daría reportes que convertir.
            lenient().when(serviceReportPersistencePort.findByWorkOrder(ORDEN))
                    .thenReturn(List.of(cerradoDe(UUID.randomUUID())));
            lenient().when(reportPdfPort.render(any())).thenReturn(new byte[] {1});

            assertThatThrownBy(() -> service.pdfsOfWorkOrder(ORDEN, soloOtro))
                    .isInstanceOf(WorkOrderNotFoundException.class);
            verify(reportPdfPort, never()).render(any());
        }
    }
}
