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
import com.malphasos.malphasos.client.application.ports.input.ManagerServicePort;
import com.malphasos.malphasos.client.application.ports.input.ServiceAreaServicePort;
import com.malphasos.malphasos.client.application.services.headquarter.commands.CreateHeadquarterCommand;
import com.malphasos.malphasos.client.application.services.manager.commands.RegisterManagerCommand;
import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.client.domain.client.IdentificationType;
import com.malphasos.malphasos.client.domain.exception.ClientNotFoundException;
import com.malphasos.malphasos.client.domain.headquarter.Address;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.client.domain.manager.Manager;
import com.malphasos.malphasos.client.domain.manager.ManagerType;
import com.malphasos.malphasos.client.infrastructure.input.model.request.ClientCreateRequest;
import com.malphasos.malphasos.client.infrastructure.input.model.request.ContactRequest;
import com.malphasos.malphasos.client.infrastructure.input.model.request.HeadquarterCreateRequest;
import com.malphasos.malphasos.client.infrastructure.input.model.request.HeadquarterUpdateRequest;
import com.malphasos.malphasos.client.infrastructure.input.model.request.ManagerRegisterRequest;
import com.malphasos.malphasos.location.domain.exception.CityNotFoundException;
import java.util.List;
import java.util.Set;
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
    @MockitoBean private ManagerServicePort managerServicePort;

    private Client unCliente(UUID id) {
        return Client.rehydrate(id, "900123456", IdentificationType.NIT_JURIDICO,
                "Hospital Central", null, true, List.of(), List.of(), Set.of());
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
        when(clientServicePort.findById(id)).thenThrow(new ClientNotFoundException(id));

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
    @DisplayName("nombrar representante cuelga del cliente y de la persona")
    void nombrarRepresentante() throws Exception {
        UUID id = UUID.randomUUID();
        UUID persona = UUID.randomUUID();
        when(clientServicePort.appointRepresentative(any())).thenReturn(unCliente(id));

        mockMvc.perform(post("/v1/api/clients/" + id + "/representatives/" + persona))
                .andExpect(status().isCreated());
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
                .andExpect(jsonPath("$.code").value("ERR_CLIENT_005"));
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
    @DisplayName("registrar un encargado crea la persona y responde 201")
    void registrarEncargado() throws Exception {
        UUID persona = UUID.randomUUID();
        UUID sede = UUID.randomUUID();
        when(managerServicePort.register(any()))
                .thenReturn(Manager.rehydrate(persona, ManagerType.HEADQUARTER, sede, true));

        mockMvc.perform(post("/v1/api/managers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ManagerRegisterRequest(
                                "1010101010", "Grace", null, "Hopper", null,
                                ManagerType.HEADQUARTER, sede,
                                List.of(new ContactRequest("grace@cliente.com")), List.of()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPersona").value(persona.toString()))
                .andExpect(jsonPath("$.idSede").value(sede.toString()))
                .andExpect(jsonPath("$.idAreaServicio").doesNotExist());

        ArgumentCaptor<RegisterManagerCommand> comando =
                ArgumentCaptor.forClass(RegisterManagerCommand.class);
        verify(managerServicePort).register(comando.capture());

        // El tipo de persona no viaja desde la peticion: lo fija el servicio.
        assertThat(comando.getValue().persona().tipoPersona()).isNull();
        assertThat(comando.getValue().persona().cedula()).isEqualTo("1010101010");
    }

    @Test
    @DisplayName("un encargado sin correos se rechaza")
    void encargadoSinCorreos() throws Exception {
        mockMvc.perform(post("/v1/api/managers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ManagerRegisterRequest(
                                "1010101010", "Grace", null, "Hopper", null,
                                ManagerType.HEADQUARTER, UUID.randomUUID(), List.of(), List.of()))))
                .andExpect(status().isBadRequest());

        verify(managerServicePort, never()).register(any());
    }
}
