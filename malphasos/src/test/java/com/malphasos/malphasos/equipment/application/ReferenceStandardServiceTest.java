package com.malphasos.malphasos.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.equipment.application.ports.output.ReferenceStandardPersistencePort;
import com.malphasos.malphasos.equipment.application.services.referenceStandard.ReferenceStandardService;
import com.malphasos.malphasos.equipment.application.services.referenceStandard.commands.SaveReferenceStandardCommand;
import com.malphasos.malphasos.equipment.domain.exception.ReferenceStandardNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ReferenceStandardSerialTakenException;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandardSheet;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** El catálogo de equipos patrón: la serie no se repite entre los vigentes (2026-10-14). */
@ExtendWith(MockitoExtension.class)
class ReferenceStandardServiceTest {

    private static final LocalDate VENCE = LocalDate.of(2027, 3, 31);
    private static final ReferenceStandardSheet SIN_FICHA = ReferenceStandardSheet.VACIA;

    @Mock private ReferenceStandardPersistencePort persistencia;
    @Mock private EventDispatcherPort eventos;

    @InjectMocks private ReferenceStandardService servicio;

    private static ReferenceStandard vigente(String serie) {
        return ReferenceStandard.rehydrate(
                UUID.randomUUID(), "Termómetro patrón", null, serie, "C", VENCE, SIN_FICHA, true);
    }

    @Test
    @DisplayName("con la serie libre se registra")
    void alta() {
        when(persistencia.findActiveBySerie("SN-1")).thenReturn(Optional.empty());
        when(persistencia.save(any())).thenAnswer(i -> i.getArgument(0));

        ReferenceStandardSheet ficha = new ReferenceStandardSheet("MS-200", null, null, null, null, null);
        ReferenceStandard creado = servicio.create(
                new SaveReferenceStandardCommand(null, "Pesas", null, "SN-1", "C", VENCE, ficha));

        assertThat(creado.getSerie()).isEqualTo("SN-1");
        assertThat(creado.getFicha()).isEqualTo(ficha);
        verify(eventos).dispatchAll(any());
    }

    @Test
    @DisplayName("con la serie de otro vigente no se registra, y dice de cuál")
    void serieOcupada() {
        when(persistencia.findActiveBySerie("SN-1")).thenReturn(Optional.of(vigente("SN-1")));

        assertThatThrownBy(() -> servicio.create(new SaveReferenceStandardCommand(
                        null, "Pesas", null, "SN-1", "C", VENCE, SIN_FICHA)))
                .isInstanceOf(ReferenceStandardSerialTakenException.class)
                .hasMessageContaining("Termómetro patrón");
        verify(persistencia, never()).save(any());
    }

    @Test
    @DisplayName("corregir uno sin cambiarle la serie no choca consigo mismo")
    void corregirSinCambiarSerie() {
        ReferenceStandard existente = vigente("SN-1");
        when(persistencia.findById(existente.getId())).thenReturn(Optional.of(existente));
        when(persistencia.findActiveBySerie("SN-1")).thenReturn(Optional.of(existente));
        when(persistencia.save(any())).thenAnswer(i -> i.getArgument(0));

        // Se renueva como se renueva de verdad: certificado, vencimiento y fecha de calibración a la vez.
        ReferenceStandardSheet ficha = new ReferenceStandardSheet(null, null, null, null, VENCE, "Metrología Andina");
        ReferenceStandard renovado = servicio.update(new SaveReferenceStandardCommand(
                existente.getId(), "Termómetro patrón", null, "SN-1", "C-2", VENCE.plusYears(1), ficha));

        assertThat(renovado.getCertificado()).isEqualTo("C-2");
        assertThat(renovado.getFicha()).isEqualTo(ficha);
    }

    @Test
    @DisplayName("uno que no existe responde que no existe")
    void inexistente() {
        UUID id = UUID.randomUUID();
        when(persistencia.findById(id)).thenReturn(Optional.empty());
        // Si no se comprobara la existencia, el choque de serie sería lo que fallara: se deja abierto.
        lenient().when(persistencia.findActiveBySerie(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.update(
                        new SaveReferenceStandardCommand(id, "N", null, "S", "C", VENCE, SIN_FICHA)))
                .isInstanceOf(ReferenceStandardNotFoundException.class);
    }
}
