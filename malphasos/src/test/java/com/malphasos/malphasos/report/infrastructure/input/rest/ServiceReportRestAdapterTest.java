package com.malphasos.malphasos.report.infrastructure.input.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.equipment.domain.exception.EquipmentTypeNotFoundException;
import com.malphasos.malphasos.report.UnDocumento;
import com.malphasos.malphasos.report.application.model.ReportDocumentView;
import com.malphasos.malphasos.report.application.model.ReportPdf;
import com.malphasos.malphasos.report.application.ports.output.ReportPdfUnavailableException;
import com.malphasos.malphasos.report.application.ports.input.ServiceReportServicePort;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.FillServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.ReplaceServiceReportCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordHandoverCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordIntakeCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RecordVerificationCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.RemoveReferenceStandardCommand;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.SignReceiptCommand;
import com.malphasos.malphasos.report.domain.exception.ServiceReportNotFoundException;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import com.malphasos.malphasos.report.domain.serviceReport.Handover;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeCheck;
import com.malphasos.malphasos.report.domain.serviceReport.IntakeState;
import com.malphasos.malphasos.report.domain.serviceReport.ReportAccessory;
import com.malphasos.malphasos.report.domain.serviceReport.ReportDocument;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.MarkReportStepCommand;
import com.malphasos.malphasos.report.domain.serviceReport.StepState;
import com.malphasos.malphasos.report.domain.serviceReport.ReportStep;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceResult;
import com.malphasos.malphasos.report.domain.serviceReport.VerificationReading;
import com.malphasos.malphasos.report.infrastructure.input.model.request.RecordVerificationRequest;
import com.malphasos.malphasos.report.infrastructure.input.model.request.ServiceReportFillRequest;
import com.malphasos.malphasos.report.infrastructure.input.model.request.UseReferenceStandardRequest;
import com.malphasos.malphasos.report.infrastructure.input.model.request.VerificationReadingRequest;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.UseReferenceStandardCommand;
import com.malphasos.malphasos.report.domain.serviceReport.UsedReferenceStandard;
import com.malphasos.malphasos.report.application.model.WorkOrderReportProgress;
import com.malphasos.malphasos.equipment.domain.exception.ReferenceStandardNotFoundException;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
    @DisplayName("sustituir responde 201 con el borrador nuevo, y el reporte sale de la ruta (V50)")
    void sustituir() throws Exception {
        ServiceReport borrador = unReporte();
        UUID cerrado = UUID.randomUUID();
        when(serviceReportServicePort.replace(any())).thenReturn(borrador);

        mockMvc.perform(post("/v1/api/reports/" + cerrado + "/replace"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(borrador.numero()))
                .andExpect(jsonPath("$.estado").value("BORRADOR"));

        ArgumentCaptor<ReplaceServiceReportCommand> comando =
                ArgumentCaptor.forClass(ReplaceServiceReportCommand.class);
        verify(serviceReportServicePort).replace(comando.capture());
        assertThat(comando.getValue().id()).isEqualTo(cerrado);
    }

    @Test
    @DisplayName("un reporte ya no se abre por su cuenta: nace al anadir el equipo a la orden (V50)")
    void noSeAbrePorSuCuenta() throws Exception {
        mockMvc.perform(post("/v1/api/reports/work-orders/" + ORDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idEquipoCliente\":\"" + EQUIPO + "\"}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
    }

    @Test
    @DisplayName("PATCH llena los cinco campos y los pasa tal cual al servicio")
    void llenar() throws Exception {
        ServiceReport reporte = unReporte();
        reporte.fill("No enciende", "Fuente quemada", "Cambio de fuente", "En prueba",
                "Sobretension", "Fuente 12 V", ServiceResult.OPERATIVO_CON_RESTRICCIONES);
        when(serviceReportServicePort.fill(any())).thenReturn(reporte);

        mockMvc.perform(patch("/v1/api/reports/" + reporte.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ServiceReportFillRequest(
                                "No enciende", "Fuente quemada", "Cambio de fuente", "En prueba",
                                "Sobretension", "Fuente 12 V",
                                ServiceResult.OPERATIVO_CON_RESTRICCIONES))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallaReportada").value("No enciende"))
                .andExpect(jsonPath("$.causa").value("Sobretension"))
                .andExpect(jsonPath("$.repuestos").value("Fuente 12 V"))
                .andExpect(jsonPath("$.resultado").value("OPERATIVO_CON_RESTRICCIONES"));

        ArgumentCaptor<FillServiceReportCommand> comando =
                ArgumentCaptor.forClass(FillServiceReportCommand.class);
        verify(serviceReportServicePort).fill(comando.capture());
        assertThat(comando.getValue().procedimientos()).isEqualTo("Cambio de fuente");
        // La causa y los repuestos del correctivo, de V24: llegan al comando con su nombre.
        assertThat(comando.getValue().causa()).isEqualTo("Sobretension");
        assertThat(comando.getValue().repuestos()).isEqualTo("Fuente 12 V");
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
        UUID verificacion = UUID.randomUUID();
        ServiceReport reporte = unReporte();
        reporte.recordVerification(List.of(VerificationReading.of(
                verificacion, punto, 1, new BigDecimal("50"), new BigDecimal("50.2"), "mmHg")));
        when(serviceReportServicePort.recordVerification(any())).thenReturn(reporte);

        mockMvc.perform(patch("/v1/api/reports/" + reporte.getId() + "/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new RecordVerificationRequest(
                                List.of(new VerificationReadingRequest(
                                        verificacion, punto, 1,
                                        new BigDecimal("50"), new BigDecimal("50.2")))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecturas[0].idVerificacion").value(verificacion.toString()))
                .andExpect(jsonPath("$.lecturas[0].idPuntoVerificacion").value(punto.toString()))
                .andExpect(jsonPath("$.lecturas[0].unidad").value("mmHg"));

        ArgumentCaptor<RecordVerificationCommand> comando =
                ArgumentCaptor.forClass(RecordVerificationCommand.class);
        verify(serviceReportServicePort).recordVerification(comando.capture());
        assertThat(comando.getValue().lecturas()).singleElement()
                .extracting(lectura -> lectura.idPuntoVerificacion())
                .isEqualTo(punto);
    }

    private static String rutaDePatrones(UUID reporte, UUID verificacion) {
        return "/v1/api/reports/" + reporte + "/verifications/" + verificacion + "/reference-standards";
    }

    @Test
    @DisplayName("poner un patron lleva reporte, verificacion y patron al servicio, y la respuesta trae lo copiado")
    void ponerPatron() throws Exception {
        UUID patron = UUID.randomUUID();
        UUID verificacion = UUID.randomUUID();
        ServiceReport reporte = unReporte();
        reporte.useReferenceStandard(verificacion, new UsedReferenceStandard(patron, "Juego de pesas", null,
                "KF1-1", "CAL-2026-0412", LocalDate.of(2027, 4, 30), "MS-F1", "BB-PAT-003", "1 mg a 2 kg",
                "0,1 mg", LocalDate.of(2026, 4, 30), "Metrologia Andina"));
        when(serviceReportServicePort.useReferenceStandard(any())).thenReturn(reporte);

        mockMvc.perform(post(rutaDePatrones(reporte.getId(), verificacion))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new UseReferenceStandardRequest(patron))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patrones[0].idVerificacion").value(verificacion.toString()))
                .andExpect(jsonPath("$.patrones[0].id").value(patron.toString()))
                .andExpect(jsonPath("$.patrones[0].certificado").value("CAL-2026-0412"))
                .andExpect(jsonPath("$.patrones[0].vencimiento").value("2027-04-30"))
                .andExpect(jsonPath("$.patrones[0].codigoInterno").value("BB-PAT-003"))
                .andExpect(jsonPath("$.patrones[0].fechaCalibracion").value("2026-04-30"))
                .andExpect(jsonPath("$.patrones[0].empresaCalibradora").value("Metrologia Andina"));

        ArgumentCaptor<UseReferenceStandardCommand> comando =
                ArgumentCaptor.forClass(UseReferenceStandardCommand.class);
        verify(serviceReportServicePort).useReferenceStandard(comando.capture());
        assertThat(comando.getValue())
                .isEqualTo(new UseReferenceStandardCommand(reporte.getId(), verificacion, patron));
    }

    @Test
    @DisplayName("la respuesta solo trae los patrones que siguen puestos")
    void soloLosPuestos() throws Exception {
        UUID verificacion = UUID.randomUUID();
        UsedReferenceStandard quitado = new UsedReferenceStandard(UUID.randomUUID(), "Viejo", null, "V-1", "C",
                LocalDate.of(2027, 1, 1));
        ServiceReport reporte = unReporte();
        reporte.useReferenceStandard(verificacion, quitado);
        reporte.removeReferenceStandard(verificacion, quitado.id());
        when(serviceReportServicePort.findById(any(), any())).thenReturn(reporte);

        mockMvc.perform(get("/v1/api/reports/" + reporte.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patrones").isEmpty());
    }

    @Test
    @DisplayName("poner sin decir cual responde 400 sin llegar al servicio")
    void ponerPatronSinPatron() throws Exception {
        mockMvc.perform(post(rutaDePatrones(UUID.randomUUID(), UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(serviceReportServicePort, never()).useReferenceStandard(any());
    }

    @Test
    @DisplayName("un patron que no existe da 404 con codigo propio, no un 500")
    void patronQueNoExiste() throws Exception {
        UUID patron = UUID.randomUUID();
        when(serviceReportServicePort.useReferenceStandard(any()))
                .thenThrow(new ReferenceStandardNotFoundException(patron));

        mockMvc.perform(post(rutaDePatrones(UUID.randomUUID(), UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new UseReferenceStandardRequest(patron))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_SERVICE_REPORT_009"));
    }

    @Test
    @DisplayName("quitar un patron responde 204 y lleva los tres identificadores de la ruta")
    void quitarPatron() throws Exception {
        UUID reporte = UUID.randomUUID();
        UUID verificacion = UUID.randomUUID();
        UUID patron = UUID.randomUUID();

        mockMvc.perform(delete(rutaDePatrones(reporte, verificacion) + "/" + patron))
                .andExpect(status().isNoContent());

        verify(serviceReportServicePort).removeReferenceStandard(
                new RemoveReferenceStandardCommand(reporte, verificacion, patron));
    }

    @Test
    @DisplayName("registrar el ingreso lleva al servicio las dos verificaciones y los accesorios en su orden")
    void registrarIngreso() throws Exception {
        ServiceReport reporte = unReporte();
        reporte.recordIntake(new IntakeCheck(IntakeState.NO_CONFORME, "Rayado"),
                new IntakeCheck(IntakeState.CONFORME, null), List.of(ReportAccessory.of(1, "Sonda", true, null)));
        when(serviceReportServicePort.recordIntake(any())).thenReturn(reporte);

        mockMvc.perform(patch("/v1/api/reports/" + reporte.getId() + "/intake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fisico": "NO_CONFORME", "observacionFisico": "Rayado", "operativo": "CONFORME",
                                 "accesorios": [{"nombre": "Sonda", "conforme": true},
                                                {"nombre": "Cable", "conforme": false, "observacion": "Pelado"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingresoFisico.estado").value("NO_CONFORME"))
                .andExpect(jsonPath("$.ingresoFisico.observacion").value("Rayado"))
                .andExpect(jsonPath("$.ingresoOperativo.estado").value("CONFORME"))
                .andExpect(jsonPath("$.accesorios[0].orden").value(1))
                .andExpect(jsonPath("$.accesorios[0].nombre").value("Sonda"));

        ArgumentCaptor<RecordIntakeCommand> comando = ArgumentCaptor.forClass(RecordIntakeCommand.class);
        verify(serviceReportServicePort).recordIntake(comando.capture());
        assertThat(comando.getValue()).isEqualTo(new RecordIntakeCommand(reporte.getId(), IntakeState.NO_CONFORME,
                "Rayado", IntakeState.CONFORME, null, List.of(
                        new RecordIntakeCommand.Accessory("Sonda", true, null),
                        new RecordIntakeCommand.Accessory("Cable", false, "Pelado"))));
    }

    @Test
    @DisplayName("el ingreso sin una de las dos verificaciones, o con un accesorio sin nombre, es 400")
    void ingresoIncompleto() throws Exception {
        mockMvc.perform(patch("/v1/api/reports/" + UUID.randomUUID() + "/intake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fisico\": \"CONFORME\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/v1/api/reports/" + UUID.randomUUID() + "/intake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fisico\": \"CONFORME\", \"operativo\": \"CONFORME\","
                                + " \"accesorios\": [{\"nombre\": \" \", \"conforme\": true}]}"))
                .andExpect(status().isBadRequest());

        verify(serviceReportServicePort, never()).recordIntake(any());
    }

    @Test
    @DisplayName("quien recibe llega al servicio y vuelve en la respuesta")
    void registrarQuienRecibe() throws Exception {
        ServiceReport reporte = unReporte();
        reporte.recordHandover(new Handover("Ana Pérez", "52123456", "Jefe de enfermeria"));
        when(serviceReportServicePort.recordHandover(any())).thenReturn(reporte);

        mockMvc.perform(patch("/v1/api/reports/" + reporte.getId() + "/handover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Ana Pérez\", \"cedula\": \"52123456\","
                                + " \"cargo\": \"Jefe de enfermeria\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recibe.nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.recibe.cargo").value("Jefe de enfermeria"));

        verify(serviceReportServicePort).recordHandover(
                new RecordHandoverCommand(reporte.getId(), "Ana Pérez", "52123456", "Jefe de enfermeria"));
    }

    @Nested
    @DisplayName("La firma de quien recibe (V39)")
    class FirmaDeQuienRecibe {

        /** Un PNG mínimo: la cabecera de ocho bytes y uno más. */
        private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 7};

        private String cuerpo(String imagen, boolean aplicarALaOrden) {
            return "{\"nombre\": \"Ana Pérez\", \"cedula\": \"52123456\", \"cargo\": \"Jefe de enfermeria\","
                    + " \"imagen\": \"" + imagen + "\", \"aplicarALaOrden\": " + aplicarALaOrden + "}";
        }

        @Test
        @DisplayName("firmar llega al servicio con los bytes del PNG y la casilla, y devuelve los firmados")
        void firmar() throws Exception {
            ServiceReport reporte = unReporte();
            ServiceReport otro = unReporte();
            Handover recibe = new Handover("Ana Pérez", "52123456", "Jefe de enfermeria");
            reporte.signReceipt(recibe, "a".repeat(64), java.time.LocalDateTime.of(2026, 10, 17, 9, 30));
            otro.signReceipt(recibe, "a".repeat(64), java.time.LocalDateTime.of(2026, 10, 17, 9, 30));
            when(serviceReportServicePort.signReceipt(any())).thenReturn(List.of(reporte, otro));

            mockMvc.perform(patch("/v1/api/reports/" + reporte.getId() + "/receipt-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("data:image/png;base64," + Base64.getEncoder().encodeToString(PNG),
                                    true)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(reporte.getId().toString()))
                    .andExpect(jsonPath("$[0].recibe.nombre").value("Ana Pérez"))
                    .andExpect(jsonPath("$[0].firmadoRecibe").value("2026-10-17T09:30:00"))
                    .andExpect(jsonPath("$[1].id").value(otro.getId().toString()));

            ArgumentCaptor<SignReceiptCommand> comando = ArgumentCaptor.forClass(SignReceiptCommand.class);
            verify(serviceReportServicePort).signReceipt(comando.capture());
            assertThat(comando.getValue().id()).isEqualTo(reporte.getId());
            assertThat(comando.getValue().nombre()).isEqualTo("Ana Pérez");
            assertThat(comando.getValue().cedula()).isEqualTo("52123456");
            assertThat(comando.getValue().cargo()).isEqualTo("Jefe de enfermeria");
            assertThat(comando.getValue().firma()).isEqualTo(PNG);
            assertThat(comando.getValue().aplicarALaOrden()).isTrue();
        }

        @Test
        @DisplayName("sin la casilla, el comando no aplica a la orden")
        void sinLaCasilla() throws Exception {
            ServiceReport reporte = unReporte();
            when(serviceReportServicePort.signReceipt(any())).thenReturn(List.of(reporte));

            mockMvc.perform(patch("/v1/api/reports/" + reporte.getId() + "/receipt-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("data:image/png;base64," + Base64.getEncoder().encodeToString(PNG),
                                    false)))
                    .andExpect(status().isOk());

            ArgumentCaptor<SignReceiptCommand> comando = ArgumentCaptor.forClass(SignReceiptCommand.class);
            verify(serviceReportServicePort).signReceipt(comando.capture());
            assertThat(comando.getValue().aplicarALaOrden()).isFalse();
        }

        @Test
        @DisplayName("la casilla es opcional: sin ella, solo este reporte")
        void sinLaCasillaEnElCuerpo() throws Exception {
            ServiceReport reporte = unReporte();
            when(serviceReportServicePort.signReceipt(any())).thenReturn(List.of(reporte));

            mockMvc.perform(patch("/v1/api/reports/" + reporte.getId() + "/receipt-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\": \"Ana Pérez\", \"imagen\": \"data:image/png;base64,"
                                    + Base64.getEncoder().encodeToString(PNG) + "\"}"))
                    .andExpect(status().isOk());

            ArgumentCaptor<SignReceiptCommand> comando = ArgumentCaptor.forClass(SignReceiptCommand.class);
            verify(serviceReportServicePort).signReceipt(comando.capture());
            assertThat(comando.getValue().aplicarALaOrden()).isFalse();
        }

        @Test
        @DisplayName("una imagen que no llega como PNG se rechaza sin llegar al servicio")
        void noEsPng() throws Exception {
            // El mismo largo de prefijo y un base64 válido: lo que la rechaza es el prefijo y no otra cosa.
            mockMvc.perform(patch("/v1/api/reports/" + UUID.randomUUID() + "/receipt-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("data:image/gif;base64," + Base64.getEncoder().encodeToString(PNG),
                                    false)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ERR_SERVICE_REPORT_002"));

            verify(serviceReportServicePort, never()).signReceipt(any());
        }

        @Test
        @DisplayName("un base64 roto responde 400 sin llegar al servicio")
        void base64Roto() throws Exception {
            mockMvc.perform(patch("/v1/api/reports/" + UUID.randomUUID() + "/receipt-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("data:image/png;base64,%%%", false)))
                    .andExpect(status().isBadRequest());

            verify(serviceReportServicePort, never()).signReceipt(any());
        }

        @Test
        @DisplayName("sin nombre no se firma: una firma sin nombre no dice quién firmó")
        void sinNombre() throws Exception {
            mockMvc.perform(patch("/v1/api/reports/" + UUID.randomUUID() + "/receipt-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\": \" \", \"imagen\": \"data:image/png;base64,"
                                    + Base64.getEncoder().encodeToString(PNG) + "\", \"aplicarALaOrden\": false}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ERR_INVALID_DATA_002"));

            verify(serviceReportServicePort, never()).signReceipt(any());
        }

        @Test
        @DisplayName("un reporte ya firmado responde 409")
        void yaFirmado() throws Exception {
            when(serviceReportServicePort.signReceipt(any()))
                    .thenThrow(new IllegalStateException("Este reporte ya tiene la firma de quien recibe"));

            mockMvc.perform(patch("/v1/api/reports/" + UUID.randomUUID() + "/receipt-signature")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("data:image/png;base64," + Base64.getEncoder().encodeToString(PNG),
                                    false)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ERR_SERVICE_REPORT_003"));
        }
    }

    @Test
    @DisplayName("la respuesta trae solo los accesorios vigentes, en su orden, no los sustituidos")
    void soloLosAccesoriosVigentes() throws Exception {
        IntakeCheck conforme = new IntakeCheck(IntakeState.CONFORME, null);
        ServiceReport reporte = unReporte();
        reporte.recordIntake(conforme, conforme, List.of(ReportAccessory.of(1, "Viejo", true, null)));
        reporte.recordIntake(conforme, conforme, List.of(
                ReportAccessory.of(2, "Cable", true, null), ReportAccessory.of(1, "Sonda", true, null)));
        when(serviceReportServicePort.findById(any(), any())).thenReturn(reporte);

        mockMvc.perform(get("/v1/api/reports/" + reporte.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accesorios.length()").value(2))
                .andExpect(jsonPath("$.accesorios[0].nombre").value("Sonda"))
                .andExpect(jsonPath("$.accesorios[1].nombre").value("Cable"));
    }

    @Test
    @DisplayName("un reporte sin ingreso ni quien recibe los devuelve nulos y sin accesorios")
    void sinRecepcion() throws Exception {
        ServiceReport reporte = unReporte();
        when(serviceReportServicePort.findById(any(), any())).thenReturn(reporte);

        mockMvc.perform(get("/v1/api/reports/" + reporte.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingresoFisico").doesNotExist())
                .andExpect(jsonPath("$.accesorios").isEmpty())
                .andExpect(jsonPath("$.recibe").doesNotExist());
    }

    @Test
    @DisplayName("cada lectura sale con su tolerancia, su error y si es conforme")
    void conformidadEnLaRespuesta() throws Exception {
        ServiceReport reporte = unReporte();
        reporte.recordVerification(List.of(
                VerificationReading.of(UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("50"),
                        new BigDecimal("50.7"), "mmHg", new BigDecimal("0.5")),
                VerificationReading.of(UUID.randomUUID(), null, 1, new BigDecimal("37"),
                        new BigDecimal("36.9"), "°C")));
        when(serviceReportServicePort.findById(any(), any())).thenReturn(reporte);

        mockMvc.perform(get("/v1/api/reports/" + reporte.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lecturas[0].tolerancia").value(0.5))
                .andExpect(jsonPath("$.lecturas[0].error").value(0.7))
                .andExpect(jsonPath("$.lecturas[0].conforme").value(false))
                // Sin tolerancia no se dice: ni true ni false.
                .andExpect(jsonPath("$.lecturas[1].error").value(-0.1))
                .andExpect(jsonPath("$.lecturas[1].conforme").doesNotExist());
    }

    @Test
    @DisplayName("el avance por orden sale con sus dos cuentas, y no se confunde con un reporte")
    void avancePorOrden() throws Exception {
        // «progress» no es un identificador: si la ruta de un reporte la capturara, esto daria 400.
        when(serviceReportServicePort.progressByWorkOrder(any()))
                .thenReturn(List.of(new WorkOrderReportProgress(ORDEN, 3, 2)));

        mockMvc.perform(get("/v1/api/reports/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idOrdenTrabajo").value(ORDEN.toString()))
                .andExpect(jsonPath("$[0].cerrados").value(3))
                .andExpect(jsonPath("$[0].abiertos").value(2));
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
                                        UUID.randomUUID(), UUID.randomUUID(), 101,
                                        BigDecimal.ONE, BigDecimal.TWO))))))
                .andExpect(status().isBadRequest());

        verify(serviceReportServicePort, never()).recordVerification(any());
    }

    @Test
    @DisplayName("el informe viaja con lo congelado, el logo como data: URI y si es copia del cierre o tardía")
    void elInforme() throws Exception {
        ServiceReport reporte = unReporte();
        DocumentImage logo =
                new DocumentImage(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1}, "image/png");
        DocumentImage firma =
                new DocumentImage(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 2}, "image/png");
        ReportDocument base = UnDocumento.CUALQUIERA;
        ReportDocument documento = new ReportDocument(base.version(), base.orden(), base.cliente(), base.equipo(),
                List.of(), new ReportDocument.Empresa("Ejemplo S.A.S.", null, null, null, null, "La garantía",
                        null, "FT-2", logo.huella()),
                "Ana Pérez", firma.huella());
        DocumentImage firmaRecibe =
                new DocumentImage(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 3}, "image/png");
        when(serviceReportServicePort.document(eq(reporte.getId()), any()))
                .thenReturn(new ReportDocumentView(reporte, documento, logo, firma, firmaRecibe, true));

        mockMvc.perform(get("/v1/api/reports/" + reporte.getId() + "/document"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.congelado").value(true))
                .andExpect(jsonPath("$.tardio").value(false))
                .andExpect(jsonPath("$.orden.numero").value("4F2A9C"))
                .andExpect(jsonPath("$.cliente.nombre").value("Clínica de Ejemplo"))
                .andExpect(jsonPath("$.equipo.modelo").value("MS-200"))
                .andExpect(jsonPath("$.empresa.garantia").value("La garantía"))
                .andExpect(jsonPath("$.empresa.logo").value(logo.dataUri()))
                .andExpect(jsonPath("$.empresa.huellaDelLogo").doesNotExist())
                .andExpect(jsonPath("$.ingeniero").value("Ana Pérez"))
                .andExpect(jsonPath("$.firmaIngeniero").value(firma.dataUri()))
                .andExpect(jsonPath("$.huellaFirmaIngeniero").doesNotExist())
                .andExpect(jsonPath("$.firmaRecibe").value(firmaRecibe.dataUri()));
    }

    @Test
    @DisplayName("el PDF se sirve en línea, con su tipo y el nombre con que se descarga (RF-17)")
    void elPdf() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] bytes = "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(serviceReportServicePort.pdf(eq(id), any())).thenReturn(new ReportPdf("informe-1DC6F5.pdf", bytes));

        mockMvc.perform(get("/v1/api/reports/" + id + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"informe-1DC6F5.pdf\""))
                .andExpect(content().bytes(bytes));
    }

    @Test
    @DisplayName("si el conversor no responde, 503 con su código: se puede reintentar")
    void pdfSinConversor() throws Exception {
        UUID id = UUID.randomUUID();
        when(serviceReportServicePort.pdf(eq(id), any()))
                .thenThrow(new ReportPdfUnavailableException(new RuntimeException("caído")));

        mockMvc.perform(get("/v1/api/reports/" + id + "/pdf"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ERR_SERVICE_REPORT_010"));
    }

    @Test
    @DisplayName("el PDF de un reporte que no existe, o que no se ve, responde 404")
    void pdfQueNoExiste() throws Exception {
        UUID id = UUID.randomUUID();
        when(serviceReportServicePort.pdf(eq(id), any())).thenThrow(new ServiceReportNotFoundException(id));

        mockMvc.perform(get("/v1/api/reports/" + id + "/pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("cerrar es una operacion, no un campo que se edita")
    void cerrar() throws Exception {
        ServiceReport reporte = unReporte();
        reporte.fill(null, null, "Limpieza", null, ServiceResult.OPERATIVO);
        reporte.recordIntake(new IntakeCheck(IntakeState.CONFORME, null),
                new IntakeCheck(IntakeState.CONFORME, null), List.of());
        reporte.finish(UnDocumento.CUALQUIERA);
        when(serviceReportServicePort.finish(any())).thenReturn(reporte);

        mockMvc.perform(patch("/v1/api/reports/" + reporte.getId() + "/finish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADO"))
                .andExpect(jsonPath("$.finalizado").isNotEmpty());
    }

    @Test
    @DisplayName("marcar un paso llega al servicio con su orden, su estado y su nota, y los pasos salen en la respuesta")
    void marcarPaso() throws Exception {
        ServiceReport reporte = ServiceReport.open(UUID.randomUUID(), UUID.randomUUID(), List.of(
                ReportStep.copied(1, UUID.randomUUID(), "Batería", "Revisar la batería.", null)));
        reporte.markStep(1, StepState.NO_APLICA, "No tiene");
        when(serviceReportServicePort.markStep(any())).thenReturn(reporte);

        mockMvc.perform(patch("/v1/api/reports/" + reporte.getId() + "/steps/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"NO_APLICA\", \"nota\": \"No tiene\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pasos[0].nombre").value("Batería"))
                .andExpect(jsonPath("$.pasos[0].estado").value("NO_APLICA"))
                .andExpect(jsonPath("$.pasos[0].nota").value("No tiene"));

        ArgumentCaptor<MarkReportStepCommand> comando = ArgumentCaptor.forClass(MarkReportStepCommand.class);
        verify(serviceReportServicePort).markStep(comando.capture());
        assertThat(comando.getValue().orden()).isEqualTo(1);
        assertThat(comando.getValue().estado()).isEqualTo(StepState.NO_APLICA);
        assertThat(comando.getValue().nota()).isEqualTo("No tiene");
    }

    @Test
    @DisplayName("marcar un paso sin estado responde 400 sin llegar al servicio")
    void marcarPasoSinEstado() throws Exception {
        mockMvc.perform(patch("/v1/api/reports/" + UUID.randomUUID() + "/steps/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\": \"Algo\"}"))
                .andExpect(status().isBadRequest());

        verify(serviceReportServicePort, never()).markStep(any());
    }

    @Test
    @DisplayName("el estado no se puede escribir en el cuerpo: no hay campo para el")
    void elEstadoNoSeEscribe() {
        assertThat(ServiceReportFillRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                // Causa y repuestos entraron el 2026-10-14 con el correctivo (V24): la lista crece por
                // decisión, que es para lo que existe esta prueba.
                .containsExactly("fallaReportada", "diagnostico", "procedimientos", "observaciones",
                        "causa", "repuestos", "resultado");
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
        when(serviceReportServicePort.findByWorkOrder(ORDEN, ReadScope.unrestricted())).thenReturn(List.of(unReporte()));

        mockMvc.perform(get("/v1/api/reports").param("idOrdenTrabajo", ORDEN.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idOrdenTrabajo").value(ORDEN.toString()));
    }

    @Test
    @DisplayName("listar por equipo devuelve su historial")
    void listarPorEquipo() throws Exception {
        when(serviceReportServicePort.findByEquipment(EQUIPO, ReadScope.unrestricted())).thenReturn(List.of(unReporte()));

        mockMvc.perform(get("/v1/api/reports").param("idEquipoCliente", EQUIPO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idEquipoCliente").value(EQUIPO.toString()));
    }

    @Test
    @DisplayName("un reporte que no existe da 404 con el codigo del modulo")
    void noExiste() throws Exception {
        UUID id = UUID.randomUUID();
        when(serviceReportServicePort.findById(id, ReadScope.unrestricted())).thenThrow(new ServiceReportNotFoundException(id));

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
                                        UUID.randomUUID(), null, 1,
                                        BigDecimal.ONE, BigDecimal.TWO))))))
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
        assertThat(docs).contains("/v1/api/reports/{id}/intake");
        assertThat(docs).contains("/v1/api/reports/{id}/handover");
        assertThat(docs).contains("/v1/api/reports/{id}/receipt-signature");
        assertThat(docs).contains("/v1/api/reports/{id}/pdf");
        assertThat(docs).contains("/v1/api/reports/{id}/verifications/{idVerificacion}/reference-standards");
        assertThat(docs).contains(
                "/v1/api/reports/{id}/verifications/{idVerificacion}/reference-standards/{idEquipoPatron}");
        assertThat(docs).contains("/v1/api/reports/progress");
        assertThat(docs).contains("/v1/api/reports/{id}/replace");
        assertThat(docs).doesNotContain("/v1/api/reports/work-orders/{idOrdenTrabajo}");
    }

    @Test
    @DisplayName("el reporte no ofrece ninguna ruta para reabrirlo")
    void nadaReabreUnReporte() throws Exception {
        mockMvc.perform(patch("/v1/api/reports/" + UUID.randomUUID() + "/reopen"))
                .andExpect(status().isNotFound());
    }
}
