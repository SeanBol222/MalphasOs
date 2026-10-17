package com.malphasos.malphasos.person.infrastructure.input.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.person.application.ports.input.PersonSignatureServicePort;
import com.malphasos.malphasos.person.domain.person.PersonSignature;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
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

/** La firma de quien llama (V38, 2026-10-17): la persona sale del token, nunca de la ruta. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PersonSignatureRestAdapterTest {

    private static final String RUTA = "/v1/api/persons/me/signature";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 7};
    private static final String DATA_URI = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG);

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PersonSignatureServicePort servicio;

    @Test
    @DisplayName("GET devuelve la firma de quien llama, como data: URI")
    void miFirma() throws Exception {
        UUID yo = UUID.randomUUID();
        when(servicio.find(yo)).thenReturn(Optional.of(new PersonSignature(PNG, LocalDateTime.of(2026, 10, 17, 9, 0))));

        mockMvc.perform(get(RUTA).with(jwt().jwt(token -> token.subject(yo.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagen").value(DATA_URI));
    }

    @Test
    @DisplayName("sin firma guardada responde 404 con su código")
    void sinFirma() throws Exception {
        UUID yo = UUID.randomUUID();
        when(servicio.find(yo)).thenReturn(Optional.empty());

        mockMvc.perform(get(RUTA).with(jwt().jwt(token -> token.subject(yo.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_PERSON_003"));
    }

    @Test
    @DisplayName("PATCH guarda los bytes del PNG para quien llama, y solo para él")
    void guardar() throws Exception {
        UUID yo = UUID.randomUUID();
        when(servicio.save(eq(yo), any())).thenReturn(new PersonSignature(PNG, LocalDateTime.of(2026, 10, 17, 9, 0)));

        mockMvc.perform(patch(RUTA).with(jwt().jwt(token -> token.subject(yo.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imagen\": \"" + DATA_URI + "\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(servicio).save(eq(yo), bytes.capture());
        assertThat(bytes.getValue()).isEqualTo(PNG);
    }

    @Test
    @DisplayName("lo que no llega como PNG en data: URI responde 400 sin llegar al servicio")
    void noPng() throws Exception {
        mockMvc.perform(patch(RUTA).with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        // Mismo largo de prefijo que el PNG y un base64 válido detrás: si no se mirara el
                        // prefijo, esto llegaría al servicio. Con otro prefijo fallaba por el base64.
                        .content("{\"imagen\": \"data:image/gif;base64,R0lGODlh\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_PERSON_002"));

        verify(servicio, never()).save(any(), any());
    }

    @Test
    @DisplayName("una sesión que no es de una persona registrada no tiene firma que leer ni guardar")
    void sinPersona() throws Exception {
        mockMvc.perform(get(RUTA).with(jwt().jwt(token -> token.subject("operador-de-keycloak"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_PERSON_001"));

        verify(servicio, never()).find(any());
    }
}
