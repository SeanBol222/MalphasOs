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

    private Person personaSinContactos() {
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
    void agregarCorreoSobrePersonaRecienConstruida() {
        Person persona = personaSinContactos();

        // El original dejaba la lista en null cuando el builder no la recibía, de modo que este
        // primer add lanzaba NullPointerException.
        assertThatCode(() -> persona.addEmail(
                        EmailPerson.builder()
                                .idCorreoPersona(UUID.randomUUID())
                                .correoPersona("ada@malphasos.local")
                                .estadoActivo(true)
                                .build()))
                .doesNotThrowAnyException();

        assertThat(persona.getEmailPersonList()).hasSize(1);
    }

    @Test
    @DisplayName("eliminar un correo lo desactiva en vez de quitarlo de la lista")
    void eliminarCorreoEsBorradoLogico() {
        Person persona = personaSinContactos();
        UUID idCorreo = UUID.randomUUID();
        persona.addEmail(EmailPerson.builder()
                .idCorreoPersona(idCorreo)
                .correoPersona("ada@malphasos.local")
                .estadoActivo(true)
                .build());

        persona.removeEmail(idCorreo);

        assertThat(persona.getEmailPersonList())
                .singleElement()
                .satisfies(correo -> assertThat(correo.isEstadoActivo()).isFalse());
    }

    @Test
    @DisplayName("eliminar un telefono lo desactiva en vez de quitarlo de la lista")
    void eliminarTelefonoEsBorradoLogico() {
        Person persona = personaSinContactos();
        UUID idTelefono = UUID.randomUUID();
        persona.addPhone(PhonePerson.builder()
                .idTelefonoPersona(idTelefono)
                .telefonoPersona("3001234567")
                .estadoActivo(true)
                .build());

        persona.removePhone(idTelefono);

        assertThat(persona.getPhonePersonList())
                .singleElement()
                .satisfies(telefono -> assertThat(telefono.isEstadoActivo()).isFalse());
    }

    @Test
    @DisplayName("un ingeniero no puede tener un segundo rol")
    void ingenieroNoAdmiteSegundoRol() {
        Person persona = personaSinContactos().setSegundoTipoPersona(PersonType.MANAGER);

        assertThatThrownBy(persona::validarRoles)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ENGINEER");
    }

    @Test
    @DisplayName("el rol principal y el secundario no pueden coincidir")
    void rolesNoPuedenSerIguales() {
        Person persona = personaSinContactos()
                .setTipoPersona(PersonType.MANAGER)
                .setSegundoTipoPersona(PersonType.MANAGER);

        assertThatThrownBy(persona::validarRoles)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no pueden ser el mismo");
    }

    @Test
    @DisplayName("una combinacion valida de roles no lanza excepcion")
    void combinacionValidaDeRoles() {
        Person persona = personaSinContactos()
                .setTipoPersona(PersonType.CEO_CLIENT)
                .setSegundoTipoPersona(PersonType.MANAGER);

        assertThatCode(persona::validarRoles).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sin segundo rol la validacion siempre pasa")
    void sinSegundoRolNoHayNadaQueValidar() {
        Person persona = personaSinContactos();

        assertThatCode(persona::validarRoles).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("eliminar un contacto inexistente no altera la lista ni lanza excepcion")
    void eliminarContactoInexistenteNoHaceNada() {
        Person persona = personaSinContactos();
        persona.addEmail(EmailPerson.builder()
                .idCorreoPersona(UUID.randomUUID())
                .correoPersona("ada@malphasos.local")
                .estadoActivo(true)
                .build());

        assertThatCode(() -> persona.removeEmail(UUID.randomUUID())).doesNotThrowAnyException();

        assertThat(persona.getEmailPersonList())
                .singleElement()
                .satisfies(correo -> assertThat(correo.isEstadoActivo()).isTrue());
    }
}
