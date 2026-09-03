package com.malphasos.malphasos.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.client.application.ports.input.ServiceAreaServicePort;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.equipment.application.ports.input.BrandServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentTypeServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ManufacturerServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ModelServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.ClientEquipmentPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.EquipmentPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ModelPersistencePort;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.ClientEquipmentService;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.RegisterClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.RelocateClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.equipment.EquipmentService;
import com.malphasos.malphasos.equipment.application.services.equipment.commands.CreateEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.model.ModelService;
import com.malphasos.malphasos.equipment.application.services.model.commands.CreateModelCommand;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.domain.exception.BrandNotFoundException;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * La cadena equipo → modelo → unidad. Lo que más importa aquí son las reglas que ninguna clave
 * foránea puede imponer, porque comprueban que algo esté <b>activo</b> y no solo que exista.
 */
@ExtendWith(MockitoExtension.class)
class EquipmentChainServiceTest {

    private static final UUID TIPO = UUID.randomUUID();
    private static final UUID MARCA = UUID.randomUUID();
    private static final UUID FABRICANTE = UUID.randomUUID();
    private static final UUID EQUIPO = UUID.randomUUID();
    private static final UUID MODELO = UUID.randomUUID();
    private static final UUID AREA = UUID.randomUUID();

    @Mock private EquipmentPersistencePort equipmentPort;
    @Mock private ModelPersistencePort modelPort;
    @Mock private ClientEquipmentPersistencePort unitPort;
    @Mock private EquipmentTypeServicePort typeService;
    @Mock private BrandServicePort brandService;
    @Mock private ManufacturerServicePort manufacturerService;
    @Mock private EquipmentServicePort equipmentService;
    @Mock private ModelServicePort modelService;
    @Mock private ServiceAreaServicePort areaService;
    @Mock private EventDispatcherPort dispatcher;

    @SuppressWarnings("unchecked")
    private List<DomainEvent<? extends Payload>> despachados() {
        ArgumentCaptor<List<? extends DomainEvent<? extends Payload>>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatchAll(captor.capture());

        return (List<DomainEvent<? extends Payload>>) captor.getValue();
    }

    @Nested
    @DisplayName("Asociacion marca-tipo")
    class Asociacion {

        private EquipmentService service() {
            return new EquipmentService(equipmentPort, typeService, brandService, dispatcher);
        }

        @Test
        @DisplayName("asociar comprueba que el tipo y la marca existan")
        void asociar() {
            when(equipmentPort.save(any(Equipment.class))).thenAnswer(i -> i.getArgument(0));

            service().create(new CreateEquipmentCommand(TIPO, MARCA));

            verify(typeService).findById(TIPO);
            verify(brandService).findById(MARCA);
            assertThat(despachados())
                    .extracting(evento -> evento.metadata().eventType())
                    .containsExactly("equipment.created");
        }

        @Test
        @DisplayName("si la marca no existe no se asocia nada")
        void marcaInexistente() {
            when(brandService.findById(MARCA)).thenThrow(new BrandNotFoundException(MARCA));

            assertThatThrownBy(() -> service().create(new CreateEquipmentCommand(TIPO, MARCA)))
                    .isInstanceOf(BrandNotFoundException.class);

            verify(equipmentPort, never()).save(any());
            verifyNoInteractions(dispatcher);
        }
    }

    @Nested
    @DisplayName("Modelo")
    class Modelo {

        private ModelService service() {
            return new ModelService(modelPort, manufacturerService, equipmentService, dispatcher);
        }

