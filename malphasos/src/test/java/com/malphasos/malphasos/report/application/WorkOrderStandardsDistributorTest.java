package com.malphasos.malphasos.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.equipment.application.ports.input.VerificationStandardsServicePort;
import com.malphasos.malphasos.equipment.domain.verificationStandards.VerificationStandards;
import com.malphasos.malphasos.report.application.services.serviceReport.WorkOrderStandardsDistributor;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationStandard;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Cómo llegan a un reporte los patrones de su orden (V60): a cada verificación que el catálogo les permite,
 * marcados, y sin tocar lo puesto a mano. Con un reporte de verdad: lo que se prueba es dónde acaban.
 */
@ExtendWith(MockitoExtension.class)
class WorkOrderStandardsDistributorTest {

    private static final UUID TIPO = UUID.randomUUID();
    private static final UUID PRESION = UUID.randomUUID();
    private static final UUID FRECUENCIA = UUID.randomUUID();
    private static final UUID SIMULADOR = UUID.randomUUID();
    private static final UUID MANOMETRO = UUID.randomUUID();

    @Mock private VerificationStandardsServicePort verificationStandardsServicePort;

    @InjectMocks private WorkOrderStandardsDistributor distributor;

    /** Un monitor: el simulador vale para presión y frecuencia, el manómetro solo para presión. */
    private void unMonitor() {
        when(verificationStandardsServicePort.standardsOf(TIPO)).thenReturn(List.of(
                VerificationStandards.rehydrate(PRESION, List.of(SIMULADOR, MANOMETRO)),
                VerificationStandards.rehydrate(FRECUENCIA, List.of(SIMULADOR))));
    }

    private static ServiceReport unBorrador() {
        return ServiceReport.open(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    @DisplayName("cada patron va a las verificaciones que el catalogo le permite, marcado desde la orden")
    void vanASuVerificacion() {
        unMonitor();
        ServiceReport reporte = unBorrador();

        distributor.apply(reporte, TIPO, Set.of(MANOMETRO, SIMULADOR), Set.of());

        assertThat(reporte.patronesActivos())
                .extracting(VerificationStandard::idVerificacion, VerificationStandard::idEquipoPatron,
                        VerificationStandard::desdeOrden)
                .containsExactlyInAnyOrder(
                        tuple(PRESION, SIMULADOR, true),
                        tuple(PRESION, MANOMETRO, true),
                        tuple(FRECUENCIA, SIMULADOR, true));
    }

    @Test
    @DisplayName("quitar uno de la orden lo saca de donde llego por ella, y deja el puesto a mano")
    void quitarRespetaLoPuestoAMano() {
        ServiceReport reporte = unBorrador();
        reporte.receiveFromWorkOrder(PRESION, SIMULADOR);
        reporte.receiveFromWorkOrder(FRECUENCIA, SIMULADOR);
        reporte.useReferenceStandard(PRESION, MANOMETRO);

        // Sin poner nada no hace falta preguntar al catalogo: no se estuba.
        distributor.apply(reporte, TIPO, Set.of(), Set.of(SIMULADOR, MANOMETRO));

        assertThat(reporte.patronesActivos())
                .extracting(VerificationStandard::idVerificacion, VerificationStandard::idEquipoPatron)
                .containsExactly(tuple(PRESION, MANOMETRO));
    }

    @Test
    @DisplayName("un patron que el catalogo ya no admite en ninguna verificacion no se pone")
    void sinVerificacionNoSePone() {
        unMonitor();
        ServiceReport reporte = unBorrador();

        distributor.apply(reporte, TIPO, Set.of(UUID.randomUUID()), Set.of());

        assertThat(reporte.patronesActivos()).isEmpty();
    }
}
