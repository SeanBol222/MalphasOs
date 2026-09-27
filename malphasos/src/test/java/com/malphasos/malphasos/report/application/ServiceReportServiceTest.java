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
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
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

    private static EquipmentType unTipo(
            VerificationMode modalidad, Integer cantidadDatos, List<VerificationPoint> puntos) {

        return EquipmentType.rehydrate(TIPO, "Tensiometro", "Definicion", "Cuidados", "Electronica",
                null, null, modalidad, cantidadDatos, puntos, 150_000L, true);
    }

    private static VerificationPoint unPunto(String valor) {
        return VerificationPoint.of(new BigDecimal(valor), "mmHg");
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
        when(clientEquipmentServicePort.findById(EQUIPO)).thenReturn(
                ClientEquipment.rehydrate(EQUIPO, "SN-1", MODELO, AREA, null, null, null, true));
        when(modelServicePort.findById(MODELO)).thenReturn(
                Model.rehydrate(MODELO, null, UUID.randomUUID(), EQUIPO_CATALOGO, true));
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
            when(workOrderServicePort.findById(ORDEN)).thenReturn(unaOrdenEnEjecucion());
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
            when(workOrderServicePort.findById(ORDEN))
                    .thenReturn(unaOrden(ExecutionState.CREADA, true, EQUIPO));

            assertThatIllegalStateException()
                    .isThrownBy(() -> service.open(new OpenServiceReportCommand(ORDEN, EQUIPO)))
                    .withMessageContaining("no ha empezado");
            verify(serviceReportPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("una orden ya ejecutada si admite reportes: aqui se registra despues de ir")
        void admiteUnaOrdenYaEjecutada() {
            when(workOrderServicePort.findById(ORDEN))
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
            when(workOrderServicePort.findById(ORDEN))
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
            when(workOrderServicePort.findById(ORDEN))
                    .thenReturn(unaOrden(ExecutionState.EN_EJECUCION, true, UUID.randomUUID()));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> service.open(new OpenServiceReportCommand(ORDEN, EQUIPO)))
                    .withMessageContaining("no esta en el alcance");
            verify(serviceReportPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("un equipo que ya tiene reporte vivo no abre otro: lo dice antes que el indice")
        void rechazaUnSegundoReporte() {
            when(workOrderServicePort.findById(ORDEN)).thenReturn(unaOrdenEnEjecucion());
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
        @DisplayName("acepta las lecturas de un punto del tipo y les pone la unidad del punto")
        void aceptaLasLecturasDelPunto() {
            ServiceReport reporte = unReporte();
            VerificationPoint punto = unPunto("50");
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(
                    unTipo(VerificationMode.PATRON_CONSTANTE, 2, List.of(punto)));
            estubarGuardado();

            service.recordVerification(new RecordVerificationCommand(reporte.getId(), List.of(
                    new VerificationReadingCommand(punto.id(), 1, new BigDecimal("50"), new BigDecimal("50.2"), "inventada"),
                    new VerificationReadingCommand(punto.id(), 2, new BigDecimal("50"), new BigDecimal("49.8"), null))));

            assertThat(reporte.lecturasActivas()).hasSize(2);
            // La unidad sale del punto, no de lo que venga escrito en el comando.
            assertThat(reporte.lecturasActivas()).extracting(VerificationReading::unidad)
                    .containsOnly("mmHg");
        }

        @Test
        @DisplayName("un tipo que no se verifica no admite lecturas")
        void rechazaUnTipoQueNoSeVerifica() {
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(unTipo(null, null, List.of()));

            assertThatIllegalArgumentException().isThrownBy(() -> service.recordVerification(
                            new RecordVerificationCommand(reporte.getId(), List.of(
                                    new VerificationReadingCommand(null, 1, BigDecimal.ONE, BigDecimal.TWO, "mA")))))
                    .withMessageContaining("no se verifica");
        }

        @Test
        @DisplayName("un punto que no es de ese tipo se rechaza: el esquema no puede verlo")
        void rechazaUnPuntoAjeno() {
            // La foranea de dato_verificacion solo comprueba que el punto exista, no que sea del tipo
            // del equipo reportado: son cuatro saltos desde equipo_cliente hasta tipo_equipo.
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(
                    unTipo(VerificationMode.PATRON_CONSTANTE, 1, List.of(unPunto("50"))));

            assertThatIllegalArgumentException().isThrownBy(() -> service.recordVerification(
                            new RecordVerificationCommand(reporte.getId(), List.of(
                                    new VerificationReadingCommand(PUNTO_AJENO, 1, BigDecimal.ONE, BigDecimal.TWO, null)))))
                    .withMessageContaining("no es un punto activo");
        }

        @Test
        @DisplayName("un punto retirado tampoco vale para una lectura nueva")
        void rechazaUnPuntoRetirado() {
            ServiceReport reporte = unReporte();
            VerificationPoint retirado = unPunto("50").deactivated();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(
                    unTipo(VerificationMode.PATRON_CONSTANTE, 1, List.of(retirado)));

            assertThatIllegalArgumentException().isThrownBy(() -> service.recordVerification(
                    new RecordVerificationCommand(reporte.getId(), List.of(
                            new VerificationReadingCommand(retirado.id(), 1, BigDecimal.ONE, BigDecimal.TWO, null)))));
        }

        @Test
        @DisplayName("con modalidad constante toda lectura declara su punto")
        void exigeElPuntoConModalidadConstante() {
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(
                    unTipo(VerificationMode.EQUIPO_CONSTANTE, 1, List.of(unPunto("50"))));

            assertThatIllegalArgumentException().isThrownBy(() -> service.recordVerification(
                            new RecordVerificationCommand(reporte.getId(), List.of(
                                    new VerificationReadingCommand(null, 1, BigDecimal.ONE, BigDecimal.TWO, "mmHg")))))
                    .withMessageContaining("declara en cual se tomo");
        }

        @Test
        @DisplayName("mas lecturas por punto de las que el tipo declara se rechazan")
        void rechazaMasLecturasDeLasDeclaradas() {
            ServiceReport reporte = unReporte();
            VerificationPoint punto = unPunto("50");
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(
                    unTipo(VerificationMode.PATRON_CONSTANTE, 2, List.of(punto)));

            assertThatIllegalArgumentException().isThrownBy(() -> service.recordVerification(
                            new RecordVerificationCommand(reporte.getId(), List.of(
                                    new VerificationReadingCommand(punto.id(), 3, BigDecimal.ONE, BigDecimal.TWO, null)))))
                    .withMessageContaining("declara 2 lecturas por punto");
        }

        @Test
        @DisplayName("con patron y equipo variables no se admite punto, y la unidad viene de fuera")
        void conModalidadVariableNoHayPunto() {
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(
                    unTipo(VerificationMode.PATRON_EQUIPO_VARIABLE, null, List.of()));
            estubarGuardado();

            service.recordVerification(new RecordVerificationCommand(reporte.getId(), List.of(
                    new VerificationReadingCommand(null, 1, new BigDecimal("1"), new BigDecimal("1.1"), "mA"))));

            assertThat(reporte.lecturasActivas()).hasSize(1);
            assertThat(reporte.lecturasActivas().getFirst().idPuntoVerificacion()).isNull();
            assertThat(reporte.lecturasActivas().getFirst().unidad()).isEqualTo("mA");
        }

        @Test
        @DisplayName("con patron y equipo variables, declarar un punto es un error")
        void conModalidadVariableElPuntoSobra() {
            ServiceReport reporte = unReporte();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(
                    unTipo(VerificationMode.PATRON_EQUIPO_VARIABLE, null, List.of()));

            assertThatIllegalArgumentException().isThrownBy(() -> service.recordVerification(
                            new RecordVerificationCommand(reporte.getId(), List.of(
                                    new VerificationReadingCommand(UUID.randomUUID(), 1, BigDecimal.ONE, BigDecimal.TWO, "mA")))))
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
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Limpieza", null, ServiceResult.OPERATIVO);
            reporte.recordVerification(List.of(
                    VerificationReading.of(punto.id(), 1, new BigDecimal("50"), new BigDecimal("50.2"), "mmHg"),
                    VerificationReading.of(punto.id(), 2, new BigDecimal("50"), new BigDecimal("49.9"), "mmHg")));
            reporte.pullEvents();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(unTipo(VerificationMode.PATRON_CONSTANTE, 2, List.of(punto)));
            estubarGuardado();

            service.finish(new FinishServiceReportCommand(reporte.getId()));

            assertThat(reporte.getEstado()).isEqualTo(ReportState.FINALIZADO);
        }

        @Test
        @DisplayName("no cierra con la tabla a medias: dos de tres lecturas no valen")
        void noCierraConLaVerificacionIncompleta() {
            VerificationPoint punto = unPunto("50");
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Limpieza", null, ServiceResult.OPERATIVO);
            reporte.recordVerification(List.of(
                    VerificationReading.of(punto.id(), 1, new BigDecimal("50"), new BigDecimal("50.2"), "mmHg")));
            reporte.pullEvents();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(unTipo(VerificationMode.PATRON_CONSTANTE, 3, List.of(punto)));

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
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Limpieza", null, ServiceResult.OPERATIVO);
            reporte.recordVerification(List.of(
                    VerificationReading.of(uno.id(), 1, new BigDecimal("50"), new BigDecimal("50.2"), "mmHg")));
            reporte.pullEvents();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(
                    unTipo(VerificationMode.PATRON_CONSTANTE, 1, List.of(uno, otro)));

            assertThatIllegalStateException()
                    .isThrownBy(() -> service.finish(new FinishServiceReportCommand(reporte.getId())))
                    .withMessageContaining("150");
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
            ServiceReport reporte = unReporte();
            reporte.fill(null, null, "Barrido completo", null, ServiceResult.OPERATIVO);
            reporte.recordVerification(List.of(VerificationReading.of(
                    null, 1, new BigDecimal("1"), new BigDecimal("1.1"), "mA")));
            reporte.pullEvents();
            estubarReporte(reporte);
            estubarCadenaDelCatalogo(
                    unTipo(VerificationMode.PATRON_EQUIPO_VARIABLE, null, List.of()));
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
            when(clientEquipmentServicePort.findById(EQUIPO)).thenReturn(
                    ClientEquipment.rehydrate(EQUIPO, "SN-1", MODELO, AREA, null, null, null, true));
            when(serviceReportPersistencePort.findByEquipment(EQUIPO)).thenReturn(List.of());

            assertThat(service.findByEquipment(EQUIPO)).isEmpty();
            verify(clientEquipmentServicePort).findById(EQUIPO);
        }
    }
}
