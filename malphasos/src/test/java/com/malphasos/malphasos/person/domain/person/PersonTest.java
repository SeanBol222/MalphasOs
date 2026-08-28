package com.malphasos.malphasos.person.domain.person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @DisplayName("un ingeniero no puede tener un segundo rol")
    void engineerCannotHaveSecondType() {
        Person person = personWithoutContacts().setSegundoTipoPersona(PersonType.MANAGER);

        assertThatThrownBy(person::validateRoles)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ENGINEER");
    }

    @Test
    @DisplayName("el rol principal y el secundario no pueden coincidir")
    void typesCannotBeEqual() {
        Person person = personWithoutContacts()
                .setTipoPersona(PersonType.MANAGER)
                .setSegundoTipoPersona(PersonType.MANAGER);

        assertThatThrownBy(person::validateRoles)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no pueden ser el mismo");
    }

    @Test
    @DisplayName("una combinacion valida de roles no lanza excepcion")
    void validTypeCombination() {
        Person person = personWithoutContacts()
                .setTipoPersona(PersonType.CEO_CLIENT)
                .setSegundoTipoPersona(PersonType.MANAGER);

        assertThatCode(person::validateRoles).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sin segundo rol la validacion siempre pasa")
    void noSecondTypeMeansNothingToValidate() {
        Person person = personWithoutContacts();

        assertThatCode(person::validateRoles).doesNotThrowAnyException();
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
}
