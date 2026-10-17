package com.malphasos.malphasos.equipment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.ReportFormat;
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
import com.malphasos.malphasos.equipment.domain.equipmentType.events.EquipmentTypeUpdatedEvent;
import com.malphasos.malphasos.equipment.domain.magnitude.Magnitude;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El formato con que se imprime el reporte de un tipo de equipo (V32, 2026-10-16). */
class EquipmentTypeReportFormatTest {

    private static final UUID ID_TEMPERATURA = UUID.randomUUID();
    private static final Magnitude TEMPERATURA = new Magnitude(ID_TEMPERATURA, "temperatura", "Temperatura", true);
    private static final MeasurementUnit GRADOS =
            new MeasurementUnit(UUID.randomUUID(), ID_TEMPERATURA, "°C", "grado Celsius", true);

    private static TypeVerification enTemperatura(String punto) {
        return TypeVerification.of(TEMPERATURA, GRADOS, VerificationMode.PATRON_CONSTANTE, 3,
                List.of(VerificationPoint.of(new BigDecimal(punto))));
    }

    private static EquipmentType unTipo(List<TypeVerification> verificaciones) {
        EquipmentType tipo = EquipmentType.create("Termohigrometro", "Definicion", "Cuidados", "Electronica",
                null, null, verificaciones, 150_000L);
        tipo.pullEvents();
        return tipo;
    }

    private static List<String> tiposDe(EquipmentType tipo) {
        return tipo.pullEvents().stream().map(DomainEvent::metadata).map(m -> m.eventType()).toList();
    }

    @Test
    @DisplayName("nace con MP2 si se verifica y con MP0 si no, como V32 relleno los que ya habia")
    void porDefecto() {
        assertThat(unTipo(List.of(enTemperatura("20"))).getFormatoReporte()).isEqualTo(ReportFormat.MP2);
        assertThat(unTipo(List.of()).getFormatoReporte()).isEqualTo(ReportFormat.MP0);
    }

    @Test
    @DisplayName("elegir otro compatible lo cambia y lo anuncia; elegir el que tiene no")
    void elegir() {
        EquipmentType tipo = unTipo(List.of(enTemperatura("20")));

        tipo.chooseReportFormat(ReportFormat.MP1);
        assertThat(tipo.getFormatoReporte()).isEqualTo(ReportFormat.MP1);
        assertThat(tiposDe(tipo)).containsExactly(EquipmentTypeUpdatedEvent.TYPE);

        tipo.chooseReportFormat(ReportFormat.MP1);
        assertThat(tiposDe(tipo)).isEmpty();
    }

    @Test
    @DisplayName("MP0 no sirve para un tipo que se verifica, ni MP1 o MP2 para uno que no, ni ninguno")
    void incompatibles() {
        EquipmentType verificable = unTipo(List.of(enTemperatura("20")));
        EquipmentType sinVerificar = unTipo(List.of());

        assertThatIllegalArgumentException().isThrownBy(() -> verificable.chooseReportFormat(ReportFormat.MP0))
                .withMessageContaining("MP1 o MP2");
        assertThatIllegalArgumentException().isThrownBy(() -> sinVerificar.chooseReportFormat(ReportFormat.MP1))
                .withMessageContaining("MP0");
        assertThatIllegalArgumentException().isThrownBy(() -> sinVerificar.chooseReportFormat(ReportFormat.MP2));
        assertThatIllegalArgumentException().isThrownBy(() -> sinVerificar.chooseReportFormat(null));
        assertThat(verificable.getFormatoReporte()).isEqualTo(ReportFormat.MP2);
        assertThat(sinVerificar.getFormatoReporte()).isEqualTo(ReportFormat.MP0);
    }

    @Test
    @DisplayName("empezar a verificarse pasa de MP0 a MP2, y dejar de verificarse vuelve a MP0")
    void seAjustaSolo() {
        EquipmentType tipo = unTipo(List.of());

        tipo.declareVerifications(List.of(enTemperatura("20")));
        assertThat(tipo.getFormatoReporte()).isEqualTo(ReportFormat.MP2);

        tipo.declareVerifications(List.of());
        assertThat(tipo.getFormatoReporte()).isEqualTo(ReportFormat.MP0);
    }

    @Test
    @DisplayName("cambiar los puntos de un tipo con MP1 lo conserva: sigue sirviendo")
    void seRespeta() {
        EquipmentType tipo = unTipo(List.of(enTemperatura("20")));
        tipo.chooseReportFormat(ReportFormat.MP1);

        tipo.declareVerifications(List.of(enTemperatura("30")));

        assertThat(tipo.getFormatoReporte()).isEqualTo(ReportFormat.MP1);
    }

    @Test
    @DisplayName("se rehidrata con el guardado, y sin el con el de por defecto")
    void rehidratar() {
        UUID id = UUID.randomUUID();

        assertThat(EquipmentType.rehydrate(id, "T", "D", "C", "E", null, null, List.of(enTemperatura("20")),
                1L, ReportFormat.MP1, true).getFormatoReporte()).isEqualTo(ReportFormat.MP1);
        assertThat(EquipmentType.rehydrate(id, "T", "D", "C", "E", null, null, List.of(), 1L, true)
                .getFormatoReporte()).isEqualTo(ReportFormat.MP0);
    }
}
