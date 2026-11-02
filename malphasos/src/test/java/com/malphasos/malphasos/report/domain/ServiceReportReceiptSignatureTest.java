package com.malphasos.malphasos.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.malphasos.malphasos.report.UnDocumento;
import com.malphasos.malphasos.report.domain.serviceReport.Handover;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeCheck;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeState;
import com.malphasos.malphasos.report.domain.serviceReport.ReceiptSignature;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportReceiptSignedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Quien recibe el servicio firma el recibido (V39, 2026-10-17): RF-18, antes o después de cerrar. */
class ServiceReportReceiptSignatureTest {

    private static final IntakeCheck CONFORME = new IntakeCheck(IntakeState.CONFORME, null);
    private static final Handover ANA = new Handover("Ana Pérez", "52123456", "Jefe de enfermeria");
    private static final String HUELLA = "a".repeat(64);
    private static final LocalDateTime CUANDO = LocalDateTime.of(2026, 10, 17, 9, 30);

    private static ServiceReport borrador() {
        ServiceReport reporte = ServiceReport.open(UUID.randomUUID(), UUID.randomUUID());
        reporte.recordIntake(CONFORME, CONFORME, List.of());
        reporte.fill(null, null, "Limpieza", null, ServiceResult.OPERATIVO);
        reporte.pullEvents();
        return reporte;
    }

    private static ServiceReport cerrado() {
        ServiceReport reporte = borrador();
        reporte.finish(UnDocumento.CUALQUIERA);
        reporte.pullEvents();
        return reporte;
    }

    @Test
    @DisplayName("firmar un borrador guarda quién firmó, la huella y cuándo, y lo cuenta")
    void firmarUnBorrador() {
        ServiceReport reporte = borrador();

        reporte.signReceipt(ANA, HUELLA, CUANDO);

        assertThat(reporte.getRecibe()).isEqualTo(ANA);
        assertThat(reporte.getFirmaRecibe()).isEqualTo(new ReceiptSignature(HUELLA, CUANDO));
        assertThat(reporte.firmadoPorQuienRecibe()).isTrue();
        assertThat(reporte.pullEvents()).singleElement().isInstanceOf(ServiceReportReceiptSignedEvent.class);
    }

    @Test
    @DisplayName("un reporte cerrado se firma: es lo único que se le añade, y lo demás no cambia")
    void firmarUnCerrado() {
        ServiceReport reporte = cerrado();
        LocalDateTime finalizado = reporte.getFinalizado();

        reporte.signReceipt(ANA, HUELLA, CUANDO);

        assertThat(reporte.getFirmaRecibe().huella()).isEqualTo(HUELLA);
        assertThat(reporte.getRecibe()).isEqualTo(ANA);
        assertThat(reporte.getFinalizado()).isEqualTo(finalizado);
        assertThat(reporte.getDocumento()).isEqualTo(UnDocumento.CUALQUIERA);
    }

    @Test
    @DisplayName("quien firma sustituye a quien se había anotado como receptor sin firmar")
    void firmaOtraPersona() {
        ServiceReport reporte = borrador();
        reporte.recordHandover(new Handover("Luis Gómez", null, null));

        reporte.signReceipt(ANA, HUELLA, CUANDO);

        assertThat(reporte.getRecibe()).isEqualTo(ANA);
    }

