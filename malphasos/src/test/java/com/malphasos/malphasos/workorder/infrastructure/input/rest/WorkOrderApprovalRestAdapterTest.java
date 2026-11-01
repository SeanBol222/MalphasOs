package com.malphasos.malphasos.workorder.infrastructure.input.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderApprovalServicePort;
import com.malphasos.malphasos.workorder.application.services.approval.commands.ApproveFromAccountCommand;
import com.malphasos.malphasos.workorder.application.services.approval.commands.ApproveOnSiteCommand;
import com.malphasos.malphasos.workorder.domain.approval.WorkOrderApproval;
import com.malphasos.malphasos.workorder.domain.exception.NoSavedSignatureException;
import com.malphasos.malphasos.workorder.domain.exception.NotSupervisorOfClientException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
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

/** Contrato HTTP de la aprobación de una orden (V54). Quién llama sale del token, nunca del cuerpo. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WorkOrderApprovalRestAdapterTest {

    private static final String RUTA = "/v1/api/work-orders/";
    private static final UUID ORDEN = UUID.randomUUID();
    private static final UUID SUPERVISOR = UUID.randomUUID();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 7};

    @Autowired private MockMvc mockMvc;

    @MockitoBean private WorkOrderApprovalServicePort servicio;

    private static WorkOrderApproval una(UUID operador) {
        return WorkOrderApproval.rehydrate(UUID.randomUUID(), ORDEN, SUPERVISOR, operador, "h".repeat(64),
                LocalDateTime.of(2026, 11, 1, 10, 30), true);
    }

    private static String cuerpo(UUID supervisor, String imagen) {
        return "{\"idSupervisor\":\"" + supervisor + "\",\"imagen\":\"" + imagen + "\"}";
    }

    @Test
    @DisplayName("GET devuelve la vigente, y 204 si la orden no está aprobada")
    void consultar() throws Exception {
        UUID ingeniero = UUID.randomUUID();
        when(servicio.findActive(any(), any())).thenReturn(Optional.of(una(ingeniero)));

        mockMvc.perform(get(RUTA + ORDEN + "/approval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSupervisor").value(SUPERVISOR.toString()))
                .andExpect(jsonPath("$.idOperador").value(ingeniero.toString()))
                .andExpect(jsonPath("$.desdeSuCuenta").value(false));

        when(servicio.findActive(any(), any())).thenReturn(Optional.empty());
        mockMvc.perform(get(RUTA + ORDEN + "/approval")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("en sitio: la firma llega como PNG en data: URI, y el operador es el sub del token")
    void enSitio() throws Exception {
        UUID ingeniero = UUID.randomUUID();
        when(servicio.approveOnSite(any(), any())).thenReturn(una(ingeniero));

        mockMvc.perform(post(RUTA + ORDEN + "/approval")
                        .with(jwt().jwt(token -> token.subject(ingeniero.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(SUPERVISOR, "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG))))
                .andExpect(status().isCreated());

        ArgumentCaptor<ApproveOnSiteCommand> comando = ArgumentCaptor.forClass(ApproveOnSiteCommand.class);
        verify(servicio).approveOnSite(comando.capture(), any());
        assertThat(comando.getValue().idOrden()).isEqualTo(ORDEN);
        assertThat(comando.getValue().idSupervisor()).isEqualTo(SUPERVISOR);
        assertThat(comando.getValue().idOperador()).isEqualTo(ingeniero);
        assertThat(comando.getValue().firma()).isEqualTo(PNG);
    }

    @Test
    @DisplayName("una firma que no es un PNG en data: URI se rechaza antes de llegar al servicio")
    void firmaMalFormada() throws Exception {
        mockMvc.perform(post(RUTA + ORDEN + "/approval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(SUPERVISOR, "data:image/jpeg;base64,AAAA")))
                .andExpect(status().isBadRequest());

        verify(servicio, never()).approveOnSite(any(), any());
    }

    @Test
    @DisplayName("desde la cuenta, quien aprueba es el sub del token: el cuerpo no puede decir otro")
    void desdeSuCuenta() throws Exception {
        when(servicio.approveFromAccount(any(), any())).thenReturn(una(SUPERVISOR));

        mockMvc.perform(post(RUTA + ORDEN + "/approval/mine")
                        .with(jwt().jwt(token -> token.subject(SUPERVISOR.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.desdeSuCuenta").value(true));

        verify(servicio).approveFromAccount(eq(new ApproveFromAccountCommand(ORDEN, SUPERVISOR)), any());
    }

    @Test
    @DisplayName("una sesión que no es de una persona no aprueba por sí misma")
    void sinPersona() throws Exception {
        mockMvc.perform(post(RUTA + ORDEN + "/approval/mine")
                        .with(jwt().jwt(token -> token.subject("operador-de-keycloak"))))
                .andExpect(status().isBadRequest());

        verify(servicio, never()).approveFromAccount(any(), any());
    }

    @Test
    @DisplayName("quien no supervisa y quien no guardó su firma son conflictos, con su propio código")
    void conflictos() throws Exception {
        when(servicio.approveFromAccount(any(), any())).thenThrow(new NoSavedSignatureException(SUPERVISOR));
        mockMvc.perform(post(RUTA + ORDEN + "/approval/mine")
                        .with(jwt().jwt(token -> token.subject(SUPERVISOR.toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_WORK_ORDER_013"));

        doThrow(new NotSupervisorOfClientException(SUPERVISOR, ORDEN)).when(servicio).approveOnSite(any(), any());
        mockMvc.perform(post(RUTA + ORDEN + "/approval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(SUPERVISOR, "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_WORK_ORDER_012"));

        // doThrow y no when(): volver a llamar al doble para re-estubarlo lanzaría la excepción anterior.
        doThrow(new IllegalStateException("ya está aprobada")).when(servicio).approveOnSite(any(), any());
        mockMvc.perform(post(RUTA + ORDEN + "/approval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(SUPERVISOR, "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_WORK_ORDER_003"));
    }

    @Test
    @DisplayName("las órdenes por aprobar tienen ruta propia, que no se confunde con /{id}")
    void porAprobar() throws Exception {
        when(servicio.pending(any())).thenReturn(List.of());

        mockMvc.perform(get(RUTA + "pending-approval")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("las cuatro rutas están en el grupo de OpenAPI del módulo")
    void documentadas() throws Exception {
        String docs = mockMvc.perform(get("/v3/api-docs/work-order")).andReturn().getResponse().getContentAsString();

        assertThat(docs)
                .contains("/v1/api/work-orders/{id}/approval")
                .contains("/v1/api/work-orders/{id}/approval/mine")
                .contains("/v1/api/work-orders/pending-approval");
    }
}
