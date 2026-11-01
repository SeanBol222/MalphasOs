package com.malphasos.malphasos.client.infrastructure.input.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.client.application.ports.input.ClientServicePort;
import com.malphasos.malphasos.client.application.ports.input.HeadquarterServicePort;
import com.malphasos.malphasos.client.application.ports.input.ServiceAreaServicePort;
import com.malphasos.malphasos.client.application.services.headquarter.commands.CreateHeadquarterCommand;
import com.malphasos.malphasos.client.application.services.client.commands.AppointSupervisorCommand;
import com.malphasos.malphasos.client.application.services.client.commands.RemoveSupervisorCommand;
import com.malphasos.malphasos.client.domain.exception.SupervisorPersonTypeException;
import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.client.domain.exception.ClientAcronymTakenException;
import com.malphasos.malphasos.client.domain.client.IdentificationType;
import com.malphasos.malphasos.client.domain.exception.ClientNotFoundException;
import com.malphasos.malphasos.client.domain.headquarter.Address;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.client.infrastructure.input.model.request.ClientCreateRequest;
import com.malphasos.malphasos.client.infrastructure.input.model.request.ContactRequest;
import com.malphasos.malphasos.client.infrastructure.input.model.request.HeadquarterCreateRequest;
import com.malphasos.malphasos.client.infrastructure.input.model.request.HeadquarterUpdateRequest;
import com.malphasos.malphasos.location.domain.exception.CityNotFoundException;
import com.malphasos.malphasos.person.domain.person.PersonType;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Contrato HTTP del módulo de clientes: rutas, códigos, validación y traducción de excepciones. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ClientRestAdapterTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;

    @MockitoBean private ClientServicePort clientServicePort;
    @MockitoBean private HeadquarterServicePort headquarterServicePort;
    @MockitoBean private ServiceAreaServicePort serviceAreaServicePort;

    private Client unCliente(UUID id) {
        return Client.rehydrate(id, "900123456", IdentificationType.NIT_JURIDICO,
                "Hospital Central", "CLI", null, true, List.of(), List.of(), List.of());
    }

    @Test
    @DisplayName("POST de un cliente valido responde 201")
    void crearCliente() throws Exception {
        UUID id = UUID.randomUUID();
        when(clientServicePort.create(any())).thenReturn(unCliente(id));

        mockMvc.perform(post("/v1/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ClientCreateRequest(
                                "900123456", IdentificationType.NIT_JURIDICO, "Hospital Central", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.tipoIdentificacion").value("NIT_JURIDICO"));
    }

    @Test
    @DisplayName("la sigla tiene su ruta, y una que ya tiene otro cliente responde 409 con su codigo")
    void siglaOcupada() throws Exception {
        UUID id = UUID.randomUUID();
        when(clientServicePort.changeAcronym(any())).thenThrow(new ClientAcronymTakenException("CDN"));

        mockMvc.perform(patch("/v1/api/clients/" + id + "/acronym")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sigla\":\"cdn\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_CLIENT_008"));
    }

    @Test
    @DisplayName("una sigla con otro formato no llega al servicio")
    void siglaMalFormada() throws Exception {
        mockMvc.perform(patch("/v1/api/clients/" + UUID.randomUUID() + "/acronym")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sigla\":\"2C\"}"))
                .andExpect(status().isBadRequest());

        verify(clientServicePort, never()).changeAcronym(any());
    }

    @Test
    @DisplayName("un documento de mas de once caracteres se rechaza sin llegar al servicio")
    void documentoDemasiadoLargo() throws Exception {
        mockMvc.perform(post("/v1/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ClientCreateRequest(
                                "123456789012", IdentificationType.CC, "Hospital", null))))
                .andExpect(status().isBadRequest());

        verify(clientServicePort, never()).create(any());
    }

    @Test
    @DisplayName("un cliente inexistente responde 404 con el codigo del catalogo del modulo")
    void clienteInexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(clientServicePort.findById(id, ReadScope.unrestricted())).thenThrow(new ClientNotFoundException(id));

        mockMvc.perform(get("/v1/api/clients/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_CLIENT_001"));
    }

    @Test
    @DisplayName("los contactos cuelgan de la ruta del cliente y responden 201")
    void agregarCorreo() throws Exception {
        UUID id = UUID.randomUUID();
        when(clientServicePort.addEmail(any())).thenReturn(unCliente(id));

        mockMvc.perform(post("/v1/api/clients/" + id + "/emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ContactRequest("contacto@hospital.com"))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("nombrar supervisor cuelga del cliente y de la persona; el cargo es opcional")
    void nombrarSupervisor() throws Exception {
        UUID id = UUID.randomUUID();
        UUID persona = UUID.randomUUID();
        when(clientServicePort.appointSupervisor(any())).thenReturn(unCliente(id));

        mockMvc.perform(post("/v1/api/clients/" + id + "/supervisors/" + persona))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/api/clients/" + id + "/supervisors/" + persona)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cargo\":\"Médico propietario\"}"))
                .andExpect(status().isCreated());

        verify(clientServicePort).appointSupervisor(new AppointSupervisorCommand(id, persona, null));
        verify(clientServicePort).appointSupervisor(new AppointSupervisorCommand(id, persona, "Médico propietario"));
    }

    @Test
    @DisplayName("la respuesta del cliente lleva a sus supervisores con su cargo")
    void respuestaConSupervisores() throws Exception {
        UUID id = UUID.randomUUID();
        UUID persona = UUID.randomUUID();
        Client cliente = unCliente(id);
        cliente.appointSupervisor(persona, "Ingeniero biomédico");
        when(clientServicePort.changeSupervisorTitle(any())).thenReturn(cliente);

        mockMvc.perform(patch("/v1/api/clients/" + id + "/supervisors/" + persona)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cargo\":\"Ingeniero biomédico\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supervisores[0].idPersona").value(persona.toString()))
                .andExpect(jsonPath("$.supervisores[0].cargo").value("Ingeniero biomédico"))
                .andExpect(jsonPath("$.representantes").doesNotExist());
    }

    @Test
    @DisplayName("un cargo de mas de 80 caracteres se rechaza antes de llegar al servicio")
    void cargoDemasiadoLargo() throws Exception {
        mockMvc.perform(post("/v1/api/clients/" + UUID.randomUUID() + "/supervisors/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cargo\":\"" + "x".repeat(81) + "\"}"))
                .andExpect(status().isBadRequest());

        verify(clientServicePort, never()).appointSupervisor(any());
    }

    @Test
    @DisplayName("nombrar supervisor a quien no es SUPERVISOR es un conflicto, 409, con su codigo")
    void nombrarAQuienNoPuede() throws Exception {
        UUID persona = UUID.randomUUID();
        when(clientServicePort.appointSupervisor(any()))
                .thenThrow(new SupervisorPersonTypeException(persona, PersonType.ENGINEER));

        mockMvc.perform(post("/v1/api/clients/" + UUID.randomUUID() + "/supervisors/" + persona))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_CLIENT_011"));
    }

    @Test
    @DisplayName("retirar un supervisor cuelga del cliente y de la persona")
    void retirarSupervisor() throws Exception {
        UUID id = UUID.randomUUID();
        UUID persona = UUID.randomUUID();
        when(clientServicePort.removeSupervisor(any())).thenReturn(unCliente(id));

        mockMvc.perform(delete("/v1/api/clients/" + id + "/supervisors/" + persona))
                .andExpect(status().isOk());

        verify(clientServicePort).removeSupervisor(new RemoveSupervisorCommand(id, persona));
    }

    @Test
    @DisplayName("los encargados y los representantes ya no tienen rutas (V53)")
    void sinEncargadosNiRepresentantes() throws Exception {
        mockMvc.perform(get("/v1/api/managers")).andExpect(status().isNotFound());
        mockMvc.perform(post("/v1/api/clients/" + UUID.randomUUID() + "/representatives/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE de un cliente responde 204 y retira sin borrar")
    void retirarCliente() throws Exception {
        mockMvc.perform(delete("/v1/api/clients/" + UUID.randomUUID()))
                .andExpect(status().isNoContent());

        verify(clientServicePort).deactivate(any());
    }

    @Test
    @DisplayName("el alta de una sede toma el cliente de la ruta, no del cuerpo")
    void abrirSede() throws Exception {
        UUID cliente = UUID.randomUUID();
        UUID ciudad = UUID.randomUUID();
        when(headquarterServicePort.create(any())).thenReturn(Headquarter.rehydrate(
                UUID.randomUUID(), "Sede Norte", new Address("10", "20", "30-40"), cliente, ciudad, true));

        mockMvc.perform(post("/v1/api/clients/" + cliente + "/headquarters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new HeadquarterCreateRequest(
                                "Sede Norte", "10", "20", "30-40", ciudad))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.calle").value("10"));

        ArgumentCaptor<CreateHeadquarterCommand> comando =
                ArgumentCaptor.forClass(CreateHeadquarterCommand.class);
        verify(headquarterServicePort).create(comando.capture());

        assertThat(comando.getValue().idCliente()).isEqualTo(cliente);
    }

    @Test
    @DisplayName("una ciudad inexistente responde 404, no un conflicto de datos")
    void ciudadInexistente() throws Exception {
        UUID ciudad = UUID.randomUUID();
        when(headquarterServicePort.create(any())).thenThrow(new CityNotFoundException(ciudad));

        mockMvc.perform(post("/v1/api/clients/" + UUID.randomUUID() + "/headquarters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new HeadquarterCreateRequest(
                                "Sede", "10", "20", "30-40", ciudad))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_CLIENT_006"));
    }

    @Test
    @DisplayName("una direccion a medias se rechaza: va entera o no va")
    void direccionAMedias() throws Exception {
        mockMvc.perform(patch("/v1/api/headquarters/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new HeadquarterUpdateRequest(null, "10", null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_CLIENT_005"));

        verify(headquarterServicePort, never()).update(any());
    }

    @Test
    @DisplayName("los tres recursos aparecen en el grupo de OpenAPI del modulo")
    void recursosDocumentados() throws Exception {
        // Un patron de grupo que no case con ninguna ruta real no falla: deja el recurso fuera de
        // Swagger en silencio. /managers se habia quedado fuera por esa via, sin declararse nunca; salio
        // con los encargados en V53.
        String docs = mockMvc.perform(get("/v3/api-docs/client"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(docs)
                .describedAs("Todo recurso del modulo debe estar en su grupo de OpenAPI")
                .contains("/v1/api/clients")
                .contains("/v1/api/headquarters")
                .contains("/v1/api/service-areas")
                .contains("/v1/api/clients/{id}/supervisors/{idPersona}")
                // El resumen de sedes del rediseño, del 2026-10-11.
                .contains("/v1/api/headquarters/summary");
    }
}
