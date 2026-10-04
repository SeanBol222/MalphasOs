package com.malphasos.malphasos.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.client.application.ports.input.ClientOwnershipPort;
import com.malphasos.malphasos.client.application.ports.input.ServiceAreaServicePort;
import com.malphasos.malphasos.client.domain.exception.ServiceAreaNotFoundException;
import com.malphasos.malphasos.equipment.application.ports.input.ModelServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.ClientEquipmentPersistencePort;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.ClientEquipmentService;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Las tres lecturas de equipos instalados acotan por dueño, cada una por un camino distinto.
 *
 * <p>Y los caminos son el contenido de esta prueba, porque un equipo <b>no guarda su cliente</b>:
 * guarda su área. El listado completo traduce el alcance a áreas preguntando al módulo de clientes;
 * el de un área deja que resolver esa área con el alcance haga la comprobación; y el de un equipo
 * concreto pregunta por el dueño de su área, a tres saltos.
 *
 * <p>Las otras ocho lecturas del módulo —marcas, tipos, fabricantes, modelos, magnitudes y
 * unidades— <b>no se acotan y no es un olvido</b>: son el catálogo de la empresa y no tienen dueño.
 * Filtrar donde no hay dueño no significa nada, y esconderlo dejaría la ficha del equipo de un
 * cliente sin marca ni modelo que mostrar.
 */
@ExtendWith(MockitoExtension.class)
class EquipmentOwnershipFilteringTest {

    private static final UUID MIO = UUID.randomUUID();
    private static final UUID AJENO = UUID.randomUUID();
    private static final UUID MI_AREA = UUID.randomUUID();
    private static final UUID AREA_AJENA = UUID.randomUUID();

    private static final ReadScope SOLO_MIO = ReadScope.ofClients(Set.of(MIO));

    @Mock private ClientEquipmentPersistencePort unitPort;
    @Mock private ModelServicePort modelService;
    @Mock private ServiceAreaServicePort areaService;
    @Mock private ClientOwnershipPort ownershipPort;
    @Mock private EventDispatcherPort dispatcher;

    private ClientEquipmentService service() {
        return new ClientEquipmentService(unitPort, modelService, areaService, ownershipPort, dispatcher);
    }

    private ClientEquipment unaUnidad(UUID id, UUID idArea) {
        return ClientEquipment.rehydrate(id, "S-" + id.hashCode(), UUID.randomUUID(), idArea,
                "INV-1", null, null, true);
    }

    @Test
    @DisplayName("el inventario completo se acota a las areas de los clientes del alcance")
    void inventarioAcotado() {
        UUID unidad = UUID.randomUUID();
        when(ownershipPort.serviceAreasOf(Set.of(MIO))).thenReturn(Set.of(MI_AREA));
        when(unitPort.findByServiceAreaIn(Set.of(MI_AREA))).thenReturn(List.of(unaUnidad(unidad, MI_AREA)));

        assertThat(service().findAll(SOLO_MIO)).extracting(ClientEquipment::getId).containsExactly(unidad);
        verify(unitPort, never()).findAll();
    }

    @Test
    @DisplayName("sin areas no se consulta el inventario: no hay nada que pedir")
    void sinAreasNoSeConsulta() {
        when(ownershipPort.serviceAreasOf(Set.of(MIO))).thenReturn(Set.of());

        assertThat(service().findAll(SOLO_MIO)).isEmpty();

        // Es el caso de un representante recien nombrado sobre un cliente sin sedes. Preguntarlo
        // igual seria un «IN ()», que ademas no todos los motores aceptan.
        verify(unitPort, never()).findByServiceAreaIn(any());
        verify(unitPort, never()).findAll();
    }

    @Test
    @DisplayName("sin restriccion no se pregunta por areas de nadie")
    void sinRestriccionNoSePreguntanAreas() {
        when(unitPort.findAll()).thenReturn(List.of(unaUnidad(UUID.randomUUID(), MI_AREA)));

        assertThat(service().findAll(ReadScope.unrestricted())).hasSize(1);
        verify(ownershipPort, never()).serviceAreasOf(any());
    }

    @Test
    @DisplayName("un equipo instalado en el area de otro cliente no existe para quien pregunta")
    void equipoDeOtroCliente() {
        UUID unidad = UUID.randomUUID();
        when(unitPort.findById(unidad)).thenReturn(Optional.of(unaUnidad(unidad, AREA_AJENA)));
        when(areaService.findOwningClient(AREA_AJENA)).thenReturn(AJENO);

        assertThatThrownBy(() -> service().findById(unidad, SOLO_MIO))
                .isInstanceOf(ClientEquipmentNotFoundException.class);
    }

    @Test
    @DisplayName("el equipo propio se devuelve")
    void equipoPropio() {
        UUID unidad = UUID.randomUUID();
        when(unitPort.findById(unidad)).thenReturn(Optional.of(unaUnidad(unidad, MI_AREA)));
        when(areaService.findOwningClient(MI_AREA)).thenReturn(MIO);

        assertThat(service().findById(unidad, SOLO_MIO).getId()).isEqualTo(unidad);
    }

    @Test
    @DisplayName("sin restriccion no se paga el camino de tres saltos hasta el dueno")
    void sinRestriccionNoSeResuelveElDueno() {
        UUID unidad = UUID.randomUUID();
        when(unitPort.findById(unidad)).thenReturn(Optional.of(unaUnidad(unidad, MI_AREA)));

        assertThat(service().findById(unidad, ReadScope.unrestricted()).getId()).isEqualTo(unidad);

        // Si esta verificacion cae, cada lectura de un equipo pasa a costar el recorrido
        // area -> sede -> cliente para todo el mundo, incluido quien no se filtra.
        verify(areaService, never()).findOwningClient(any());
    }

    @Test
    @DisplayName("el inventario de un area ajena lo rechaza el propio modulo de clientes")
    void inventarioDeAreaAjena() {
        // Esta lectura no comprueba nada por su cuenta: le pasa el alcance a quien sabe de quien es
        // el area, y ese ya responde «no existe». La prueba fija que el alcance se le pase de
        // verdad, porque pasarle unrestricted compilaria igual y no filtraria nada.
        when(areaService.findById(AREA_AJENA, SOLO_MIO))
                .thenThrow(new ServiceAreaNotFoundException(AREA_AJENA));

        assertThatThrownBy(() -> service().findByServiceArea(AREA_AJENA, SOLO_MIO))
                .isInstanceOf(ServiceAreaNotFoundException.class);

        verify(unitPort, never()).findByServiceArea(any());
    }

    @Test
    @DisplayName("el inventario del area propia se devuelve")
    void inventarioDelAreaPropia() {
        UUID unidad = UUID.randomUUID();
        when(unitPort.findByServiceArea(MI_AREA)).thenReturn(List.of(unaUnidad(unidad, MI_AREA)));

        assertThat(service().findByServiceArea(MI_AREA, SOLO_MIO))
                .extracting(ClientEquipment::getId)
                .containsExactly(unidad);

        verify(areaService).findById(MI_AREA, SOLO_MIO);
    }
}
