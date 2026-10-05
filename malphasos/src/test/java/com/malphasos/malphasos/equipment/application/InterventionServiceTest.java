package com.malphasos.malphasos.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.InterventionPersistencePort;
import com.malphasos.malphasos.equipment.application.services.intervention.InterventionService;
import com.malphasos.malphasos.equipment.application.services.intervention.commands.RecordInterventionCommand;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** El historial se escribe solo, una vez por reporte, y se lee acotado por dueño. */
@ExtendWith(MockitoExtension.class)
class InterventionServiceTest {

    private static final UUID EQUIPO = UUID.randomUUID();
    private static final UUID REPORTE = UUID.randomUUID();

    @Mock private InterventionPersistencePort interventionPersistencePort;
    @Mock private ClientEquipmentServicePort clientEquipmentServicePort;

    @InjectMocks private InterventionService service;

    private RecordInterventionCommand unCierre() {
        return new RecordInterventionCommand(
                EQUIPO,
                REPORTE,
                LocalDateTime.now(),
                InterventionType.PREVENTIVO,
                InterventionResult.OPERATIVO,
                Set.of());
    }

    @Test
    @DisplayName("anotar un cierre guarda la linea con los datos del reporte")
    void anotarUnCierre() {
        when(interventionPersistencePort.existsByReport(REPORTE)).thenReturn(false);

        service.record(unCierre());

        ArgumentCaptor<Intervention> guardada = ArgumentCaptor.forClass(Intervention.class);
        verify(interventionPersistencePort).save(guardada.capture());
        assertThat(guardada.getValue().idEquipoCliente()).isEqualTo(EQUIPO);
        assertThat(guardada.getValue().idReporteServicio()).isEqualTo(REPORTE);
        assertThat(guardada.getValue().tipoServicio()).isEqualTo(InterventionType.PREVENTIVO);
        assertThat(guardada.getValue().resultado()).isEqualTo(InterventionResult.OPERATIVO);
        assertThat(guardada.getValue().estadoActivo()).isTrue();
    }

