package com.malphasos.malphasos.equipment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.equipment.domain.model.RiskClass;
import com.malphasos.malphasos.equipment.domain.model.TechnicalSheet;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La ficha técnica de un modelo, desde {@code V15}: riesgo, características y los cinco datos
 * eléctricos, dos de los cuales —voltaje y amperaje— vivían en el tipo de equipo.
 */
class ModelTechnicalSheetTest {

    private static final UUID FABRICANTE = UUID.randomUUID();
    private static final UUID EQUIPO = UUID.randomUUID();

    private static TechnicalSheet ficha(Integer voltaje, String amperaje) {
        return TechnicalSheet.of(RiskClass.IIA, "Pantalla LCD", "Red electrica", voltaje, 50,
                amperaje == null ? null : new BigDecimal(amperaje), 60);
    }

    @Test
    @DisplayName("un modelo dado de alta sin ficha tiene la ficha vacia, nunca nula")
    void sinFicha() {
        Model modelo = Model.create("GS14", null, FABRICANTE, EQUIPO);

        assertThat(modelo.getFichaTecnica()).isEqualTo(TechnicalSheet.EMPTY);
        assertThat(Model.rehydrate(UUID.randomUUID(), "GS14", null, FABRICANTE, EQUIPO, null, true)
                        .getFichaTecnica())
                .isEqualTo(TechnicalSheet.EMPTY);
    }

    @Test
    @DisplayName("los datos electricos, si vienen, son positivos")
    void positivos() {
        assertThatThrownBy(() -> ficha(0, null)).hasMessageContaining("voltaje");
        assertThatThrownBy(() -> ficha(110, "-1")).hasMessageContaining("amperaje");
        assertThatThrownBy(() -> TechnicalSheet.of(null, null, null, null, 0, null, null))
                .hasMessageContaining("potencia");
        assertThatThrownBy(() -> TechnicalSheet.of(null, null, null, null, null, null, -60))
                .hasMessageContaining("frecuencia");
    }

    @Test
    @DisplayName("el amperaje se guarda con dos decimales, y con tres se rechaza en vez de redondear")
    void amperajeConDosDecimales() {
        // En el esquema original numeric(2) redondeaba 2.5 a 3. Aqui se normaliza la escala, y un
        // tercer decimal se rechaza: redondearlo seria cambiar en silencio lo que dice la placa.
        assertThat(ficha(110, "2.5").amperaje()).isEqualTo(new BigDecimal("2.50"));
        assertThatThrownBy(() -> ficha(110, "2.555")).hasMessageContaining("dos decimales");
    }

    @Test
    @DisplayName("un texto en blanco es no tenerlo")
    void textosEnBlanco() {
        TechnicalSheet blanca = TechnicalSheet.of(null, "  ", "", null, null, null, null);

        assertThat(blanca).isEqualTo(TechnicalSheet.EMPTY);
    }

    @Test
    @DisplayName("corregir la ficha emite un evento; volver a mandar la misma, no")
    void describeEsIdempotente() {
        Model modelo = Model.create("GS14", null, FABRICANTE, EQUIPO);
        modelo.pullEvents();

        modelo.describe(ficha(110, "2.5"));
        assertThat(modelo.pullEvents()).hasSize(1);
        assertThat(modelo.getFichaTecnica().riesgo()).isEqualTo(RiskClass.IIA);

        // 2.50 y 2.5 son el mismo amperaje: sin normalizar la escala, esto emitiria un evento por nada.
        modelo.describe(ficha(110, "2.50"));
        assertThat(modelo.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("mandar la ficha sin un dato lo vacia: se reemplaza entera")
    void seReemplazaEntera() {
        Model modelo = Model.create("GS14", null, FABRICANTE, EQUIPO, ficha(110, "2.5"));

        modelo.describe(TechnicalSheet.of(RiskClass.I, null, null, null, null, null, null));

        assertThat(modelo.getFichaTecnica().voltaje()).isNull();
        assertThat(modelo.getFichaTecnica().riesgo()).isEqualTo(RiskClass.I);
    }
}
