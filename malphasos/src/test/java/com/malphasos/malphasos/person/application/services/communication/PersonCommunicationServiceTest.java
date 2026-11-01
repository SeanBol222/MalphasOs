package com.malphasos.malphasos.person.application.services.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.person.application.mapper.PersonCommunicationMapperImpl;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationResponse;
import com.malphasos.malphasos.person.application.ports.input.PersonServicePort;
import com.malphasos.malphasos.person.application.ports.output.PersonPersistencePort;
import com.malphasos.malphasos.person.domain.person.EmailPerson;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.domain.person.PersonType;
import com.malphasos.malphasos.person.domain.person.PhonePerson;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pruebas del contrato que el módulo publica hacia los demás. El servicio de personas se sustituye
 * por un doble: lo que se verifica aquí es la traducción del dominio al DTO de la frontera, no las
 * reglas de negocio, que ya tienen sus propias pruebas. Hasta V53 traducía también en el otro sentido,
 * para el alta de los encargados.
 */
@ExtendWith(MockitoExtension.class)
class PersonCommunicationServiceTest {

    @Mock private PersonServicePort personServicePort;
    @Mock private PersonPersistencePort personPersistencePort;

    private PersonCommunicationService service() {
        return new PersonCommunicationService(personServicePort, new PersonCommunicationMapperImpl(), personPersistencePort);
    }

    @Test
    @DisplayName("findById devuelve una vista con los telefonos completos")
    void findByIdDevuelveLosTelefonos() {
        UUID id = UUID.randomUUID();
        UUID idTelefono = UUID.randomUUID();

        when(personServicePort.findById(id)).thenReturn(Person.builder()
                .identificador(id)
                .cedula("3030303030")
                .primerNombre("Alan")
                .primerApellido("Turing")
                .tipoPersona(PersonType.SUPERVISOR)
                .estadoActivo(true)
                .phonePersonList(List.of(PhonePerson.builder()
                        .idTelefonoPersona(idTelefono)
                        .telefonoPersona("3009876543")
                        .estadoActivo(true)
                        .build()))
                .build());

        PersonCommunicationResponse respuesta = service().findById(id);

        assertThat(respuesta.identificador()).isEqualTo(id);
        assertThat(respuesta.tipoPersona()).isEqualTo(PersonType.SUPERVISOR);
        // En el original el DTO de telefono era una clase vacia: la lista llegaba con objetos sin
        // ningun dato dentro.
        assertThat(respuesta.phonePersonList()).singleElement().satisfies(telefono -> {
            assertThat(telefono.idTelefonoPersona()).isEqualTo(idTelefono);
            assertThat(telefono.telefonoPersona()).isEqualTo("3009876543");
        });
    }

    @Test
    @DisplayName("exists dice si hay ficha sin lanzar, y un identificador nulo no la tiene")
    void existsNoLanza() {
        UUID conFicha = UUID.randomUUID();
        when(personPersistencePort.findById(conFicha)).thenReturn(java.util.Optional.of(new Person()));

        assertThat(service().exists(conFicha)).isTrue();
        assertThat(service().exists(UUID.randomUUID())).isFalse();
        assertThat(service().exists(null)).isFalse();
    }
}
