package com.malphasos.malphasos.equipment.infrastructure.input.rest;

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
import com.malphasos.malphasos.equipment.application.ports.input.VerificationStandardsServicePort;
import com.malphasos.malphasos.equipment.application.services.verificationStandards.commands.AssignVerificationStandardsCommand;
import com.malphasos.malphasos.equipment.domain.exception.VerificationNotFoundException;
import com.malphasos.malphasos.equipment.domain.verificationStandards.VerificationStandards;
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

/** Contrato HTTP de los patrones de cada verificación de un tipo (V60). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EquipmentTypeStandardsRestAdapterTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private VerificationStandardsServicePort verificationStandardsServicePort;

    private final UUID tipo = UUID.randomUUID();
    private final UUID verificacion = UUID.randomUUID();
    private final UUID simulador = UUID.randomUUID();

    private String ruta() {
        return "/v1/api/equipment-types/" + tipo + "/verifications/" + verificacion + "/reference-standards";
    }

    @Test
    @DisplayName("GET da una entrada por verificacion con los identificadores de sus patrones")
    void listar() throws Exception {
        when(verificationStandardsServicePort.standardsOf(tipo))
                .thenReturn(List.of(VerificationStandards.rehydrate(verificacion, List.of(simulador))));

        mockMvc.perform(get("/v1/api/equipment-types/" + tipo + "/reference-standards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idVerificacion").value(verificacion.toString()))
                .andExpect(jsonPath("$[0].patrones[0]").value(simulador.toString()));
    }

    @Test
    @DisplayName("PATCH lleva al servicio el tipo y la verificacion de la ruta y la lista del cuerpo")
    void asignar() throws Exception {
        when(verificationStandardsServicePort.assign(any()))
                .thenReturn(VerificationStandards.rehydrate(verificacion, List.of(simulador)));

        mockMvc.perform(patch(ruta()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patrones\": [\"" + simulador + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patrones[0]").value(simulador.toString()));

        ArgumentCaptor<AssignVerificationStandardsCommand> comando =
                ArgumentCaptor.forClass(AssignVerificationStandardsCommand.class);
        verify(verificationStandardsServicePort).assign(comando.capture());
        assertThat(comando.getValue())
                .isEqualTo(new AssignVerificationStandardsCommand(tipo, verificacion, List.of(simulador)));
    }

    @Test
    @DisplayName("sin la lista responde 400 sin llegar al servicio")
    void sinLista() throws Exception {
        mockMvc.perform(patch(ruta()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verify(verificationStandardsServicePort, never()).assign(any());
    }

    @Test
    @DisplayName("una verificacion que no es del tipo responde 404 con su propio codigo")
    void verificacionAjena() throws Exception {
        when(verificationStandardsServicePort.assign(any()))
                .thenThrow(new VerificationNotFoundException(tipo, verificacion));

        mockMvc.perform(patch(ruta()).contentType(MediaType.APPLICATION_JSON).content("{\"patrones\": []}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_022"));
    }
}
