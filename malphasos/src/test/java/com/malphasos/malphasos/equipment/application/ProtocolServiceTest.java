package com.malphasos.malphasos.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.equipment.application.model.AssignedProtocolDetail;
import com.malphasos.malphasos.equipment.application.ports.output.EquipmentTypePersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ProtocolAssignmentPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ProtocolPersistencePort;
import com.malphasos.malphasos.equipment.application.services.protocol.ProtocolService;
import com.malphasos.malphasos.equipment.application.services.protocol.commands.AssignProtocolsCommand;
import com.malphasos.malphasos.equipment.application.services.protocol.commands.CreateProtocolCommand;
import com.malphasos.malphasos.equipment.application.services.protocol.commands.DeactivateProtocolCommand;
import com.malphasos.malphasos.equipment.application.services.protocol.commands.UpdateProtocolCommand;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.exception.EquipmentTypeNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ProtocolInUseException;
import com.malphasos.malphasos.equipment.domain.exception.ProtocolNameTakenException;
import com.malphasos.malphasos.equipment.domain.exception.ProtocolNotFoundException;
import com.malphasos.malphasos.equipment.domain.protocol.AssignedProtocol;
import com.malphasos.malphasos.equipment.domain.protocol.Protocol;
import com.malphasos.malphasos.equipment.domain.protocol.ProtocolAssignment.Requested;
import com.malphasos.malphasos.equipment.domain.protocol.ProtocolAssignment;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Las tres reglas que el servicio de protocolos comprueba fuera de los agregados: el nombre no se
 * repite, no se retira lo que un tipo usa, y solo se asigna lo vigente a lo vigente.
 *
 * <p>Cada guarda se prueba con el camino que guardaría <b>estubado como si fuera a funcionar</b>, y se
 * verifica que no se guardó nada: si la guarda faltara, la prueba tiene que caer por eso y no porque un
 * doble sin estubar devolviera vacío.
 */
@ExtendWith(MockitoExtension.class)
class ProtocolServiceTest {

    @Mock private ProtocolPersistencePort protocolPersistencePort;
    @Mock private ProtocolAssignmentPersistencePort protocolAssignmentPersistencePort;
    @Mock private EquipmentTypePersistencePort equipmentTypePersistencePort;
    @Mock private EventDispatcherPort eventDispatcherPort;

    @InjectMocks private ProtocolService service;

    private static Protocol vigente(String nombre) {
        return Protocol.rehydrate(UUID.randomUUID(), nombre, "Descripción", true);
    }

    @Nested
    @DisplayName("El nombre no se repite")
    class NombreUnico {

        @BeforeEach
        void guardarDevuelveLoGuardado() {
            lenient().when(protocolPersistencePort.save(any())).thenAnswer(llamada -> llamada.getArgument(0));
        }