        @Test
        @DisplayName("no se registra un modelo sobre una asociacion retirada")
        void asociacionRetirada() {
            when(equipmentService.findById(EQUIPO))
                    .thenReturn(Equipment.rehydrate(EQUIPO, TIPO, MARCA, false));

            // La clave foranea comprueba que la fila exista, no que este activa.
            assertThatThrownBy(() ->
                            service().create(new CreateModelCommand("INV-1", FABRICANTE, EQUIPO)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("asociacion retirada");

            verify(modelPort, never()).save(any());
        }

        @Test
        @DisplayName("sobre una asociacion activa si se registra")
        void asociacionActiva() {
            when(equipmentService.findById(EQUIPO))
                    .thenReturn(Equipment.rehydrate(EQUIPO, TIPO, MARCA, true));
            when(modelPort.save(any(Model.class))).thenAnswer(i -> i.getArgument(0));

            service().create(new CreateModelCommand("INV-1", FABRICANTE, EQUIPO));

            verify(manufacturerService).findById(FABRICANTE);
            assertThat(despachados())
                    .extracting(evento -> evento.metadata().eventType())
                    .containsExactly("model.created");
        }
    }

    @Nested
    @DisplayName("Unidad de un cliente")
    class Unidad {

        private ClientEquipmentService service() {
            return new ClientEquipmentService(unitPort, modelService, areaService, dispatcher);
        }

        private void elAreaEsta(boolean activa) {
            when(areaService.findById(AREA))
                    .thenReturn(ServiceArea.rehydrate(AREA, "UCI", UUID.randomUUID(), activa));
        }

        private void elModeloEsta(boolean activo) {
            when(modelService.findById(MODELO))
                    .thenReturn(Model.rehydrate(MODELO, "INV-1", FABRICANTE, EQUIPO, activo));
        }

        @Test
        @DisplayName("incorporar una unidad consulta al modulo de clientes por el area")
        void incorporar() {
            elModeloEsta(true);
            elAreaEsta(true);
            when(unitPort.save(any(ClientEquipment.class))).thenAnswer(i -> i.getArgument(0));

            service().register(new RegisterClientEquipmentCommand(
                    "SN-001", MODELO, AREA, "INV-42", null, null));

            // Es la primera vez que equipment consulta a client.
            verify(areaService).findById(AREA);
            assertThat(despachados())
                    .extracting(evento -> evento.metadata().eventType())
                    .containsExactly("client-equipment.registered");
        }

        @Test
        @DisplayName("no se incorpora una unidad de un modelo retirado")
        void modeloRetirado() {
            elModeloEsta(false);

            assertThatThrownBy(() -> service().register(new RegisterClientEquipmentCommand(
                            "SN-001", MODELO, AREA, null, null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("modelo retirado");

            verify(unitPort, never()).save(any());
        }

        @Test
        @DisplayName("no se instala un equipo en un area de servicio cerrada")
        void areaCerrada() {
            elModeloEsta(true);
            elAreaEsta(false);

            assertThatThrownBy(() -> service().register(new RegisterClientEquipmentCommand(
                            "SN-001", MODELO, AREA, null, null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("area de servicio cerrada");

            verify(unitPort, never()).save(any());
        }

        @Test
        @DisplayName("tampoco se traslada a un area cerrada")
        void trasladarAAreaCerrada() {
            elAreaEsta(false);

            assertThatThrownBy(() ->
                            service().relocate(new RelocateClientEquipmentCommand(UUID.randomUUID(), AREA)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cerrada");

            verify(unitPort, never()).save(any());
        }

        @Test
        @DisplayName("trasladar a un area activa publica el hecho")
        void trasladar() {
            UUID id = UUID.randomUUID();
            elAreaEsta(true);
            when(unitPort.findById(id)).thenReturn(Optional.of(ClientEquipment.rehydrate(
                    id, "SN-001", MODELO, UUID.randomUUID(), null, null, null, true)));
            when(unitPort.save(any(ClientEquipment.class))).thenAnswer(i -> i.getArgument(0));

            service().relocate(new RelocateClientEquipmentCommand(id, AREA));

            assertThat(despachados())
                    .extracting(evento -> evento.metadata().eventType())
                    .containsExactly("client-equipment.relocated");
        }
    }
}
