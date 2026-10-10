package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import com.malphasos.malphasos.equipment.application.model.AssignedProtocolDetail;
import com.malphasos.malphasos.equipment.application.ports.input.ProtocolServicePort;
import com.malphasos.malphasos.equipment.application.services.protocol.commands.AssignProtocolsCommand;
import com.malphasos.malphasos.equipment.domain.exception.ProtocolInUseException;
import com.malphasos.malphasos.equipment.domain.exception.ProtocolNameTakenException;
import com.malphasos.malphasos.equipment.domain.exception.ProtocolNotFoundException;
import com.malphasos.malphasos.equipment.domain.protocol.Protocol;
import com.malphasos.malphasos.equipment.domain.protocol.ProtocolAssignment;
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

/** Contrato HTTP de los protocolos y de su asignación a un tipo de equipo. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProtocolRestAdapterTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ProtocolServicePort protocolServicePort;

    private static final String CUERPO = """
            {"nombre": "Limpieza", "descripcion": "Paño humedecido."}
            """;

    @Test
    @DisplayName("POST de un protocolo válido responde 201 con lo creado")
    void crear() throws Exception {
        when(protocolServicePort.create(any())).thenReturn(Protocol.create("Limpieza", "Paño humedecido."));

        mockMvc.perform(post("/v1/api/protocols").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Limpieza"))
                .andExpect(jsonPath("$.descripcion").value("Paño humedecido."))
                .andExpect(jsonPath("$.estadoActivo").value(true));
    }

    @Test
    @DisplayName("sin descripción responde 400 sin llegar al servicio")
    void sinDescripcion() throws Exception {
        mockMvc.perform(post("/v1/api/protocols").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Limpieza\"}"))
                .andExpect(status().isBadRequest());

        verify(protocolServicePort, never()).create(any());
    }

    @Test
    @DisplayName("un nombre repetido responde 409 con su código, no 400: el dato está bien escrito")
    void nombreRepetido() throws Exception {
        when(protocolServicePort.create(any()))
                .thenThrow(new ProtocolNameTakenException("limpieza", "Limpieza"));

        mockMvc.perform(post("/v1/api/protocols").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_015"));
    }

    @Test
    @DisplayName("retirar uno en uso responde 409 con su código")
    void retirarEnUso() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ProtocolInUseException("Limpieza", 2)).when(protocolServicePort).deactivate(any());

        mockMvc.perform(delete("/v1/api/protocols/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_016"));
    }

    @Test
    @DisplayName("uno que no existe responde 404 con su código")
    void inexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(protocolServicePort.findById(id)).thenThrow(new ProtocolNotFoundException(id));

        mockMvc.perform(get("/v1/api/protocols/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_014"));
    }

    @Test
    @DisplayName("asignar manda la lista en el orden recibido y sin prioridades: las deriva el servidor")
    void asignar() throws Exception {
        UUID tipo = UUID.randomUUID();
        Protocol limpieza = Protocol.create("Limpieza", "Paño.");
        Protocol electrico = Protocol.create("Eléctrico", "Multímetro.");
        when(protocolServicePort.assign(any())).thenReturn(List.of(
                new AssignedProtocolDetail(electrico, 1, null),
                new AssignedProtocolDetail(limpieza, 2, "Con guantes")));

        mockMvc.perform(patch("/v1/api/equipment-types/" + tipo + "/protocols")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"protocolos": [
                                  {"idProtocolo": "%s"},
                                  {"idProtocolo": "%s", "indicacion": "Con guantes"}
                                ]}
                                """.formatted(electrico.getId(), limpieza.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Eléctrico"))
                .andExpect(jsonPath("$[0].prioridad").value(1))
                .andExpect(jsonPath("$[1].indicacion").value("Con guantes"));

        ArgumentCaptor<AssignProtocolsCommand> comando = ArgumentCaptor.forClass(AssignProtocolsCommand.class);
        verify(protocolServicePort).assign(comando.capture());
        assertThat(comando.getValue().idTipoEquipo()).isEqualTo(tipo);
        assertThat(comando.getValue().protocolos()).containsExactly(
                new ProtocolAssignment.Requested(electrico.getId(), null),
                new ProtocolAssignment.Requested(limpieza.getId(), "Con guantes"));
    }

    @Test
    @DisplayName("asignar sin lista responde 400: vacía quita todos, ausente es un error")
    void asignarSinLista() throws Exception {
        mockMvc.perform(patch("/v1/api/equipment-types/" + UUID.randomUUID() + "/protocols")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(protocolServicePort, never()).assign(any());
    }

    @Test
    @DisplayName("los protocolos de un tipo salen con nombre, descripción, prioridad e indicación")
    void leer() throws Exception {
        UUID tipo = UUID.randomUUID();
        when(protocolServicePort.protocolsOf(tipo)).thenReturn(List.of(
                new AssignedProtocolDetail(Protocol.create("Limpieza", "Paño."), 1, null)));

        mockMvc.perform(get("/v1/api/equipment-types/" + tipo + "/protocols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Limpieza"))
                .andExpect(jsonPath("$[0].descripcion").value("Paño."))
                .andExpect(jsonPath("$[0].prioridad").value(1));
    }
}
