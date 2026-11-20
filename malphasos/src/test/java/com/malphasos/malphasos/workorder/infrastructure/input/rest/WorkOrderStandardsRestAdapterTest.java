package com.malphasos.malphasos.workorder.infrastructure.input.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.client.infrastructure.input.security.ReadScopeResolver;
import com.malphasos.malphasos.equipment.domain.exception.ReferenceStandardNotFoundException;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderStandardsServicePort;
import com.malphasos.malphasos.workorder.application.services.workOrderStandards.commands.AssignWorkOrderStandardsCommand;
import com.malphasos.malphasos.workorder.domain.workOrderStandards.WorkOrderStandards;
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

/** Contrato HTTP de los patrones de una orden por tipo de equipo (V60). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WorkOrderStandardsRestAdapterTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private WorkOrderStandardsServicePort workOrderStandardsServicePort;
    @MockitoBean private ReadScopeResolver readScopeResolver;

    private final UUID orden = UUID.randomUUID();
    private final UUID tipo = UUID.randomUUID();
    private final UUID simulador = UUID.randomUUID();

    private String ruta() {
        return "/v1/api/work-orders/" + orden + "/equipment-types/" + tipo + "/reference-standards";
    }

    @Test
    @DisplayName("GET da una entrada por tipo con los identificadores de sus patrones, con el alcance de quien pide")
    void listar() throws Exception {
        when(readScopeResolver.scopeFor(any())).thenReturn(ReadScope.unrestricted());
        when(workOrderStandardsServicePort.standardsOf(orden, ReadScope.unrestricted()))
                .thenReturn(List.of(WorkOrderStandards.rehydrate(orden, tipo, List.of(simulador))));

        mockMvc.perform(get("/v1/api/work-orders/" + orden + "/reference-standards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idTipoEquipo").value(tipo.toString()))
                .andExpect(jsonPath("$[0].patrones[0]").value(simulador.toString()));
    }

    @Test
    @DisplayName("PATCH lleva al servicio la orden y el tipo de la ruta y la lista del cuerpo")
    void asignar() throws Exception {
        when(workOrderStandardsServicePort.assign(any()))
                .thenReturn(WorkOrderStandards.rehydrate(orden, tipo, List.of(simulador)));

        mockMvc.perform(patch(ruta()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patrones\": [\"" + simulador + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idTipoEquipo").value(tipo.toString()))
                .andExpect(jsonPath("$.patrones[0]").value(simulador.toString()));

        ArgumentCaptor<AssignWorkOrderStandardsCommand> comando =
                ArgumentCaptor.forClass(AssignWorkOrderStandardsCommand.class);
        verify(workOrderStandardsServicePort).assign(comando.capture());
        assertThat(comando.getValue()).isEqualTo(new AssignWorkOrderStandardsCommand(orden, tipo, List.of(simulador)));
    }

    @Test
    @DisplayName("sin la lista responde 400 sin llegar al servicio")
    void sinLista() throws Exception {
        mockMvc.perform(patch(ruta()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verify(workOrderStandardsServicePort, never()).assign(any());
    }

    @Test
    @DisplayName("un patron que no existe responde 404 con su propio codigo, y una orden ejecutada 409")
    void errores() throws Exception {
        when(workOrderStandardsServicePort.assign(any()))
                .thenThrow(new ReferenceStandardNotFoundException(simulador))
                .thenThrow(new IllegalStateException("No se puede cambiar los patrones en una orden ya ejecutada"));

        mockMvc.perform(patch(ruta()).contentType(MediaType.APPLICATION_JSON).content("{\"patrones\": []}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_WORK_ORDER_014"));
        mockMvc.perform(patch(ruta()).contentType(MediaType.APPLICATION_JSON).content("{\"patrones\": []}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_WORK_ORDER_003"));
    }
}
