package com.malphasos.malphasos.client.application.services.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.client.application.ports.output.ManagerPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.ServiceAreaPersistencePort;
import com.malphasos.malphasos.client.domain.manager.Manager;
import com.malphasos.malphasos.client.domain.manager.ManagerType;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationResponse;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.person.domain.person.PersonType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Quién responde por un área: la regla que decidió el usuario el 2026-10-05 para el «profesional
 * responsable» de la hoja de vida.
 */
@ExtendWith(MockitoExtension.class)
class ServiceAreaResponsibleServiceTest {

    private static final UUID AREA = UUID.randomUUID();
    private static final UUID SEDE = UUID.randomUUID();

    @Mock private ManagerPersistencePort managerPersistencePort;
    @Mock private ServiceAreaPersistencePort serviceAreaPersistencePort;
    @Mock private PersonCommunicationPort personCommunicationPort;

    @InjectMocks private ServiceAreaResponsibleService service;

    private Manager delArea(UUID persona, boolean activo) {
        return Manager.rehydrate(persona, ManagerType.SERVICE_AREA, AREA, activo);
    }

    private Manager deLaSede(UUID persona) {
        return Manager.rehydrate(persona, ManagerType.HEADQUARTER, SEDE, true);
    }

    private PersonCommunicationResponse persona(
            String nombre, String segundoNombre, String apellido, String segundoApellido, boolean activa) {
        return PersonCommunicationResponse.builder()
                .identificador(UUID.randomUUID())
                .cedula("1")
                .primerNombre(nombre)
                .segundoNombre(segundoNombre)
                .primerApellido(apellido)
                .segundoApellido(segundoApellido)
                .tipoPersona(PersonType.MANAGER)
                .estadoActivo(activa)
                .emailPersonList(List.of())
                .phonePersonList(List.of())
                .build();
    }

    @Test
    @DisplayName("con encargados en el area, son ellos, con el nombre completo y en orden alfabetico")
    void losDelArea() {
        UUID ana = UUID.randomUUID();
        UUID bruno = UUID.randomUUID();
        when(managerPersistencePort.findByServiceArea(AREA)).thenReturn(List.of(delArea(bruno, true), delArea(ana, true)));
        when(personCommunicationPort.findById(bruno)).thenReturn(persona("Bruno", null, "Diaz", null, true));
        when(personCommunicationPort.findById(ana)).thenReturn(persona("Ana", "Maria", "Perez", "Gomez", true));

        assertThat(service.responsiblesFor(AREA)).containsExactly("Ana Maria Perez Gomez", "Bruno Diaz");
        // Con encargados en el area, la sede ni se consulta.
        verify(managerPersistencePort, never()).findByHeadquarter(SEDE);
    }

    @Test
    @DisplayName("sin encargados en el area, responden los de su sede")
    void losDeLaSede() {
        UUID carla = UUID.randomUUID();
        when(managerPersistencePort.findByServiceArea(AREA)).thenReturn(List.of());
        when(serviceAreaPersistencePort.findById(AREA))
                .thenReturn(Optional.of(ServiceArea.rehydrate(AREA, "UCI", SEDE, true)));
        when(managerPersistencePort.findByHeadquarter(SEDE)).thenReturn(List.of(deLaSede(carla)));
        when(personCommunicationPort.findById(carla)).thenReturn(persona("Carla", null, "Ruiz", null, true));

        assertThat(service.responsiblesFor(AREA)).containsExactly("Carla Ruiz");
    }

    @Test
    @DisplayName("un encargado retirado del area no cuenta, y entonces responde la sede")
    void elRetiradoNoCuenta() {
        UUID retirado = UUID.randomUUID();
        UUID carla = UUID.randomUUID();
        when(managerPersistencePort.findByServiceArea(AREA)).thenReturn(List.of(delArea(retirado, false)));
        // Estubado aunque no deba llamarse: si el filtro desapareciera, el retirado saldria con su
        // nombre en lugar de que la sede respondiera, y no un nulo por un doble sin estubar.
        lenient().when(personCommunicationPort.findById(retirado)).thenReturn(persona("Ivan", null, "Rojas", null, true));
        when(serviceAreaPersistencePort.findById(AREA))
                .thenReturn(Optional.of(ServiceArea.rehydrate(AREA, "UCI", SEDE, true)));
        when(managerPersistencePort.findByHeadquarter(SEDE)).thenReturn(List.of(deLaSede(carla)));
        when(personCommunicationPort.findById(carla)).thenReturn(persona("Carla", null, "Ruiz", null, true));

        assertThat(service.responsiblesFor(AREA)).containsExactly("Carla Ruiz");
    }

    @Test
    @DisplayName("una persona dada de baja tampoco responde, aunque siga figurando como encargada")
    void laPersonaDeBajaNoCuenta() {
        UUID deBaja = UUID.randomUUID();
        when(managerPersistencePort.findByServiceArea(AREA)).thenReturn(List.of(delArea(deBaja, true)));
        when(personCommunicationPort.findById(deBaja)).thenReturn(persona("Ivan", null, "Rojas", null, false));
        when(serviceAreaPersistencePort.findById(AREA))
                .thenReturn(Optional.of(ServiceArea.rehydrate(AREA, "UCI", SEDE, true)));
        when(managerPersistencePort.findByHeadquarter(SEDE)).thenReturn(List.of());

        assertThat(service.responsiblesFor(AREA)).isEmpty();
    }

    @Test
    @DisplayName("sin encargados ni en el area ni en la sede, la lista esta vacia: no se inventa nadie")
    void nadie() {
        when(managerPersistencePort.findByServiceArea(AREA)).thenReturn(List.of());
        when(serviceAreaPersistencePort.findById(AREA))
                .thenReturn(Optional.of(ServiceArea.rehydrate(AREA, "UCI", SEDE, true)));
        when(managerPersistencePort.findByHeadquarter(SEDE)).thenReturn(List.of());

        assertThat(service.responsiblesFor(AREA)).isEmpty();
    }
}
