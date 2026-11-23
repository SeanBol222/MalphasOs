package com.malphasos.malphasos.equipment.application.services.lifeSheet;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheetPrint;
import com.malphasos.malphasos.equipment.domain.externalIntervention.ExternalServiceType;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import java.time.LocalDate;
import java.util.UUID;
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
                .con(UUID.fromString("5c9c8306-0000-4000-8000-000000000000"),
                        InterventionResult.FUERA_DE_SERVICIO, LocalDate.of(2026, 10, 4))
                .con(InterventionResult.OPERATIVO, LocalDate.of(2026, 3, 1))
                .impresa();

        LifeSheetPrint.Linea reciente = hoja.historial().getFirst();
        // El número del reporte delante del ingeniero, pedido por el usuario el 2026-11-23.
        assertThat(reciente.responsable())
                .isEqualTo("N.º 5C9C83 · Ada Lovelace · Servicios Biomédicos de Ejemplo S.A.S.");
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
    @DisplayName("un voltaje con decimales se escribe con coma y sin ceros de sobra: 1,5 V")
    void voltajeConDecimales() {
        // V57: el termometro a pila de los informes de la aplicacion anterior quedaba en «1 V».
        assertThat(new UnaHojaDeVida().conVoltaje("1.50").impresa().caracteristicas().voltaje()).isEqualTo("1,5 V");
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
        assertThat(hoja.historial().getFirst().responsable()).startsWith("N.º ").endsWith(" · Ada Lovelace");
    }

    @Test
    @DisplayName("un equipo dado de baja lo dice")
    void deBaja() {
        assertThat(new UnaHojaDeVida().deBaja().impresa().deBaja()).isTrue();
        assertThat(new UnaHojaDeVida().impresa().deBaja()).isFalse();
    }

    @Test
    @DisplayName("lo que hizo otra empresa entra en el historial por su fecha: la calibración de ayer, encima (V61)")
    void conLasExternas() {
        LifeSheetPrint hoja = new UnaHojaDeVida()
                .con(InterventionResult.OPERATIVO, LocalDate.of(2026, 10, 10))
                .conExterna(ExternalServiceType.CALIBRACION, InterventionResult.OPERATIVO_CON_RESTRICCIONES,
                        LocalDate.of(2026, 10, 17))
                .conExterna(ExternalServiceType.OTRO, null, LocalDate.of(2026, 9, 1))
                .impresa();

        assertThat(hoja.historial()).extracting(LifeSheetPrint.Linea::servicio)
                .containsExactly("Calibración", "Preventivo", "Otro");
        LifeSheetPrint.Linea calibracion = hoja.historial().getFirst();
        assertThat(calibracion.responsable()).isEqualTo("Externo · Metrología S.A.S. · CAL-2026-118");
        assertThat(calibracion.fecha()).isEqualTo("17 oct 2026");
        assertThat(hoja.estado().ultimoServicio()).isEqualTo("17 oct 2026");
        assertThat(hoja.estado().actual()).isEqualTo("Operativo con restricciones");
        assertThat(hoja.estado().intervenciones()).isEqualTo(3);
        // Una externa sin resultado no pinta marca ni se marca en rojo.
        LifeSheetPrint.Linea sinResultado = hoja.historial().getLast();
        assertThat(sinResultado.marca()).isEmpty();
        assertThat(sinResultado.resultado()).isEqualTo("—");
        assertThat(sinResultado.enRojo()).isFalse();
    }

    @Test
    @DisplayName("el estado de hoy es el de la más reciente que lo dice: una externa sin resultado no lo borra")
    void estadoSinResultado() {
        LifeSheetPrint hoja = new UnaHojaDeVida()
                .con(InterventionResult.FUERA_DE_SERVICIO, LocalDate.of(2026, 10, 1))
                .conExterna(ExternalServiceType.OTRO, null, LocalDate.of(2026, 10, 15))
                .impresa();

        assertThat(hoja.estado().ultimoServicio()).isEqualTo("15 oct 2026");
        assertThat(hoja.estado().actual()).isEqualTo("Fuera de servicio");
    }
}
