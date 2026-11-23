package com.malphasos.malphasos.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.ExternalInterventionPersistencePort;
import com.malphasos.malphasos.equipment.application.services.externalIntervention.ExternalInterventionService;
import com.malphasos.malphasos.equipment.application.services.externalIntervention.commands.RecordExternalInterventionCommand;
import com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ExternalInterventionNotFoundException;
import com.malphasos.malphasos.equipment.domain.externalIntervention.ExternalIntervention;
import com.malphasos.malphasos.equipment.domain.externalIntervention.ExternalServiceType;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Las reglas del servicio de intervenciones externas (V61): el alcance decide sobre qué equipos se escribe, y
 * una que no es de ese equipo no se retira.
 */
@ExtendWith(MockitoExtension.class)
class ExternalInterventionServiceTest {

    private static final UUID EQUIPO = UUID.randomUUID();
    private static final ReadScope DE_UN_CLIENTE = ReadScope.ofClients(Set.of(UUID.randomUUID()));
    /** Las once de la noche en Bogotá son ya el día siguiente en Greenwich. */
    private static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-11-24T04:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock private ExternalInterventionPersistencePort persistencia;
    @Mock private ClientEquipmentServicePort equipos;
    @Mock private EventDispatcherPort eventos;

    private ExternalInterventionService servicio;

    @BeforeEach
    void montar() {
        servicio = new ExternalInterventionService(persistencia, equipos, eventos, RELOJ);
        lenient().when(persistencia.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static RecordExternalInterventionCommand para(UUID equipo, LocalDate dia) {
        return new RecordExternalInterventionCommand(equipo, dia, ExternalServiceType.CALIBRACION,
                "Calibración", "Metrología S.A.S.", null, null);
    }

    @Test
    @DisplayName("se anota, se guarda y se publica su evento")
    void anotar() {
        ExternalIntervention guardada = servicio.record(para(EQUIPO, LocalDate.of(2026, 11, 20)), DE_UN_CLIENTE);

        assertThat(guardada.getIdEquipoCliente()).isEqualTo(EQUIPO);
        verify(equipos).findById(EQUIPO, DE_UN_CLIENTE);
        verify(eventos).dispatchAll(org.mockito.ArgumentMatchers.argThat(lista -> lista.size() == 1));
    }

    @Test
    @DisplayName("«hoy» es el de la empresa: a las once de la noche en Bogotá, hoy todavía vale")
    void hoyEnLaZonaDeLaEmpresa() {
        assertThat(servicio.record(para(EQUIPO, LocalDate.of(2026, 11, 23)), DE_UN_CLIENTE)).isNotNull();
        assertThatThrownBy(() -> servicio.record(para(EQUIPO, LocalDate.of(2026, 11, 24)), DE_UN_CLIENTE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("en un equipo fuera del alcance de quien llama no se anota nada: «no existe»")
    void fueraDelAlcance() {
        when(equipos.findById(EQUIPO, DE_UN_CLIENTE)).thenThrow(new ClientEquipmentNotFoundException(EQUIPO));

        assertThatThrownBy(() -> servicio.record(para(EQUIPO, LocalDate.of(2026, 11, 20)), DE_UN_CLIENTE))
                .isInstanceOf(ClientEquipmentNotFoundException.class);
        verify(persistencia, never()).save(any());
    }

    @Test
    @DisplayName("retirar una que es de otro equipo es «no existe», aunque el equipo sea del alcance")
    void deOtroEquipo() {
        ExternalIntervention ajena = ExternalIntervention.create(UUID.randomUUID(), LocalDate.of(2026, 11, 1),
                ExternalServiceType.OTRO, "Algo", "Otra", null, null, LocalDate.of(2026, 11, 23));
        when(persistencia.findById(ajena.getId())).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> servicio.deactivate(EQUIPO, ajena.getId(), DE_UN_CLIENTE))
                .isInstanceOf(ExternalInterventionNotFoundException.class);
        verify(persistencia, never()).save(any());
    }

    @Test
    @DisplayName("retirar una de un equipo fuera del alcance no llega a buscarla")
    void retirarFueraDelAlcance() {
        when(equipos.findById(EQUIPO, DE_UN_CLIENTE)).thenThrow(new ClientEquipmentNotFoundException(EQUIPO));

        assertThatThrownBy(() -> servicio.deactivate(EQUIPO, UUID.randomUUID(), DE_UN_CLIENTE))
                .isInstanceOf(ClientEquipmentNotFoundException.class);
        verify(persistencia, never()).findById(any());
    }
}
