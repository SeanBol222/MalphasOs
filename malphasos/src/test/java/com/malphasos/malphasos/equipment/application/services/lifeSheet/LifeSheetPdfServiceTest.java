package com.malphasos.malphasos.equipment.application.services.lifeSheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.company.application.ports.input.ServiceCompanyServicePort;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheetPdf;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheetPrint;
import com.malphasos.malphasos.equipment.application.ports.input.LifeSheetServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.LifeSheetPdfPort;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** La hoja de vida en PDF (2026-10-18): con el alcance de quien pide, la empresa y la fecha de Bogotá. */
@ExtendWith(MockitoExtension.class)
class LifeSheetPdfServiceTest {

    @Mock private LifeSheetServicePort hojas;
    @Mock private ServiceCompanyServicePort empresas;
    @Mock private LifeSheetPdfPort conversor;

    /** Las ocho de la noche del 17 en Bogotá, que en Greenwich ya es el 18. */
    private static final Clock NOCHE_EN_BOGOTA =
            Clock.fixed(Instant.parse("2026-10-18T01:00:00Z"), ZoneId.of("America/Bogota"));

    @Test
    @DisplayName("lee la hoja con el alcance de quien pide, la convierte y la nombra por su número")
    void pdf() {
        UnaHojaDeVida ejemplo = new UnaHojaDeVida();
        ReadScope alcance = ReadScope.unrestricted();
        when(hojas.findByEquipment(UnaHojaDeVida.ID, alcance)).thenReturn(ejemplo.hoja());
        when(empresas.find()).thenReturn(ejemplo.empresa());
        when(conversor.render(any())).thenReturn(new byte[] {1, 2, 3});

        LifeSheetPdf pdf = new LifeSheetPdfService(hojas, empresas, conversor, NOCHE_EN_BOGOTA)
                .pdf(UnaHojaDeVida.ID, alcance);

        assertThat(pdf.nombre()).isEqualTo("hoja-de-vida-HV-CDN-0001.pdf");
        assertThat(pdf.contenido()).containsExactly(1, 2, 3);
        verify(hojas).findByEquipment(UnaHojaDeVida.ID, alcance);
    }

    @Test
    @DisplayName("la fecha de actualización es la de Bogotá, no la de Greenwich del servidor")
    void fechaDeBogota() {
        UnaHojaDeVida ejemplo = new UnaHojaDeVida();
        when(hojas.findByEquipment(any(), any())).thenReturn(ejemplo.hoja());
        when(empresas.find()).thenReturn(ejemplo.empresa());
        ArgumentCaptor<LifeSheetPrint> impresa = ArgumentCaptor.forClass(LifeSheetPrint.class);
        when(conversor.render(impresa.capture())).thenReturn(new byte[0]);

        new LifeSheetPdfService(hojas, empresas, conversor, NOCHE_EN_BOGOTA).pdf(UnaHojaDeVida.ID, ReadScope.unrestricted());

        assertThat(impresa.getValue().codigoYFecha()).endsWith("Actualizada el 17 oct 2026");
    }
}
