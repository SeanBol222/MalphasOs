package com.malphasos.malphasos.workorder.application.services.workOrderStandards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.equipment.application.ports.input.ReferenceStandardServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.VerificationStandardsServicePort;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandardSheet;
import com.malphasos.malphasos.equipment.domain.verificationStandards.VerificationStandards;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El único patrón vigente de un tipo, el que la orden pone sola (V60). En el mismo paquete que la clase para
 * usar su constructor con reloj: la vigencia es de un día, y el día tiene que ser el de la prueba.
 */
class WorkOrderStandardsCatalogTest {

    private static final UUID TIPO = UUID.randomUUID();
    /** El 2026-11-19 en Bogotá. */
    private static final LocalDate HOY = LocalDate.of(2026, 11, 19);
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-19T15:00:00Z"), ZoneId.of("America/Bogota"));

    private final VerificationStandardsServicePort verificaciones = mock(VerificationStandardsServicePort.class);
    private final ReferenceStandardServicePort patrones = mock(ReferenceStandardServicePort.class);
    private final WorkOrderStandardsCatalog catalogo = new WorkOrderStandardsCatalog(verificaciones, patrones, RELOJ);

    private ReferenceStandard unPatron(LocalDate vence, boolean activo) {
        ReferenceStandard patron = ReferenceStandard.rehydrate(UUID.randomUUID(), "Pesas", null, "P-1", "CAL-1",
                vence, ReferenceStandardSheet.VACIA, activo);
        when(patrones.findById(patron.getId())).thenReturn(patron);
        return patron;
    }

    /** Dos verificaciones del tipo: el primero en las dos, que cuenta una vez. */
    private void elTipoAdmite(ReferenceStandard primero, ReferenceStandard... resto) {
        List<UUID> segunda = new java.util.ArrayList<>(List.of(primero.getId()));
        java.util.Arrays.stream(resto).map(ReferenceStandard::getId).forEach(segunda::add);
        when(verificaciones.standardsOf(TIPO)).thenReturn(List.of(
                VerificationStandards.rehydrate(UUID.randomUUID(), List.of(primero.getId())),
                VerificationStandards.rehydrate(UUID.randomUUID(), segunda)));
    }

    @Test
    @DisplayName("con un solo vigente entre los vencidos y retirados, ese es el que se pone")
    void unoSoloVigente() {
        ReferenceStandard vigente = unPatron(HOY, true);
        ReferenceStandard vencido = unPatron(HOY.minusDays(1), true);
        ReferenceStandard retirado = unPatron(HOY.plusYears(1), false);
        elTipoAdmite(vigente, vencido, retirado);

        // El dia del vencimiento todavia vale.
        assertThat(catalogo.onlyCurrent(TIPO)).contains(vigente.getId());
        assertThat(catalogo.admitted(TIPO))
                .containsExactlyInAnyOrder(vigente.getId(), vencido.getId(), retirado.getId());
    }

    @Test
    @DisplayName("con dos vigentes no elige, y sin ninguno tampoco")
    void variosONinguno() {
        ReferenceStandard uno = unPatron(HOY.plusMonths(1), true);
        ReferenceStandard otro = unPatron(HOY.plusMonths(2), true);
        elTipoAdmite(uno, otro);

        assertThat(catalogo.onlyCurrent(TIPO)).isEmpty();

        when(verificaciones.standardsOf(TIPO)).thenReturn(List.of());
        assertThat(catalogo.onlyCurrent(TIPO)).isEmpty();
    }
}