    @Test
    @DisplayName("firmado no se vuelve a firmar, ni con la misma firma")
    void unaSolaVez() {
        ServiceReport reporte = borrador();
        reporte.signReceipt(ANA, HUELLA, CUANDO);
        reporte.pullEvents();

        assertThatIllegalStateException()
                .isThrownBy(() -> reporte.signReceipt(new Handover("Otra", null, null), "b".repeat(64), CUANDO))
                .withMessageContaining("ya tiene la firma");
        assertThat(reporte.getRecibe()).isEqualTo(ANA);
        assertThat(reporte.getFirmaRecibe().huella()).isEqualTo(HUELLA);
        assertThat(reporte.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("tras firmar no se cambia quién recibe: el nombre es el de quien firmó")
    void firmadoNoSeCambiaElNombre() {
        ServiceReport reporte = borrador();
        reporte.signReceipt(ANA, HUELLA, CUANDO);

        assertThatIllegalStateException()
                .isThrownBy(() -> reporte.recordHandover(new Handover("Otra", null, null)))
                .withMessageContaining("ya firmo");
        assertThatIllegalStateException().isThrownBy(() -> reporte.recordHandover(null));
        assertThat(reporte.getRecibe()).isEqualTo(ANA);
    }

    @Test
    @DisplayName("un reporte retirado no se firma")
    void retirado() {
        ServiceReport reporte = cerrado();
        reporte.deactivate();

        assertThatIllegalStateException().isThrownBy(() -> reporte.signReceipt(ANA, HUELLA, CUANDO))
                .withMessageContaining("retirado");
        assertThat(reporte.getFirmaRecibe()).isNull();
    }

    @Test
    @DisplayName("sin quién firma, sin huella o sin cuándo, no hay firma")
    void loQueLaFirmaExige() {
        assertThatNullPointerException().isThrownBy(() -> borrador().signReceipt(null, HUELLA, CUANDO));
        assertThatNullPointerException().isThrownBy(() -> borrador().signReceipt(ANA, null, CUANDO));
        assertThatNullPointerException().isThrownBy(() -> borrador().signReceipt(ANA, HUELLA, null));
    }

    @Test
    @DisplayName("sin firmar, quién recibe se sigue corrigiendo en el borrador")
    void sinFirmarSeCorrige() {
        ServiceReport reporte = borrador();
        reporte.recordHandover(ANA);
        reporte.recordHandover(new Handover("Luis Gómez", null, null));

        assertThat(reporte.getRecibe().nombre()).isEqualTo("Luis Gómez");
        assertThat(reporte.firmadoPorQuienRecibe()).isFalse();
    }

    // --- Quien recibe como persona registrada (V55, 2026-11-01) ----------------------------------

    @Test
    @DisplayName("una persona registrada firma: queda su identificador y no se copia ningún nombre")
    void firmaUnaPersonaRegistrada() {
        ServiceReport reporte = borrador();
        reporte.recordHandover(new Handover("Luis Gómez", null, null));
        reporte.pullEvents();
        UUID supervisor = UUID.randomUUID();

        reporte.signReceiptAs(supervisor, HUELLA, CUANDO);

        assertThat(reporte.getIdRecibe()).isEqualTo(supervisor);
        // Lo anotado sin firmar se descarta: quien firma es ella, y su nombre sale de su ficha.
        assertThat(reporte.getRecibe()).isNull();
        assertThat(reporte.getFirmaRecibe()).isEqualTo(new ReceiptSignature(HUELLA, CUANDO));
        assertThat(reporte.pullEvents()).singleElement().isInstanceOf(ServiceReportReceiptSignedEvent.class);
    }

    @Test
    @DisplayName("firmado, una persona registrada no vuelve a firmar, ni un retirado se firma")
    void personaRegistradaUnaSolaVez() {
        ServiceReport reporte = borrador();
        reporte.signReceipt(ANA, HUELLA, CUANDO);

        assertThatIllegalStateException()
                .isThrownBy(() -> reporte.signReceiptAs(UUID.randomUUID(), HUELLA, CUANDO))
                .withMessageContaining("ya tiene la firma");
        assertThat(reporte.getIdRecibe()).isNull();
        assertThat(reporte.getRecibe()).isEqualTo(ANA);

        ServiceReport retirado = borrador();
        retirado.deactivate();
        assertThatIllegalStateException()
                .isThrownBy(() -> retirado.signReceiptAs(UUID.randomUUID(), HUELLA, CUANDO))
                .withMessageContaining("retirado");
    }

    @Test
    @DisplayName("tras firmar una persona registrada, ya no se anota a nadie por su nombre")
    void personaRegistradaNoSeSustituye() {
        ServiceReport reporte = borrador();
        reporte.signReceiptAs(UUID.randomUUID(), HUELLA, CUANDO);

        assertThatIllegalStateException()
                .isThrownBy(() -> reporte.recordHandover(new Handover("Otra", null, null)));
    }
}
