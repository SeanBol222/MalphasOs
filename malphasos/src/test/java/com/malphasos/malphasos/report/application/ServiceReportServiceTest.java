package com.malphasos.malphasos.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.malphasos.malphasos.report.application.ports.output.ServiceReportPersistencePort;
import com.malphasos.malphasos.report.application.services.serviceReport.ServiceReportService;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FillServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FinishServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.OpenServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordVerificationCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.VerificationReadingCommand;
import com.malphasos.malphasos.report.domain.exception.ServiceReportNotFoundException;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationReading;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.domain.workOrder.ExecutionState;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.SelectedEquipment;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @InjectMocks private ServiceReportService service;

    // ---- Dobles de los otros modulos ----

    private static WorkOrder unaOrden(ExecutionState estado, boolean activa, UUID... equipos) {
        return WorkOrder.rehydrate(
                ORDEN,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                Periodicity.ANUAL,
                ServiceType.PREVENTIVO,
                estado,
                UUID.randomUUID(),
                java.util.Arrays.stream(equipos)
                        .map(equipo -> SelectedEquipment.of(equipo, AREA))
                        .toList(),
                activa);
    }

    /** La orden en la que todo se puede reportar: empezada, activa y con el equipo dentro. */
    private static WorkOrder unaOrdenEnEjecucion() {
        return unaOrden(ExecutionState.EN_EJECUCION, true, EQUIPO);
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

    private static VerificationPoint unPunto(String valor) {
        return VerificationPoint.of(new BigDecimal(valor));
    }

    private static ServiceReport unReporte() {
        ServiceReport reporte = ServiceReport.open(ORDEN, EQUIPO);
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
                ClientEquipment.rehydrate(EQUIPO, "SN-1", MODELO, AREA, null, null, null, true));
        when(modelServicePort.findById(MODELO)).thenReturn(
                Model.rehydrate(MODELO, "IdeaPad 3", null, UUID.randomUUID(), EQUIPO_CATALOGO, true));
        when(equipmentServicePort.findById(EQUIPO_CATALOGO)).thenReturn(
                Equipment.rehydrate(EQUIPO_CATALOGO, TIPO, UUID.randomUUID(), true));
        when(equipmentTypeServicePort.findById(TIPO)).thenReturn(tipo);
    }

    private void estubarGuardado() {
        when(serviceReportPersistencePort.save(any(ServiceReport.class)))
                .thenAnswer(llamada -> llamada.getArgument(0));
    }

    private void estubarReporte(ServiceReport reporte) {
        when(serviceReportPersistencePort.findById(reporte.getId())).thenReturn(Optional.of(reporte));
    }

    @Nested
    @DisplayName("Al abrir un reporte")
    class AlAbrir {

        @Test
        @DisplayName("lo abre si la orden esta en ejecucion y el equipo esta en su alcance")
        void abreElReporte() {
            when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted())).thenReturn(unaOrdenEnEjecucion());
            when(serviceReportPersistencePort.findActiveByWorkOrderAndEquipment(ORDEN, EQUIPO))
                    .thenReturn(Optional.empty());
            estubarGuardado();

            ServiceReport reporte = service.open(new OpenServiceReportCommand(ORDEN, EQUIPO));

            assertThat(reporte.getEstado()).isEqualTo(ReportState.BORRADOR);
            assertThat(reporte.getIdOrdenTrabajo()).isEqualTo(ORDEN);
            verify(eventDispatcherPort).dispatch(any());
        }

        @Test
        @DisplayName("una orden todavia creada no admite reportes: no se ha hecho nada")
        void rechazaUnaOrdenSinEmpezar() {
            when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted()))
                    .thenReturn(unaOrden(ExecutionState.CREADA, true, EQUIPO));

            assertThatIllegalStateException()
                    .isThrownBy(() -> service.open(new OpenServiceReportCommand(ORDEN, EQUIPO)))
                    .withMessageContaining("no ha empezado");
            verify(serviceReportPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("una orden ya ejecutada si admite reportes: aqui se registra despues de ir")
        void admiteUnaOrdenYaEjecutada() {
            when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted()))
                    .thenReturn(unaOrden(ExecutionState.EJECUTADA, true, EQUIPO));
            when(serviceReportPersistencePort.findActiveByWorkOrderAndEquipment(ORDEN, EQUIPO))
                    .thenReturn(Optional.empty());
            estubarGuardado();

            assertThatCode(() -> service.open(new OpenServiceReportCommand(ORDEN, EQUIPO)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("una orden cancelada no admite reportes")
        void rechazaUnaOrdenCancelada() {
            when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted()))
                    .thenReturn(unaOrden(ExecutionState.EN_EJECUCION, false, EQUIPO));

            assertThatIllegalStateException()
                    .isThrownBy(() -> service.open(new OpenServiceReportCommand(ORDEN, EQUIPO)))
                    .withMessageContaining("cancelada");
        }

        @Test
        @DisplayName("un equipo fuera del alcance vivo se rechaza, aunque la base lo admitiera")
        void rechazaUnEquipoFueraDelAlcance() {
            // La foranea compuesta del esquema solo ve que la fila del puente existe; que siga activa
            // no lo puede comprobar, y un equipo retirado del alcance dejo de estar en la orden.
            when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted()))
                    .thenReturn(unaOrden(ExecutionState.EN_EJECUCION, true, UUID.randomUUID()));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> service.open(new OpenServiceReportCommand(ORDEN, EQUIPO)))
                    .withMessageContaining("no esta en el alcance");
            verify(serviceReportPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("un equipo que ya tiene reporte vivo no abre otro: lo dice antes que el indice")
        void rechazaUnSegundoReporte() {
            when(workOrderServicePort.findById(ORDEN, ReadScope.unrestricted())).thenReturn(unaOrdenEnEjecucion());
            when(serviceReportPersistencePort.findActiveByWorkOrderAndEquipment(ORDEN, EQUIPO))
                    .thenReturn(Optional.of(unReporte()));

            assertThatIllegalStateException()
                    .isThrownBy(() -> service.open(new OpenServiceReportCommand(ORDEN, EQUIPO)))
                    .withMessageContaining("ya tiene el reporte");
        }
    }

    @Nested
    @DisplayName("Al registrar la verificacion")
    class AlVerificar {

        private static final UUID PUNTO_AJENO = UUID.randomUUID();

        @Test
        @DisplayName("acepta las lecturas de un punto y les pone la unidad de la verificacion")
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
            // La unidad sale de la verificacion, y ya no hay forma de enviarla desde fuera.
            assertThat(reporte.lecturasActivas()).extracting(VerificationReading::unidad)
                    .containsOnly("mmHg");
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

            assertThat(reporte.lecturasActivas()).extracting(VerificationReading::unidad)
                    .containsExactlyInAnyOrder("mmHg", "°C");
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
            // Antes la unidad tenia que venir de fuera en este caso, y eso permitia inventarsela.
            assertThat(reporte.lecturasActivas().getFirst().unidad()).isEqualTo("mmHg");
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
                            new BigDecimal("50"), new BigDecimal("50.2"), "mmHg"),
                    VerificationReading.of(verificacion, punto.id(), 2,
                            new BigDecimal("50"), new BigDecimal("49.9"), "mmHg")));
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
                            new BigDecimal("50"), new BigDecimal("50.2"), "mmHg")));
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
                            new BigDecimal("50"), new BigDecimal("50.2"), "mmHg")));
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
                    new BigDecimal("50"), new BigDecimal("50.2"), "mmHg")));
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
                    new BigDecimal("1"), new BigDecimal("1.1"), "mmHg")));
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
                            new BigDecimal("1"), new BigDecimal("1.1"), "mmHg"),
                    VerificationReading.of(tipo.verificacionesActivas().get(1).id(), null, 1,
                            new BigDecimal("37"), new BigDecimal("36.8"), "°C")));
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
                    new BigDecimal("1"), new BigDecimal("1.1"), "mmHg")));
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
                    ClientEquipment.rehydrate(EQUIPO, "SN-1", MODELO, AREA, null, null, null, true));
            when(serviceReportPersistencePort.findByEquipment(EQUIPO)).thenReturn(List.of());

            assertThat(service.findByEquipment(EQUIPO, ReadScope.unrestricted())).isEmpty();
            verify(clientEquipmentServicePort).findById(EQUIPO, ReadScope.unrestricted());
        }
    }
}
