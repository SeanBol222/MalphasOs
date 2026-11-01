package com.malphasos.malphasos.person.domain.person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.person.domain.exception.PrimaryEmailRemovalException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del comportamiento propio de {@link Person}: gestión de correos y teléfonos con borrado
 * lógico. No requieren contexto de Spring porque el dominio no depende del framework.
 */
class PersonTest {

    private Person personWithoutContacts() {
        return Person.builder()
                .identificador(UUID.randomUUID())
                .cedula("1234567890")
                .primerNombre("Ada")
                .primerApellido("Lovelace")
                .tipoPersona(PersonType.ENGINEER)
                .estadoActivo(true)
                .build();
    }

    @Test
    @DisplayName("agregar un correo a una persona construida sin correos no falla")
    void addEmailToNewlyBuiltPerson() {
        Person person = personWithoutContacts();

        // El original dejaba la lista en null cuando el builder no la recibía, de modo que este
        // primer add lanzaba NullPointerException.
        assertThatCode(() -> person.addEmail(
                        EmailPerson.builder()
                                .idCorreoPersona(UUID.randomUUID())
                                .correoPersona("ada@malphasos.local")
                                .estadoActivo(true)
                                .build()))
                .doesNotThrowAnyException();

        assertThat(person.getEmailPersonList()).hasSize(1);
    }

    @Test
    @DisplayName("eliminar un correo lo desactiva en vez de quitarlo de la lista")
    void removingEmailIsSoftDelete() {
        Person person = personWithoutContacts();
        UUID emailId = UUID.randomUUID();
        person.addEmail(EmailPerson.builder()
                .idCorreoPersona(emailId)
                .correoPersona("ada@malphasos.local")
                .estadoActivo(true)
                .build());

        person.removeEmail(emailId);

        assertThat(person.getEmailPersonList())
                .singleElement()
                .satisfies(email -> assertThat(email.isEstadoActivo()).isFalse());
    }

    @Test
    @DisplayName("eliminar un telefono lo desactiva en vez de quitarlo de la lista")
    void removingPhoneIsSoftDelete() {
        Person person = personWithoutContacts();
        UUID phoneId = UUID.randomUUID();
        person.addPhone(PhonePerson.builder()
                .idTelefonoPersona(phoneId)
                .telefonoPersona("3001234567")
                .estadoActivo(true)
                .build());

        person.removePhone(phoneId);

        assertThat(person.getPhonePersonList())
                .singleElement()
                .satisfies(phone -> assertThat(phone.isEstadoActivo()).isFalse());
    }

    @Test
    @DisplayName("eliminar un contacto inexistente no altera la lista ni lanza excepcion")
    void removingUnknownContactDoesNothing() {
        Person person = personWithoutContacts();
        person.addEmail(EmailPerson.builder()
                .idCorreoPersona(UUID.randomUUID())
                .correoPersona("ada@malphasos.local")
                .estadoActivo(true)
                .build());

        assertThatCode(() -> person.removeEmail(UUID.randomUUID())).doesNotThrowAnyException();

        assertThat(person.getEmailPersonList())
                .singleElement()
                .satisfies(email -> assertThat(email.isEstadoActivo()).isTrue());
    }

    // --- El correo de la cuenta (V40, 2026-10-17) -------------------------------------------

    private static Person conDosCorreos() {
        Person persona = Person.builder().tipoPersona(PersonType.ENGINEER).build();
        persona.addEmail(EmailPerson.builder().idCorreoPersona(UUID.randomUUID())
                .correoPersona("a@x.local").estadoActivo(true).principal(true).build());
        persona.addEmail(EmailPerson.builder().idCorreoPersona(UUID.randomUUID())
                .correoPersona("b@x.local").estadoActivo(true).build());
        return persona;
    }

    @Test
    @DisplayName("marcar un correo como el de la cuenta desmarca el que lo era: solo hay uno")
    void unSoloPrincipal() {
        Person persona = conDosCorreos();
        UUID segundo = persona.getEmailPersonList().get(1).getIdCorreoPersona();

        persona.markPrimaryEmail(segundo);

        assertThat(persona.getEmailPersonList()).filteredOn(EmailPerson::isPrincipal)
                .singleElement()
                .extracting(EmailPerson::getIdCorreoPersona)
                .isEqualTo(segundo);
    }

    @Test
    @DisplayName("un correo retirado no puede ser el de la cuenta")
    void retiradoNoEsPrincipal() {
        Person persona = conDosCorreos();
        EmailPerson segundo = persona.getEmailPersonList().get(1);
        segundo.setEstadoActivo(false);

        assertThatThrownBy(() -> persona.markPrimaryEmail(segundo.getIdCorreoPersona()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(persona.getEmailPersonList().getFirst().isPrincipal()).isTrue();
    }

    @Test
    @DisplayName("el correo de la cuenta no se retira sin elegir otro antes")
    void principalNoSeRetira() {
        Person persona = conDosCorreos();
        EmailPerson primero = persona.getEmailPersonList().getFirst();

        assertThatThrownBy(() -> persona.removeEmail(primero.getIdCorreoPersona()))
                .isInstanceOf(PrimaryEmailRemovalException.class);
        assertThat(primero.isEstadoActivo()).isTrue();
    }

    @Test
    @DisplayName("los demás correos se retiran como siempre")
    void otroSeRetira() {
        Person persona = conDosCorreos();
        EmailPerson segundo = persona.getEmailPersonList().get(1);

        persona.removeEmail(segundo.getIdCorreoPersona());

        assertThat(segundo.isEstadoActivo()).isFalse();
    }
}
