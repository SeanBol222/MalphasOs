package com.malphasos.malphasos.dataload.infrastructure.input.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import com.malphasos.malphasos.dataload.application.model.DataLoadReport;
import com.malphasos.malphasos.dataload.application.ports.input.DataLoadServicePort;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Contrato HTTP de la carga de datos (2026-11-07). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DataLoadRestAdapterTest {

    private static final String RUTA = "/v1/api/data-loads/validation";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private DataLoadServicePort servicio;

    @Test
    @DisplayName("los archivos subidos llegan al servicio con su nombre y sus bytes")
    @SuppressWarnings("unchecked")
    void llegan() throws Exception {
        when(servicio.validate(any())).thenReturn(new DataLoadReport(List.of(), List.of()));

        mockMvc.perform(multipart(RUTA)
                        .file(new MockMultipartFile("archivos", "1-catalogo.json", "application/json", "{\"a\":1}".getBytes()))
                        .file(new MockMultipartFile("archivos", "2-cliente.json", "application/json", "{}".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(true));

        ArgumentCaptor<List<DataLoadFile>> archivos = ArgumentCaptor.forClass(List.class);
        verify(servicio).validate(archivos.capture());
        assertThat(archivos.getValue()).extracting(DataLoadFile::nombre).containsExactly("1-catalogo.json", "2-cliente.json");
        assertThat(new String(archivos.getValue().getFirst().contenido())).isEqualTo("{\"a\":1}");
    }

    @Test
    @DisplayName("una carga con errores responde 200: es el resultado de validarla, no un fallo de la peticion")
    void conErrores() throws Exception {
        when(servicio.validate(any())).thenReturn(new DataLoadReport(
                List.of(new DataLoadProblem("1-catalogo.json", "/catalogo/protocolos/9/nombre", "es demasiado largo")),
                List.of(DataLoadProblem.of("2-cliente.json", "un equipo recibira serie provisional"))));

        mockMvc.perform(multipart(RUTA)
                        .file(new MockMultipartFile("archivos", "1-catalogo.json", "application/json", "{}".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(false))
                .andExpect(jsonPath("$.errores[0].archivo").value("1-catalogo.json"))
                .andExpect(jsonPath("$.errores[0].ruta").value("/catalogo/protocolos/9/nombre"))
                .andExpect(jsonPath("$.avisos[0].ruta").doesNotExist());
    }

    @Test
    @DisplayName("sin archivos el servicio recibe una lista vacia y lo dice en su informe")
    void sinArchivos() throws Exception {
        when(servicio.validate(List.of())).thenReturn(new DataLoadReport(List.of(DataLoadProblem.of(null, "sin archivos")), List.of()));

        mockMvc.perform(multipart(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(false));
    }

    @Test
    @DisplayName("el grupo data-load del contrato publica su ruta")
    void openApi() throws Exception {
        String docs = mockMvc.perform(get("/v3/api-docs/data-load"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(docs).contains("/v1/api/data-loads/validation");
    }
}
