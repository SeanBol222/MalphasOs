package com.malphasos.malphasos.person.infrastructure.input.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.json.JsonMapper;
import com.malphasos.malphasos.person.application.ports.input.PersonServicePort;
import com.malphasos.malphasos.person.domain.exception.KeycloakUserAlreadyExistsException;
import com.malphasos.malphasos.person.domain.exception.PersonNotFoundException;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.domain.person.PersonType;
import com.malphasos.malphasos.person.infrastructure.input.model.request.EmailPersonCreateRequest;
import com.malphasos.malphasos.person.infrastructure.input.model.request.PersonCreateRequest;
import com.malphasos.malphasos.person.infrastructure.input.model.request.PersonRegisterRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
        when(personServicePort.registerCeoClient(any())).thenReturn(samplePerson(UUID.randomUUID()));

        for (String ruta : List.of("engineers", "admins", "ceo-clients")) {
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
    @DisplayName("crear una persona exige el tipo, que el original ni siquiera pedia")
    void createPersonRequiresPersonType() throws Exception {
        String sinTipo = """
                {"cedula":"1234567890","primerNombre":"Ada","primerApellido":"Lovelace"}
                """;

        mockMvc.perform(post("/v1/api/persons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sinTipo))
                .andExpect(status().isBadRequest());

        verify(personServicePort, never()).save(any());
    }

    @Test
    @DisplayName("crear una persona con tipo valido responde 201")
    void createPersonReturnsCreated() throws Exception {
        when(personServicePort.save(any())).thenReturn(samplePerson(UUID.randomUUID()));

        PersonCreateRequest request = PersonCreateRequest.builder()
                .cedula("1234567890")
                .primerNombre("Ada")
                .primerApellido("Lovelace")
                .tipoPersona(PersonType.MANAGER)
                .emailPersonList(List.of())
                .phonePersonList(List.of())
                .build();

        mockMvc.perform(post("/v1/api/persons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("un tipo de persona fuera del catalogo se rechaza al deserializar")
    void unknownPersonTypeIsRejected() throws Exception {
        String tipoInvalido = """
                {"cedula":"1234567890","primerNombre":"Ada","primerApellido":"Lovelace",
                 "tipoPersona":"PRESIDENTE"}
                """;

        mockMvc.perform(post("/v1/api/persons")
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
    @DisplayName("una combinacion de tipos invalida responde 400 y no 500")
    void invalidTypeCombinationReturnsBadRequest() throws Exception {
        when(personServicePort.save(any()))
                .thenThrow(new IllegalArgumentException("Una persona de tipo ENGINEER no puede tener un segundo tipo"));

        PersonCreateRequest request = PersonCreateRequest.builder()
                .cedula("1234567890")
                .primerNombre("Ada")
                .primerApellido("Lovelace")
                .tipoPersona(PersonType.ENGINEER)
                .segundoTipoPersona(PersonType.MANAGER)
                .emailPersonList(List.of())
                .phonePersonList(List.of())
                .build();

        mockMvc.perform(post("/v1/api/persons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
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
}
