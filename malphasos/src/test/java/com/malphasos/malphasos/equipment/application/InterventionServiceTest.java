package com.malphasos.malphasos.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.ServiceHistoryPort;
import com.malphasos.malphasos.equipment.application.services.intervention.InterventionService;
import com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException;
import com.malphasos.malphasos.equipment.domain.intervention.Intervention;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionType;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.time.LocalDateTime;
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
 * El historial de un equipo se lee acotado por dueño. Desde {@code V52} no se escribe: lo deriva de los
 * reportes cerrados {@code report}, por {@link ServiceHistoryPort}; la regla de qué línea cuenta la prueba
 * {@code ServiceHistoryAdapterTest}.
 */
@ExtendWith(MockitoExtension.class)
class InterventionServiceTest {

    private static final UUID EQUIPO = UUID.randomUUID();
    private static final UUID REPORTE = UUID.randomUUID();

    @Mock private ServiceHistoryPort serviceHistoryPort;
    @Mock private ClientEquipmentServicePort clientEquipmentServicePort;

    @InjectMocks private InterventionService service;

    @Test
    @DisplayName("el historial de un equipo ajeno no existe para quien pregunta")
    void historialDeEquipoAjeno() {
        ReadScope soloMio = ReadScope.ofClients(Set.of(UUID.randomUUID()));
        when(clientEquipmentServicePort.findById(EQUIPO, soloMio))
                .thenThrow(new ClientEquipmentNotFoundException(EQUIPO));

        assertThatThrownBy(() -> service.findByEquipment(EQUIPO, soloMio))
                .isInstanceOf(ClientEquipmentNotFoundException.class);

        verify(serviceHistoryPort, never()).historyOf(any());
    }

    @Test
    @DisplayName("el historial del equipo propio se devuelve, y se le pasa el alcance")
    void historialDelEquipoPropio() {
        ReadScope soloMio = ReadScope.ofClients(Set.of(UUID.randomUUID()));
        when(serviceHistoryPort.historyOf(EQUIPO)).thenReturn(List.of(new Intervention(
                EQUIPO, REPORTE, LocalDateTime.now(), InterventionType.CALIBRACION, InterventionResult.OPERATIVO,
                null, null)));

        assertThat(service.findByEquipment(EQUIPO, soloMio)).hasSize(1);

        // Con unrestricted() esto compilaria y no filtraria nada: lo que se fija es que viaje.
        verify(clientEquipmentServicePort).findById(EQUIPO, soloMio);
    }

    @Test
    @DisplayName("una linea sin alguno de los datos de RF-27 no se puede construir, y su reporte la identifica")
    void faltaUnDatoObligatorio() {
        LocalDateTime ahora = LocalDateTime.now();

        assertThatThrownBy(() -> new Intervention(null, REPORTE, ahora, InterventionType.PREVENTIVO,
                        InterventionResult.OPERATIVO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Intervention(EQUIPO, null, ahora, InterventionType.PREVENTIVO,
                        InterventionResult.OPERATIVO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Intervention(EQUIPO, REPORTE, null, InterventionType.PREVENTIVO,
                        InterventionResult.OPERATIVO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Intervention(EQUIPO, REPORTE, ahora, null, InterventionResult.OPERATIVO,
                        null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Intervention(EQUIPO, REPORTE, ahora, InterventionType.PREVENTIVO, null,
                        null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new Intervention(EQUIPO, REPORTE, ahora, InterventionType.PREVENTIVO,
                        InterventionResult.OPERATIVO, null, null).id())
                .isEqualTo(REPORTE);
    }

    @Test
    @DisplayName("los dos vocabularios repiten exactamente los de su origen")
    void losVocabulariosCoinciden() {
        // Son copias de ServiceType y ServiceResult, repetidas porque esos modulos estan aguas abajo en el
        // grafo. Si alguno crece y este no, la linea no puede representar lo que el reporte dice: el adaptador
        // que la compone lanzaria al traducirlo.
        assertThat(InterventionType.values()).hasSize(3);
        assertThat(InterventionResult.values()).hasSize(3);
        assertThat(java.util.Arrays.stream(InterventionType.values()).map(Enum::name).toList())
                .containsExactlyInAnyOrderElementsOf(
                        java.util.Arrays.stream(
                                        com.malphasos.malphasos.workorder.domain.workOrder.ServiceType.values())
                                .map(Enum::name)
                                .toList());
        assertThat(java.util.Arrays.stream(InterventionResult.values()).map(Enum::name).toList())
                .containsExactlyInAnyOrderElementsOf(
                        java.util.Arrays.stream(
                                        com.malphasos.malphasos.report.domain.serviceReport.ServiceResult.values())
                                .map(Enum::name)
                                .toList());
    }
}
