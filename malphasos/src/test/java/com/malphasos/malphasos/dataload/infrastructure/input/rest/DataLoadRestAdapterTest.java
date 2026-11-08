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
import com.malphasos.malphasos.dataload.application.exception.DataLoadRejectedException;
import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import com.malphasos.malphasos.dataload.application.model.DataLoadReport;
import com.malphasos.malphasos.dataload.application.model.SectionCount;
import com.malphasos.malphasos.dataload.application.ports.input.DataLoadServicePort;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Contrato HTTP de la carga de datos (2026-11-07). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DataLoadRestAdapterTest {

    private static final String RUTA = "/v1/api/data-loads/validation";
    private static final String APLICAR = "/v1/api/data-loads";

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
                List.of(DataLoadProblem.of("2-cliente.json", "un equipo recibira serie provisional")),
                List.of(new SectionCount("marcas", 3, 2))));

        mockMvc.perform(multipart(RUTA)
                        .file(new MockMultipartFile("archivos", "1-catalogo.json", "application/json", "{}".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(false))
                .andExpect(jsonPath("$.errores[0].archivo").value("1-catalogo.json"))
                .andExpect(jsonPath("$.errores[0].ruta").value("/catalogo/protocolos/9/nombre"))
                .andExpect(jsonPath("$.avisos[0].ruta").doesNotExist())
                .andExpect(jsonPath("$.resumen[0].seccion").value("marcas"))
                .andExpect(jsonPath("$.resumen[0].nuevos").value(3))
                .andExpect(jsonPath("$.resumen[0].existentes").value(2));
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
    @DisplayName("aplicar una carga valida responde 200 con su informe")
    void aplicada() throws Exception {
        when(servicio.apply(any())).thenReturn(new DataLoadReport(List.of(), List.of(), List.of(new SectionCount("marcas", 1, 0))));

        mockMvc.perform(multipart(APLICAR)
                        .file(new MockMultipartFile("archivos", "1-catalogo.json", "application/json", "{}".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(true))
                .andExpect(jsonPath("$.resumen[0].nuevos").value(1));
    }

    @Test
    @DisplayName("aplicar una carga con errores responde 422: no se escribio nada")
    void noAplicada() throws Exception {
        when(servicio.apply(any())).thenReturn(new DataLoadReport(List.of(DataLoadProblem.of(null, "sin archivos")), List.of()));

        mockMvc.perform(multipart(APLICAR))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.valida").value(false))
                .andExpect(jsonPath("$.errores[0].mensaje").value("sin archivos"));
    }

    @Test
    @DisplayName("una carga que MalphasOS rechaza al escribirla responde 422 con el mismo informe")
    void rechazada() throws Exception {
        when(servicio.apply(any())).thenThrow(new DataLoadRejectedException(
                new DataLoadProblem("1-catalogo.json", "/catalogo/patrones/0", "MalphasOS no lo aceptó: fecha"), null));

        mockMvc.perform(multipart(APLICAR)
                        .file(new MockMultipartFile("archivos", "1-catalogo.json", "application/json", "{}".getBytes())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.valida").value(false))
                .andExpect(jsonPath("$.errores[0].ruta").value("/catalogo/patrones/0"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("MalphasOS no lo aceptó: fecha"));
    }

    /**
     * El rechazo de la base lleva su causa, y el advice global sabe responder a esa causa con un 409: sin
     * orden entre los dos, Spring eligió el global por la causa. Lo destapó la primera carga real, el
     * 2026-11-07; la prueba de arriba no lo veía porque su rechazo no tiene causa.
     */
    @Test
    @DisplayName("un rechazo de la base responde 422 con el informe, no el 409 generico de su causa")
    void rechazadaPorLaBase() throws Exception {
        when(servicio.apply(any())).thenThrow(new DataLoadRejectedException(
                DataLoadProblem.of(null, "La base de datos rechazó la carga, y no se escribió nada: CHK"),
                new DataIntegrityViolationException("CHK_orden_trabajo_garantia")));

        mockMvc.perform(multipart(APLICAR)
                        .file(new MockMultipartFile("archivos", "1-catalogo.json", "application/json", "{}".getBytes())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.valida").value(false))
                .andExpect(jsonPath("$.errores[0].mensaje").value("La base de datos rechazó la carga, y no se escribió nada: CHK"));
    }

    @Test
    @DisplayName("el grupo data-load del contrato publica su ruta")
    void openApi() throws Exception {
        String docs = mockMvc.perform(get("/v3/api-docs/data-load"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(docs).contains("/v1/api/data-loads/validation").contains("\"/v1/api/data-loads\"");
    }
}