    @Test
    @DisplayName("el mismo reporte anotado dos veces solo deja una linea")
    void idempotente() {
        // Un oyente de eventos puede recibir el mismo hecho dos veces -- un reintento, un reenvio --
        // y el historial no puede contar dos veces el mismo mantenimiento. La garantia ultima es del
        // esquema, que tiene un unico sobre el reporte; esto evita provocar la violacion.
        when(interventionPersistencePort.existsByReport(REPORTE)).thenReturn(true);

        service.record(unCierre());

        verify(interventionPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("el historial de un equipo ajeno no existe para quien pregunta")
    void historialDeEquipoAjeno() {
        ReadScope soloMio = ReadScope.ofClients(Set.of(UUID.randomUUID()));
        when(clientEquipmentServicePort.findById(EQUIPO, soloMio))
                .thenThrow(new ClientEquipmentNotFoundException(EQUIPO));

        assertThatThrownBy(() -> service.findByEquipment(EQUIPO, soloMio))
                .isInstanceOf(ClientEquipmentNotFoundException.class);

        verify(interventionPersistencePort, never()).findByEquipment(any());
    }

    @Test
    @DisplayName("el historial del equipo propio se devuelve, y se le pasa el alcance")
    void historialDelEquipoPropio() {
        ReadScope soloMio = ReadScope.ofClients(Set.of(UUID.randomUUID()));
        when(interventionPersistencePort.findByEquipment(EQUIPO))
                .thenReturn(List.of(Intervention.record(
                        EQUIPO, REPORTE, LocalDateTime.now(),
                        InterventionType.CALIBRACION, InterventionResult.OPERATIVO)));

        assertThat(service.findByEquipment(EQUIPO, soloMio)).hasSize(1);

        // Con unrestricted() esto compilaria y no filtraria nada: lo que se fija es que viaje.
        verify(clientEquipmentServicePort).findById(EQUIPO, soloMio);
    }

    @Test
    @DisplayName("una intervencion sin alguno de los datos de RF-27 no se puede construir")
    void faltaUnDatoObligatorio() {
        LocalDateTime ahora = LocalDateTime.now();

        assertThatThrownBy(() -> Intervention.record(null, REPORTE, ahora,
                        InterventionType.PREVENTIVO, InterventionResult.OPERATIVO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Intervention.record(EQUIPO, null, ahora,
                        InterventionType.PREVENTIVO, InterventionResult.OPERATIVO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Intervention.record(EQUIPO, REPORTE, null,
                        InterventionType.PREVENTIVO, InterventionResult.OPERATIVO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Intervention.record(EQUIPO, REPORTE, ahora,
                        null, InterventionResult.OPERATIVO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Intervention.record(EQUIPO, REPORTE, ahora,
                        InterventionType.PREVENTIVO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("los dos vocabularios repiten exactamente los de su origen")
    void losVocabulariosCoinciden() {
        // Son copias de ServiceType y ServiceResult, repetidas porque esos modulos estan aguas abajo
        // en el grafo. Si alguno crece y este no, la copia congelada deja de poder representar lo que
        // el reporte dice, y el CHECK del esquema lo rechazaria en tiempo de ejecucion.
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

    // ------------------------------------------------------------------------
    // El sustituto reemplaza al anterior: corregir no deja dos lineas
    // ------------------------------------------------------------------------

    private static final UUID REPORTE_CORREGIDO = UUID.randomUUID();

    private Intervention vigenteDe(UUID reporte) {
        return Intervention.record(EQUIPO, reporte, LocalDateTime.now().minusDays(1),
                InterventionType.PREVENTIVO, InterventionResult.FUERA_DE_SERVICIO);
    }

    private RecordInterventionCommand unCierreQueCorrige(Set<UUID> corregidos) {
        return new RecordInterventionCommand(EQUIPO, REPORTE, LocalDateTime.now(),
                InterventionType.PREVENTIVO, InterventionResult.OPERATIVO, corregidos);
    }

    @Test
    @DisplayName("cerrar un reporte que corrige a otro deja la linea vieja reemplazada por la nueva")
    void elSustitutoReemplazaAlAnterior() {
        Intervention vieja = vigenteDe(REPORTE_CORREGIDO);
        when(interventionPersistencePort.existsByReport(REPORTE)).thenReturn(false);
        when(interventionPersistencePort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(interventionPersistencePort.findByReports(Set.of(REPORTE_CORREGIDO))).thenReturn(List.of(vieja));

        service.record(unCierreQueCorrige(Set.of(REPORTE_CORREGIDO)));

        ArgumentCaptor<Intervention> guardadas = ArgumentCaptor.forClass(Intervention.class);
        verify(interventionPersistencePort, org.mockito.Mockito.times(2)).save(guardadas.capture());
        Intervention nueva = guardadas.getAllValues().getFirst();
        Intervention reemplazada = guardadas.getAllValues().getLast();

        // No se borra: queda apuntando a la que la sustituyo, que es el rastro de la correccion.
        assertThat(reemplazada.id()).isEqualTo(vieja.id());
        assertThat(reemplazada.estadoActivo()).isFalse();
        assertThat(reemplazada.reemplazadaPor()).isEqualTo(nueva.id());
        assertThat(nueva.estadoActivo()).isTrue();
    }

    @Test
    @DisplayName("una linea ya reemplazada no se vuelve a reemplazar: la cadena no se reescribe")
    void unaReemplazadaSeQuedaComoEsta() {
        // En una cadena de correcciones cada linea apunta a la que la sustituyo primero.
        Intervention yaReemplazada = vigenteDe(REPORTE_CORREGIDO).reemplazadaPor(UUID.randomUUID());
        when(interventionPersistencePort.existsByReport(REPORTE)).thenReturn(false);
        when(interventionPersistencePort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(interventionPersistencePort.findByReports(Set.of(REPORTE_CORREGIDO)))
                .thenReturn(List.of(yaReemplazada));

        service.record(unCierreQueCorrige(Set.of(REPORTE_CORREGIDO)));

        // Solo se guarda la nueva.
        verify(interventionPersistencePort, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    @DisplayName("un cierre que no corrige nada no busca lineas que reemplazar")
    void sinCorreccionNoSeBusca() {
        when(interventionPersistencePort.existsByReport(REPORTE)).thenReturn(false);

        service.record(unCierreQueCorrige(Set.of()));

        verify(interventionPersistencePort, never()).findByReports(any());
    }

    @Test
    @DisplayName("un cierre repetido no reemplaza dos veces: sale antes de tocar nada")
    void unCierreRepetidoNoTocaNada() {
        when(interventionPersistencePort.existsByReport(REPORTE)).thenReturn(true);

        service.record(unCierreQueCorrige(Set.of(REPORTE_CORREGIDO)));

        verify(interventionPersistencePort, never()).findByReports(any());
        verify(interventionPersistencePort, never()).save(any());
    }

    @Test
    @DisplayName("una intervencion no puede estar reemplazada y seguir activa, ni reemplazarse a si misma")
    void lasDosGuardasDelReemplazo() {
        Intervention una = vigenteDe(REPORTE);

        assertThatThrownBy(() -> una.reemplazadaPor(una.id())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Intervention.rehydrate(UUID.randomUUID(), EQUIPO, REPORTE, LocalDateTime.now(),
                        InterventionType.PREVENTIVO, InterventionResult.OPERATIVO, null, null, true,
                        UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
