package com.malphasos.malphasos.equipment.application.services.lifeSheet;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheetPrint;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cómo se escribe la hoja de vida en el papel (2026-10-18). Son las reglas que fijaba
 * {@code hoja-de-vida.spec.ts} cuando la hoja la pintaba Angular, ahora en el servidor.
 */
class LifeSheetPrintComposerTest {

    @Test
    @DisplayName("el estado es el de la intervención más reciente, que es la primera")
    void estadoDeLaMasReciente() {
        LifeSheetPrint hoja = new UnaHojaDeVida()
                .con(InterventionResult.OPERATIVO_CON_RESTRICCIONES, LocalDate.of(2026, 10, 4))
                .con(InterventionResult.OPERATIVO, LocalDate.of(2026, 3, 1))
                .impresa();

        assertThat(hoja.estado().actual()).isEqualTo("Operativo con restricciones");
        assertThat(hoja.estado().ultimoServicio()).isEqualTo("04 oct 2026");
        assertThat(hoja.estado().intervenciones()).isEqualTo(2);
        assertThat(hoja.estado().riesgo()).isEqualTo("Clase IIa");
    }

    @Test
    @DisplayName("sin historial, el estado no se afirma: «Sin servicios» y la raya")
    void sinHistorial() {
        LifeSheetPrint hoja = new UnaHojaDeVida().impresa();

        assertThat(hoja.estado().actual()).isEqualTo("Sin servicios");
        assertThat(hoja.estado().ultimoServicio()).isEqualTo("—");
        assertThat(hoja.historial()).isEmpty();
    }

    @Test
    @DisplayName("cada línea del historial dice quién con su empresa, y en rojo lo que no es operativo")
    void lineasDelHistorial() {
        LifeSheetPrint hoja = new UnaHojaDeVida()
                .con(InterventionResult.FUERA_DE_SERVICIO, LocalDate.of(2026, 10, 4))
                .con(InterventionResult.OPERATIVO, LocalDate.of(2026, 3, 1))
                .impresa();

        LifeSheetPrint.Linea reciente = hoja.historial().getFirst();
        assertThat(reciente.responsable()).isEqualTo("Ada Lovelace · Servicios Biomédicos de Ejemplo S.A.S.");
        assertThat(reciente.servicio()).isEqualTo("Preventivo");
        assertThat(reciente.marca()).isEqualTo("FUERA_DE_SERVICIO");
        assertThat(reciente.enRojo()).isTrue();
        assertThat(hoja.historial().get(1).enRojo()).isFalse();
    }

    @Test
    @DisplayName("lo que falta es la raya, y el valor de compra va en pesos sin decimales")
    void rayasYPesos() {
        LifeSheetPrint hoja = new UnaHojaDeVida().impresa();

        assertThat(hoja.identificacion().placaYCodigo()).isEqualTo("— · INT-7");
        assertThat(hoja.notaDeCompra())
                .startsWith("Fabricante Kern & Sohn · Alemania · Proveedor Proveedor Ejemplo · Compra 05 mar 2024")
                .contains("1.250.000")
                .doesNotContain(",00");
        assertThat(hoja.caracteristicas().corriente()).isEqualTo("0,1 A");
        assertThat(hoja.caracteristicas().voltaje()).isEqualTo("110 V");
    }

    @Test
    @DisplayName("las recomendaciones de cuidado salen una por línea, sin las vacías")
    void cuidado() {
        LifeSheetPrint hoja = new UnaHojaDeVida().impresa();

        assertThat(hoja.cuidado()).containsExactly("No exponer a humedad", "Limpiar la pantalla con un paño seco");
        assertThat(hoja.limpiezaCotidiana()).isEqualTo("Paño con alcohol al 70 %");
    }

    @Test
    @DisplayName("el pie lleva el código del formato y la fecha de impresión")
    void pie() {
        assertThat(new UnaHojaDeVida().impresa().codigoYFecha()).isEqualTo("FT-HV-01 · Actualizada el 18 oct 2026");
    }

    @Test
    @DisplayName("sin empresa configurada la hoja sale igual, sin membrete ni código")
    void sinEmpresa() {
        LifeSheetPrint hoja = new UnaHojaDeVida().sinEmpresa().con(InterventionResult.OPERATIVO, LocalDate.of(2026, 1, 1))
                .impresa();

        assertThat(hoja.empresa()).isEqualTo(LifeSheetPrint.Empresa.NINGUNA);
        assertThat(hoja.codigoYFecha()).isEqualTo("Actualizada el 18 oct 2026");
        assertThat(hoja.historial().getFirst().responsable()).isEqualTo("Ada Lovelace");
    }

    @Test
    @DisplayName("un equipo dado de baja lo dice")
    void deBaja() {
        assertThat(new UnaHojaDeVida().deBaja().impresa().deBaja()).isTrue();
        assertThat(new UnaHojaDeVida().impresa().deBaja()).isFalse();
    }
}