        @Test
        @DisplayName("un alta con el nombre de otro vigente, escrito distinto, se rechaza sin guardar")
        void altaRepetida() {
            Protocol existente = vigente("Limpieza y desinfección");
            when(protocolPersistencePort.findActiveByClave("limpieza y desinfeccion"))
                    .thenReturn(Optional.of(existente));

            assertThatThrownBy(() -> service.create(new CreateProtocolCommand("LIMPIEZA y desinfeccion", "Otra")))
                    .isInstanceOf(ProtocolNameTakenException.class)
                    .hasMessageContaining("Limpieza y desinfección");
            verify(protocolPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("un alta con nombre libre se guarda y se publica")
        void altaLibre() {
            when(protocolPersistencePort.findActiveByClave("limpieza")).thenReturn(Optional.empty());

            Protocol creado = service.create(new CreateProtocolCommand("Limpieza", "Paño."));

            assertThat(creado.getNombre()).isEqualTo("Limpieza");
            verify(eventDispatcherPort).dispatchAll(any());
        }

        @Test
        @DisplayName("cambiar la descripción sin tocar el nombre no choca consigo mismo")
        void cambioPropio() {
            Protocol protocolo = vigente("Limpieza");
            when(protocolPersistencePort.findById(protocolo.getId())).thenReturn(Optional.of(protocolo));
            when(protocolPersistencePort.findActiveByClave("limpieza")).thenReturn(Optional.of(protocolo));

            service.update(new UpdateProtocolCommand(protocolo.getId(), "Limpieza", "Nueva descripción"));

            verify(protocolPersistencePort).save(protocolo);
        }

        @Test
        @DisplayName("renombrar al nombre de otro vigente se rechaza sin guardar")
        void renombrarAOtro() {
            Protocol protocolo = vigente("Limpieza");
            when(protocolPersistencePort.findById(protocolo.getId())).thenReturn(Optional.of(protocolo));
            when(protocolPersistencePort.findActiveByClave("verificacion electrica"))
                    .thenReturn(Optional.of(vigente("Verificación eléctrica")));

            assertThatThrownBy(() -> service.update(
                            new UpdateProtocolCommand(protocolo.getId(), "Verificacion electrica", "x")))
                    .isInstanceOf(ProtocolNameTakenException.class);
            verify(protocolPersistencePort, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Retirar")
    class Retirar {

        @Test
        @DisplayName("lo que usan tipos vigentes no se retira: hay que quitarlo de ellos antes")
        void enUso() {
            Protocol protocolo = vigente("Limpieza");
            when(protocolPersistencePort.findById(protocolo.getId())).thenReturn(Optional.of(protocolo));
            when(protocolPersistencePort.countActiveAssignments(protocolo.getId())).thenReturn(3L);
            lenient().when(protocolPersistencePort.save(any())).thenAnswer(llamada -> llamada.getArgument(0));

            assertThatThrownBy(() -> service.deactivate(new DeactivateProtocolCommand(protocolo.getId())))
                    .isInstanceOf(ProtocolInUseException.class)
                    .hasMessageContaining("3 tipos de equipo");
            assertThat(protocolo.isEstadoActivo()).isTrue();
            verify(protocolPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("lo que nadie usa se retira")
        void libre() {
            Protocol protocolo = vigente("Limpieza");
            when(protocolPersistencePort.findById(protocolo.getId())).thenReturn(Optional.of(protocolo));
            when(protocolPersistencePort.countActiveAssignments(protocolo.getId())).thenReturn(0L);
            when(protocolPersistencePort.save(any())).thenAnswer(llamada -> llamada.getArgument(0));

            service.deactivate(new DeactivateProtocolCommand(protocolo.getId()));

            assertThat(protocolo.isEstadoActivo()).isFalse();
        }
    }

    @Nested
    @DisplayName("Asignar a un tipo")
    class Asignar {

        private final UUID idTipo = UUID.randomUUID();
        private final Protocol limpieza = vigente("Limpieza");
        private final Protocol electrico = vigente("Eléctrico");

        private EquipmentType tipo(boolean activo) {
            EquipmentType tipo = org.mockito.Mockito.mock(EquipmentType.class);
            lenient().when(tipo.getId()).thenReturn(idTipo);
            lenient().when(tipo.getNombre()).thenReturn("Balanza");
            lenient().when(tipo.isEstadoActivo()).thenReturn(activo);

            return tipo;
        }

        /** Todo lo que haría falta para guardar, estubado: si una guarda falta, se guardaría. */
        private void caminoDeGuardadoAbierto() {
            lenient().when(protocolAssignmentPersistencePort.findByType(idTipo))
                    .thenReturn(ProtocolAssignment.empty(idTipo));
            lenient().when(protocolAssignmentPersistencePort.save(any()))
                    .thenAnswer(llamada -> llamada.getArgument(0));
            lenient().when(protocolPersistencePort.findAllById(anyCollection()))
                    .thenReturn(List.of(limpieza, electrico));
        }

        @Test
        @DisplayName("guarda en el orden pedido y devuelve los protocolos con su nombre")
        void asigna() {
            EquipmentType tipo = tipo(true);
            when(equipmentTypePersistencePort.findById(idTipo)).thenReturn(Optional.of(tipo));
            caminoDeGuardadoAbierto();

            List<AssignedProtocolDetail> resultado = service.assign(new AssignProtocolsCommand(idTipo, List.of(
                    new Requested(electrico.getId(), null), new Requested(limpieza.getId(), "Con guantes"))));

            assertThat(resultado)
                    .extracting(detalle -> detalle.protocolo().getNombre(), AssignedProtocolDetail::prioridad)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("Eléctrico", 1),
                            org.assertj.core.groups.Tuple.tuple("Limpieza", 2));
            verify(eventDispatcherPort).dispatchAll(any());
        }

        @Test
        @DisplayName("a un tipo que no existe, 404 del tipo")
        void tipoInexistente() {
            when(equipmentTypePersistencePort.findById(idTipo)).thenReturn(Optional.empty());
            caminoDeGuardadoAbierto();

            assertThatThrownBy(() -> service.assign(new AssignProtocolsCommand(idTipo, List.of())))
                    .isInstanceOf(EquipmentTypeNotFoundException.class);
            verify(protocolAssignmentPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("a un tipo retirado no se le asigna nada")
        void tipoRetirado() {
            EquipmentType tipo = tipo(false);
            when(equipmentTypePersistencePort.findById(idTipo)).thenReturn(Optional.of(tipo));
            caminoDeGuardadoAbierto();

            assertThatThrownBy(() -> service.assign(
                            new AssignProtocolsCommand(idTipo, List.of(new Requested(limpieza.getId(), null)))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("retirado");
            verify(protocolAssignmentPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("un protocolo que no existe se nombra en el 404")
        void protocoloInexistente() {
            UUID fantasma = UUID.randomUUID();
            EquipmentType tipo = tipo(true);
            when(equipmentTypePersistencePort.findById(idTipo)).thenReturn(Optional.of(tipo));
            caminoDeGuardadoAbierto();

            assertThatThrownBy(() -> service.assign(
                            new AssignProtocolsCommand(idTipo, List.of(new Requested(fantasma, null)))))
                    .isInstanceOf(ProtocolNotFoundException.class)
                    .hasMessageContaining(fantasma.toString());
            verify(protocolAssignmentPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("un protocolo retirado no se asigna")
        void protocoloRetirado() {
            Protocol retirado = Protocol.rehydrate(UUID.randomUUID(), "Viejo", "x", false);
            EquipmentType tipo = tipo(true);
            when(equipmentTypePersistencePort.findById(idTipo)).thenReturn(Optional.of(tipo));
            caminoDeGuardadoAbierto();
            when(protocolPersistencePort.findAllById(anyCollection())).thenReturn(List.of(retirado));

            assertThatThrownBy(() -> service.assign(
                            new AssignProtocolsCommand(idTipo, List.of(new Requested(retirado.getId(), null)))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Viejo");
            verify(protocolAssignmentPersistencePort, never()).save(any());
        }

        @Test
        @DisplayName("leer los de un tipo que no existe es 404, no una lista vacía")
        void leerDeTipoInexistente() {
            when(equipmentTypePersistencePort.findById(idTipo)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.protocolsOf(idTipo)).isInstanceOf(EquipmentTypeNotFoundException.class);
        }
    }

    @Test
    @DisplayName("los protocolos por tipo salen con nombre, en su prioridad, con dos consultas en total")
    void porTipo() {
        Protocol limpieza = Protocol.create("Limpieza", "Paño.");
        Protocol electrico = Protocol.create("Eléctrico", "Multímetro.");
        UUID idTipo = UUID.randomUUID();
        UUID otroTipo = UUID.randomUUID();
        when(protocolPersistencePort.findAll()).thenReturn(List.of(limpieza, electrico));
        when(protocolAssignmentPersistencePort.findAllActive()).thenReturn(List.of(
                ProtocolAssignment.rehydrate(idTipo, List.of(
                        new AssignedProtocol(electrico.getId(), 1, "Con guantes"),
                        new AssignedProtocol(limpieza.getId(), 2, null))),
                ProtocolAssignment.rehydrate(otroTipo, List.of(new AssignedProtocol(limpieza.getId(), 1, null)))));

        var porTipo = service.protocolsByType();

        assertThat(porTipo).containsOnlyKeys(idTipo, otroTipo);
        assertThat(porTipo.get(idTipo)).extracting(d -> d.protocolo().getNombre(), AssignedProtocolDetail::prioridad, AssignedProtocolDetail::indicacion)
                .containsExactly(tuple("Eléctrico", 1, "Con guantes"), tuple("Limpieza", 2, null));
        // Una consulta de protocolos para todos los tipos, no una por tipo.
        verify(protocolPersistencePort).findAll();
        verify(protocolPersistencePort, never()).findAllById(any());
    }
}
