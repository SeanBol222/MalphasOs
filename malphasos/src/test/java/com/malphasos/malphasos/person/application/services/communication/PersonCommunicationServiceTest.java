package com.malphasos.malphasos.person.application.services.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.person.application.mapper.PersonCommunicationMapperImpl;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationRequest;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationResponse;
import com.malphasos.malphasos.person.application.model.request.EmailPersonUseCaseRequest;
import com.malphasos.malphasos.person.application.model.request.PhonePersonUseCaseRequest;
import com.malphasos.malphasos.person.application.ports.input.PersonServicePort;
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
 * por un doble: lo que se verifica aquí es la traducción entre los DTO de la frontera y el dominio,
 * no las reglas de negocio, que ya tienen sus propias pruebas.
 */
@ExtendWith(MockitoExtension.class)
class PersonCommunicationServiceTest {

    @Mock private PersonServicePort personServicePort;

    private PersonCommunicationService service() {
        return new PersonCommunicationService(personServicePort, new PersonCommunicationMapperImpl());
    }

    private PersonCommunicationRequest managerRequest() {
        return PersonCommunicationRequest.builder()
                .cedula("1010101010")
                .primerNombre("Grace")
                .primerApellido("Hopper")
                .tipoPersona(PersonType.MANAGER)
                .emailPersonList(List.of(new EmailPersonUseCaseRequest("grace@cliente.com")))
                .phonePersonList(List.of(new PhonePersonUseCaseRequest("3001234567")))
                .build();
    }

    @Test
    @DisplayName("save traduce la peticion al dominio y devuelve el identificador asignado")
    void saveDevuelveElIdentificador() {
        UUID asignado = UUID.randomUUID();
        when(personServicePort.save(any(Person.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Person.class)
                        .setIdentificador(asignado));

        UUID resultado = service().save(managerRequest());

        assertThat(resultado).isEqualTo(asignado);

        ArgumentCaptor<Person> capturada = ArgumentCaptor.forClass(Person.class);
        org.mockito.Mockito.verify(personServicePort).save(capturada.capture());

        Person enviada = capturada.getValue();
        assertThat(enviada.getCedula()).isEqualTo("1010101010");
        assertThat(enviada.getTipoPersona()).isEqualTo(PersonType.MANAGER);
        assertThat(enviada.getEmailPersonList()).singleElement()
                .extracting(EmailPerson::getCorreoPersona).isEqualTo("grace@cliente.com");
        assertThat(enviada.getPhonePersonList()).singleElement()
                .extracting(PhonePerson::getTelefonoPersona).isEqualTo("3001234567");
    }

    @Test
    @DisplayName("save no fija el identificador desde la peticion: lo asigna el servicio")
    void saveNoAceptaIdentificadorDeFuera() {
        when(personServicePort.save(any(Person.class))).thenAnswer(i -> i.getArgument(0));

        service().save(managerRequest());

        ArgumentCaptor<Person> capturada = ArgumentCaptor.forClass(Person.class);
        org.mockito.Mockito.verify(personServicePort).save(capturada.capture());

        assertThat(capturada.getValue().getIdentificador()).isNull();
    }

    @Test
    @DisplayName("una peticion sin listas de contacto no revienta: llegan vacias, no nulas")
    void saveSinListasDeContacto() {
        when(personServicePort.save(any(Person.class))).thenAnswer(i -> i.getArgument(0));

        PersonCommunicationRequest sinContacto = PersonCommunicationRequest.builder()
                .cedula("2020202020")
                .primerNombre("Ada")
                .primerApellido("Lovelace")
                .tipoPersona(PersonType.MANAGER)
                .build();

        assertThatCode(() -> service().save(sinContacto)).doesNotThrowAnyException();

        ArgumentCaptor<Person> capturada = ArgumentCaptor.forClass(Person.class);
        org.mockito.Mockito.verify(personServicePort).save(capturada.capture());

        // El servicio de personas recorre ambas listas al asignar identificadores. Si el mapeo las
        // dejara en null en vez de vacias, ese recorrido lanzaria NullPointerException.
        assertThat(capturada.getValue().getEmailPersonList()).isNotNull().isEmpty();
        assertThat(capturada.getValue().getPhonePersonList()).isNotNull().isEmpty();
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
                .tipoPersona(PersonType.MANAGER)
                .estadoActivo(true)
                .phonePersonList(List.of(PhonePerson.builder()
                        .idTelefonoPersona(idTelefono)
                        .telefonoPersona("3009876543")
                        .estadoActivo(true)
                        .build()))
                .build());

        PersonCommunicationResponse respuesta = service().findById(id);

        assertThat(respuesta.identificador()).isEqualTo(id);
        assertThat(respuesta.tipoPersona()).isEqualTo(PersonType.MANAGER);
        // En el original el DTO de telefono era una clase vacia: la lista llegaba con objetos sin
        // ningun dato dentro.
        assertThat(respuesta.phonePersonList()).singleElement().satisfies(telefono -> {
            assertThat(telefono.idTelefonoPersona()).isEqualTo(idTelefono);
            assertThat(telefono.telefonoPersona()).isEqualTo("3009876543");
        });
    }
}
