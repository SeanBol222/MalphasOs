package com.malphasos.malphasos.report.infrastructure.input.rest;

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
import com.malphasos.malphasos.equipment.domain.exception.EquipmentTypeNotFoundException;
import com.malphasos.malphasos.report.application.ports.input.ServiceReportServicePort;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FillServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.OpenServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordVerificationCommand;
import com.malphasos.malphasos.report.domain.exception.ServiceReportNotFoundException;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationReading;
import com.malphasos.malphasos.report.infrastructure.input.model.request.RecordVerificationRequest;
import com.malphasos.malphasos.report.infrastructure.input.model.request.ServiceReportFillRequest;
import com.malphasos.malphasos.report.infrastructure.input.model.request.ServiceReportOpenRequest;
import com.malphasos.malphasos.report.infrastructure.input.model.request.VerificationReadingRequest;
import java.math.BigDecimal;
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

/** Contrato HTTP de los reportes de servicio. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ServiceReportRestAdapterTest {

    private static final UUID ORDEN = UUID.randomUUID();
    private static final UUID EQUIPO = UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;

    @MockitoBean private ServiceReportServicePort serviceReportServicePort;

    private static ServiceReport unReporte() {
        return ServiceReport.open(ORDEN, EQUIPO);
    }

    @Test
    @DisplayName("POST abre el reporte y responde 201 con el borrador vacio")
    void abrir() throws Exception {
        when(serviceReportServicePort.open(any())).thenReturn(unReporte());

        mockMvc.perform(post("/v1/api/reports/work-orders/" + ORDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new ServiceReportOpenRequest(EQUIPO))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.resultado").isEmpty())
                .andExpect(jsonPath("$.finalizado").isEmpty())
                .andExpect(jsonPath("$.lecturas").isEmpty());
    }

    @Test
    @DisplayName("la orden sale de la ruta y el equipo del cuerpo")
    void laOrdenViajaEnLaRuta() throws Exception {
        when(serviceReportServicePort.open(any())).thenReturn(unReporte());

        mockMvc.perform(post("/v1/api/reports/work-orders/" + ORDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new ServiceReportOpenRequest(EQUIPO))))
                .andExpect(status().isCreated());

        ArgumentCaptor<OpenServiceReportCommand> comando =
                ArgumentCaptor.forClass(OpenServiceReportCommand.class);
        verify(serviceReportServicePort).open(comando.capture());

        assertThat(comando.getValue().idOrdenTrabajo()).isEqualTo(ORDEN);
        assertThat(comando.getValue().idEquipoCliente()).isEqualTo(EQUIPO);
    }

    @Test
    @DisplayName("abrir sin equipo se rechaza sin llegar al servicio")
    void sinEquipo() throws Exception {
        mockMvc.perform(post("/v1/api/reports/work-orders/" + ORDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new ServiceReportOpenRequest(null))))
                .andExpect(status().isBadRequest());

        verify(serviceReportServicePort, never()).open(any());
    }

    @Test
    @DisplayName("PATCH llena los cinco campos y los pasa tal cual al servicio")
    void llenar() throws Exception {
        ServiceReport reporte = unReporte();
        reporte.fill("No enciende", "Fuente quemada", "Cambio de fuente", "En prueba",
                ServiceResult.OPERATIVO_CON_RESTRICCIONES);
        when(serviceReportServicePort.fill(any())).thenReturn(reporte);

        mockMvc.perform(patch("/v1/api/reports/" + reporte.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ServiceReportFillRequest(
                                "No enciende", "Fuente quemada", "Cambio de fuente", "En prueba",
                                ServiceResult.OPERATIVO_CON_RESTRICCIONES))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallaReportada").value("No enciende"))
                .andExpect(jsonPath("$.resultado").value("OPERATIVO_CON_RESTRICCIONES"));

        ArgumentCaptor<FillServiceReportCommand> comando =
                ArgumentCaptor.forClass(FillServiceReportCommand.class);
        verify(serviceReportServicePort).fill(comando.capture());
        assertThat(comando.getValue().procedimientos()).isEqualTo("Cambio de fuente");
    }

    @Test
    @DisplayName("un cuerpo vacio al llenar es valido: ningun campo es obligatorio")
    void llenarConCuerpoVacio() throws Exception {
        // El reporte se llena por partes mientras se trabaja. Lo que hace falta para cerrarlo se
        // exige al cerrarlo, no aqui.
        when(serviceReportServicePort.fill(any())).thenReturn(unReporte());

        mockMvc.perform(patch("/v1/api/reports/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH de la verificacion traslada cada lectura al comando")
    void registrarVerificacion() throws Exception {
        UUID punto = UUID.randomUUID();
        ServiceReport reporte = unReporte();
        reporte.recordVerification(List.of(VerificationReading.of(
                punto, 1, new BigDecimal("50"), new BigDecimal("50.2"), "mmHg")));
        when(serviceReportServicePort.recordVerification(any())).thenReturn(reporte);

        mockMvc.perform(patch("/v1/api/reports/" + reporte.getId() + "/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RecordVerificationRequest(
                                List.of(new VerificationReadingRequest(
                                        punto, 1, new BigDecimal("50"), new BigDecimal("50.2"), null))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecturas[0].idPuntoVerificacion").value(punto.toString()))
                .andExpect(jsonPath("$.lecturas[0].unidad").value("mmHg"));

        ArgumentCaptor<RecordVerificationCommand> comando =
                ArgumentCaptor.forClass(RecordVerificationCommand.class);
        verify(serviceReportServicePort).recordVerification(comando.capture());
        assertThat(comando.getValue().lecturas()).singleElement()
                .extracting(lectura -> lectura.idPuntoVerificacion())
                .isEqualTo(punto);
    }

    @Test
    @DisplayName("una verificacion sin lecturas se rechaza sin llegar al servicio")
    void verificacionVacia() throws Exception {
        mockMvc.perform(patch("/v1/api/reports/" + UUID.randomUUID() + "/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new RecordVerificationRequest(List.of()))))
                .andExpect(status().isBadRequest());

        verify(serviceReportServicePort, never()).recordVerification(any());
    }

    @Test
    @DisplayName("una lectura numerada por encima de cien se rechaza en la peticion")
    void lecturaFueraDeRango() throws Exception {
        mockMvc.perform(patch("/v1/api/reports/" + UUID.randomUUID() + "/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RecordVerificationRequest(
                                List.of(new VerificationReadingRequest(
                                        UUID.randomUUID(), 101, BigDecimal.ONE, BigDecimal.TWO, null))))))
                .andExpect(status().isBadRequest());

        verify(serviceReportServicePort, never()).recordVerification(any());
    }

    @Test
    @DisplayName("cerrar es una operacion, no un campo que se edita")
    void cerrar() throws Exception {
        ServiceReport reporte = unReporte();
        reporte.fill(null, null, "Limpieza", null, ServiceResult.OPERATIVO);
        reporte.finish();
        when(serviceReportServicePort.finish(any())).thenReturn(reporte);

        mockMvc.perform(patch("/v1/api/reports/" + reporte.getId() + "/finish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADO"))
                .andExpect(jsonPath("$.finalizado").isNotEmpty());
    }

    @Test
    @DisplayName("el estado no se puede escribir en el cuerpo: no hay campo para el")
    void elEstadoNoSeEscribe() {
        assertThat(ServiceReportFillRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("fallaReportada", "diagnostico", "procedimientos", "observaciones",
                        "resultado");
    }

    @Test
    @DisplayName("DELETE retira el reporte y responde 204")
    void retirar() throws Exception {
        mockMvc.perform(delete("/v1/api/reports/" + UUID.randomUUID()))
                .andExpect(status().isNoContent());

        verify(serviceReportServicePort).discard(any());
    }

    @Test
    @DisplayName("listar sin filtro se rechaza: un reporte no se consulta suelto")
    void listarSinFiltro() throws Exception {
        mockMvc.perform(get("/v1/api/reports"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_SERVICE_REPORT_002"));
    }

    @Test
    @DisplayName("listar con los dos filtros a la vez tambien se rechaza")
    void listarConLosDosFiltros() throws Exception {
        mockMvc.perform(get("/v1/api/reports")
                        .param("idOrdenTrabajo", ORDEN.toString())
                        .param("idEquipoCliente", EQUIPO.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("listar por orden devuelve sus reportes")
    void listarPorOrden() throws Exception {
        when(serviceReportServicePort.findByWorkOrder(ORDEN)).thenReturn(List.of(unReporte()));

        mockMvc.perform(get("/v1/api/reports").param("idOrdenTrabajo", ORDEN.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idOrdenTrabajo").value(ORDEN.toString()));
    }

    @Test
    @DisplayName("listar por equipo devuelve su historial")
    void listarPorEquipo() throws Exception {
        when(serviceReportServicePort.findByEquipment(EQUIPO)).thenReturn(List.of(unReporte()));

        mockMvc.perform(get("/v1/api/reports").param("idEquipoCliente", EQUIPO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idEquipoCliente").value(EQUIPO.toString()));
    }

    @Test
    @DisplayName("un reporte que no existe da 404 con el codigo del modulo")
    void noExiste() throws Exception {
        UUID id = UUID.randomUUID();
        when(serviceReportServicePort.findById(id)).thenThrow(new ServiceReportNotFoundException(id));

        mockMvc.perform(get("/v1/api/reports/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_SERVICE_REPORT_001"));
    }

    @Test
    @DisplayName("el momento equivocado da 409, no 400")
    void conflictoDeEstado() throws Exception {
        UUID id = UUID.randomUUID();
        when(serviceReportServicePort.finish(any()))
                .thenThrow(new IllegalStateException("La verificacion esta a medias"));

        mockMvc.perform(patch("/v1/api/reports/" + id + "/finish"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_SERVICE_REPORT_003"));
    }

    @Test
    @DisplayName("un eslabon roto del catalogo da 404 con codigo propio, no un 500")
    void eslabonDelCatalogoRoto() throws Exception {
        // El llamante nunca nombra el tipo de equipo: lo recorre el servidor para saber como se
        // verifica. Sin declararlo en el advice, un tipo que no aparece saldria como 500.
        UUID id = UUID.randomUUID();
        when(serviceReportServicePort.recordVerification(any()))
                .thenThrow(new EquipmentTypeNotFoundException(UUID.randomUUID()));

        mockMvc.perform(patch("/v1/api/reports/" + id + "/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RecordVerificationRequest(
                                List.of(new VerificationReadingRequest(
                                        null, 1, BigDecimal.ONE, BigDecimal.TWO, "mA"))))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_SERVICE_REPORT_008"));
    }

    @Test
    @DisplayName("el recurso aparece en el grupo de OpenAPI del modulo, que llevaba vacio desde que se declaro")
    void recursoDocumentado() throws Exception {
        // El grupo `reports` existia apuntando a /v1/api/reports/** sin que ninguna ruta casara: un
        // patron que no casa no falla ni avisa, deja el grupo vacio en silencio. Estaba anotado como
        // deuda propia del proyecto y se cierra con esta prueba, escrita a la vez que las rutas.
        String docs = mockMvc.perform(get("/v3/api-docs/reports"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(docs).contains("/v1/api/reports");
        assertThat(docs).contains("/v1/api/reports/{id}/verification");
        assertThat(docs).contains("/v1/api/reports/{id}/finish");
        assertThat(docs).contains("/v1/api/reports/work-orders/{idOrdenTrabajo}");
    }

    @Test
    @DisplayName("el reporte no ofrece ninguna ruta para reabrirlo")
    void nadaReabreUnReporte() throws Exception {
        mockMvc.perform(patch("/v1/api/reports/" + UUID.randomUUID() + "/reopen"))
                .andExpect(status().isNotFound());
    }
}
