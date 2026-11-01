package com.malphasos.malphasos.person.infrastructure.input.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.json.JsonMapper;
import com.malphasos.malphasos.person.application.ports.input.PersonServicePort;
import com.malphasos.malphasos.person.domain.exception.KeycloakConnectionException;
import com.malphasos.malphasos.person.domain.exception.KeycloakEmailInUseException;
import com.malphasos.malphasos.person.domain.exception.KeycloakPasswordRejectedException;
import com.malphasos.malphasos.person.domain.exception.KeycloakUnauthorizedException;
import com.malphasos.malphasos.person.domain.exception.KeycloakUserAlreadyExistsException;
import com.malphasos.malphasos.person.domain.exception.KeycloakUserNotFoundException;
import com.malphasos.malphasos.person.domain.exception.PersonNotFoundException;
import com.malphasos.malphasos.person.domain.exception.PrimaryEmailRemovalException;
import com.malphasos.malphasos.person.domain.person.EmailPerson;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.domain.person.PersonType;
import com.malphasos.malphasos.person.infrastructure.input.model.request.EmailPersonCreateRequest;
import com.malphasos.malphasos.person.infrastructure.input.model.request.PersonRegisterRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifica el contrato HTTP del módulo: rutas, códigos de estado, validación de la petición y
 * traducción de excepciones. El servicio se sustituye por un doble porque aquí se prueba la capa
 * web, no la lógica de negocio.
 *
 * <p>La seguridad está desactivada en la configuración local, de modo que las peticiones no
 * necesitan token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(com.malphasos.malphasos.TestcontainersConfiguration.class)
class PersonRestAdapterTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;

    @MockitoBean private PersonServicePort personServicePort;

    private Person samplePerson(UUID id) {
        return Person.builder()
                .identificador(id)
                .cedula("1234567890")
                .primerNombre("Ada")
                .primerApellido("Lovelace")
                .tipoPersona(PersonType.ENGINEER)
                .estadoActivo(true)
                .build();
    }

    private PersonRegisterRequest validRegisterRequest() {
        return PersonRegisterRequest.builder()
                .cedula("1234567890")
                .primerNombre("Ada")
                .primerApellido("Lovelace")
                .nombreUsuario("ada")
                .password("secreto")
                .emailPersonList(List.of(
                        EmailPersonCreateRequest.builder().correoPersona("ada@ejemplo.com").build()))
                .phonePersonList(List.of())
                .build();
    }

    @Test
    @DisplayName("listar personas responde 200 con la coleccion")
    void listPersonsReturnsOk() throws Exception {
        when(personServicePort.findAll()).thenReturn(List.of(samplePerson(UUID.randomUUID())));

        mockMvc.perform(get("/v1/api/persons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].primerNombre").value("Ada"))
                .andExpect(jsonPath("$[0].tipoPersona").value("ENGINEER"));
    }

    @Test
    @DisplayName("una persona inexistente responde 404 con el codigo del catalogo")
    void unknownPersonReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(personServicePort.findById(id)).thenThrow(new PersonNotFoundException(id));

        mockMvc.perform(get("/v1/api/persons/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_PERSON_001"));
    }

    @Test
    @DisplayName("registrar un ingeniero responde 201")
    void registerEngineerReturnsCreated() throws Exception {
        when(personServicePort.registerEngineer(any())).thenReturn(samplePerson(UUID.randomUUID()));

        mockMvc.perform(post("/v1/api/persons/engineers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.identificador").isNotEmpty());
    }

    @Test
    @DisplayName("las tres rutas de registro cuelgan de la misma version del API")
    void allRegistrationRoutesShareTheSameVersion() throws Exception {
        // El original repartia estas tres operaciones entre /vi/, /v1/ y /v2/, la primera por un
        // error tipografico.
        when(personServicePort.registerEngineer(any())).thenReturn(samplePerson(UUID.randomUUID()));
        when(personServicePort.registerAdmin(any())).thenReturn(samplePerson(UUID.randomUUID()));
        when(personServicePort.registerSupervisor(any())).thenReturn(samplePerson(UUID.randomUUID()));

        for (String ruta : List.of("engineers", "admins", "supervisors")) {
            mockMvc.perform(post("/v1/api/persons/" + ruta)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(validRegisterRequest())))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    @DisplayName("un registro sin correo se rechaza con 400 y no llega al servicio")
    void registerWithoutEmailIsRejected() throws Exception {
        PersonRegisterRequest sinCorreo = PersonRegisterRequest.builder()
                .cedula("1234567890")
                .primerNombre("Ada")
                .primerApellido("Lovelace")
                .nombreUsuario("ada")
                .password("secreto")
                .emailPersonList(List.of())
                .build();

        mockMvc.perform(post("/v1/api/persons/engineers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(sinCorreo)))
                .andExpect(status().isBadRequest());

        verify(personServicePort, never()).registerEngineer(any());
    }

    @Test
    @DisplayName("ya no hay alta sin cuenta ni de representante legal: toda persona entra con usuario (V53)")
    void sinAltaSinCuenta() throws Exception {
        mockMvc.perform(post("/v1/api/persons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/v1/api/persons/ceo-clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("un tipo de persona fuera del catalogo se rechaza al deserializar")
    void unknownPersonTypeIsRejected() throws Exception {
        String tipoInvalido = """
                {"cedula":"1234567890","primerNombre":"Ada","primerApellido":"Lovelace",
                 "tipoPersona":"PRESIDENTE"}
                """;

        mockMvc.perform(put("/v1/api/persons/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tipoInvalido))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un usuario ya existente en Keycloak responde 409")
    void duplicatedKeycloakUserReturnsConflict() throws Exception {
        when(personServicePort.registerEngineer(any()))
                .thenThrow(new KeycloakUserAlreadyExistsException("ya existe"));

        mockMvc.perform(post("/v1/api/persons/engineers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_KEYCLOAK_001"));
    }

    @Test
    @DisplayName("un dato que el dominio rechaza responde 400 y no 500")
    void invalidDataReturnsBadRequest() throws Exception {
        when(personServicePort.registerEngineer(any()))
                .thenThrow(new IllegalArgumentException("Dato rechazado por el dominio"));

        mockMvc.perform(post("/v1/api/persons/engineers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_PERSON_002"));
    }

    @Test
    @DisplayName("desactivar una persona responde 204")
    void deletePersonReturnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/api/persons/{id}", id)).andExpect(status().isNoContent());

        verify(personServicePort).delete(id);
    }

    @Test
    @DisplayName("desactivar con Keycloak caido responde 503 y no un 500 generico")
    void deleteWithKeycloakDownReturnsServiceUnavailable() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new KeycloakConnectionException("keycloak caido")).when(personServicePort).delete(id);

        mockMvc.perform(delete("/v1/api/persons/{id}", id))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ERR_KEYCLOAK_004"));
    }

    @Test
    @DisplayName("desactivar con un secreto invalido contra Keycloak responde 502")
    void deleteWithInvalidKeycloakSecretReturnsBadGateway() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new KeycloakUnauthorizedException("secreto invalido")).when(personServicePort).delete(id);

        mockMvc.perform(delete("/v1/api/persons/{id}", id))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ERR_KEYCLOAK_003"));
    }

    @Test
    @DisplayName("agregar un correo usa POST y responde 201")
    void addEmailUsesPost() throws Exception {
        // El original creaba correos con PUT.
        UUID personId = UUID.randomUUID();
        when(personServicePort.addEmail(eq(personId), any())).thenReturn(samplePerson(personId));

        mockMvc.perform(post("/v1/api/persons/{id}/emails", personId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correoPersona\":\"ada@ejemplo.com\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("un correo con formato invalido se rechaza")
    void malformedEmailIsRejected() throws Exception {
        mockMvc.perform(post("/v1/api/persons/{id}/emails", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correoPersona\":\"esto-no-es-un-correo\"}"))
                .andExpect(status().isBadRequest());

        verify(personServicePort, never()).addEmail(any(), any());
    }

    @Test
    @DisplayName("los tres recursos de persona aparecen en su grupo de OpenAPI")
    void recursosDocumentados() throws Exception {
        // Este grupo tenia una comprobacion de que el documento responde 200 -en SecurityIntegrationTest-
        // y ninguna de que trajera algo dentro. Son dos cosas distintas: un grupo vacio tambien responde
        // 200. Los sub-recursos van nombrados porque son controladores aparte, y el patron del grupo los
        // cubre por prefijo: si alguien cambiara la ruta de uno, aqui se veria.
        String docs = mockMvc.perform(get("/v3/api-docs/person"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(docs)
                .describedAs("La persona y sus dos sub-recursos deben estar en el grupo person")
                .contains("/v1/api/persons")
                .contains("/v1/api/persons/{personId}/emails")
                .contains("/v1/api/persons/{personId}/phones");
    }

    @Test
    @DisplayName("usar un correo para la cuenta es un PATCH sobre su sub-recurso")
    void marcarPrincipal() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID emailId = UUID.randomUUID();
        when(personServicePort.markPrimaryEmail(personId, emailId)).thenReturn(samplePerson(personId));

        mockMvc.perform(patch("/v1/api/persons/{id}/emails/{emailId}/primary", personId, emailId))
                .andExpect(status().isOk());

        verify(personServicePort).markPrimaryEmail(personId, emailId);
    }

    @Test
    @DisplayName("retirar el correo de la cuenta responde 409 con su código")
    void retirarPrincipal() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID emailId = UUID.randomUUID();
        when(personServicePort.removeEmail(personId, emailId))
                .thenThrow(new PrimaryEmailRemovalException(emailId));

        mockMvc.perform(delete("/v1/api/persons/{id}/emails/{emailId}", personId, emailId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_PERSON_004"));
    }

    @Test
    @DisplayName("un correo que otra cuenta ya usa responde 409 con un código distinto de «el usuario ya existe»")
    void correoEnUso() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID emailId = UUID.randomUUID();
        when(personServicePort.markPrimaryEmail(personId, emailId))
                .thenThrow(new KeycloakEmailInUseException("en uso", null));

        mockMvc.perform(patch("/v1/api/persons/{id}/emails/{emailId}/primary", personId, emailId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_KEYCLOAK_005"));
    }

    @Test
    @DisplayName("cada correo de la respuesta dice si es el de la cuenta")
    void respuestaConPrincipal() throws Exception {
        UUID personId = UUID.randomUUID();
        Person persona = samplePerson(personId);
        persona.addEmail(EmailPerson.builder().idCorreoPersona(UUID.randomUUID())
                .correoPersona("ada@ejemplo.com").estadoActivo(true).principal(true).build());
        when(personServicePort.findById(personId)).thenReturn(persona);

        mockMvc.perform(get("/v1/api/persons/{id}", personId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailPersonList[0].principal").value(true));
    }

    // --- Restablecer la contraseña (2026-10-17) ---------------------------------------------

    @Test
    @DisplayName("restablecer la contraseña es un PATCH que responde 204 y no devuelve la contraseña")
    void restablecer() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/v1/api/persons/{id}/password", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"temporal-de-prueba\"}"))
                .andExpect(status().isNoContent());

        verify(personServicePort).resetPassword(id, "temporal-de-prueba");
    }

    @Test
    @DisplayName("sin contraseña no llega al servicio")
    void restablecerSinContrasena() throws Exception {
        mockMvc.perform(patch("/v1/api/persons/{id}/password", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\" \"}"))
                .andExpect(status().isBadRequest());

        verify(personServicePort, never()).resetPassword(any(), any());
    }

    @Test
    @DisplayName("una contraseña que la política rechaza responde 400 con su propio código")
    void restablecerRechazada() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new KeycloakPasswordRejectedException("corta", null)).when(personServicePort).resetPassword(eq(id), any());

        mockMvc.perform(patch("/v1/api/persons/{id}/password", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_KEYCLOAK_006"));
    }

    @Test
    @DisplayName("una persona cuya cuenta no está en Keycloak responde 404 con su código, no 500")
    void restablecerSinCuenta() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new KeycloakUserNotFoundException("no esta")).when(personServicePort).resetPassword(eq(id), any());

        mockMvc.perform(patch("/v1/api/persons/{id}/password", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"temporal-de-prueba\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_KEYCLOAK_007"));
    }
}
