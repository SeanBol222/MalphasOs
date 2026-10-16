package com.malphasos.malphasos.equipment.infrastructure.input.rest;

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
import com.malphasos.malphasos.equipment.application.ports.input.ReferenceStandardServicePort;
import com.malphasos.malphasos.equipment.application.services.referenceStandard.commands.SaveReferenceStandardCommand;
import com.malphasos.malphasos.equipment.domain.exception.ReferenceStandardNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ReferenceStandardSerialTakenException;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandardSheet;
import java.time.LocalDate;
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

/** Contrato HTTP del catálogo de equipos patrón (2026-10-14). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReferenceStandardRestAdapterTest {

    private static final String RUTA = "/v1/api/reference-standards";
    private static final String CUERPO = """
            {"nombre": "Juego de pesas F1", "marca": "Kern", "serie": "SN-1",
             "certificado": "CAL-1", "vencimientoCertificado": "2027-03-31"}
            """;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ReferenceStandardServicePort servicio;

    @Test
    @DisplayName("POST responde 201 con lo creado, la fecha en ISO")
    void crear() throws Exception {
        when(servicio.create(any())).thenReturn(
                ReferenceStandard.create("Juego de pesas F1", "Kern", "SN-1", "CAL-1", LocalDate.of(2027, 3, 31),
                        ReferenceStandardSheet.VACIA));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serie").value("SN-1"))
                .andExpect(jsonPath("$.vencimientoCertificado").value("2027-03-31"))
                .andExpect(jsonPath("$.estadoActivo").value(true));

        ArgumentCaptor<SaveReferenceStandardCommand> comando = ArgumentCaptor.forClass(SaveReferenceStandardCommand.class);
        verify(servicio).create(comando.capture());
        assertThat(comando.getValue().id()).isNull();
        assertThat(comando.getValue().certificado()).isEqualTo("CAL-1");
    }

    @Test
    @DisplayName("la ficha viaja al comando y vuelve en la respuesta")
    void laFicha() throws Exception {
        ReferenceStandardSheet ficha = new ReferenceStandardSheet("MS-200", "BB-PAT-003", "1 g a 2 kg", "0,1 mg",
                LocalDate.of(2026, 3, 31), "Metrologia Andina");
        when(servicio.create(any())).thenReturn(ReferenceStandard.create(
                "Juego de pesas F1", "Kern", "SN-1", "CAL-1", LocalDate.of(2027, 3, 31), ficha));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                        {"nombre": "Juego de pesas F1", "marca": "Kern", "serie": "SN-1",
                         "certificado": "CAL-1", "vencimientoCertificado": "2027-03-31",
                         "modelo": "MS-200", "codigoInterno": "BB-PAT-003", "rango": "1 g a 2 kg",
                         "resolucion": "0,1 mg", "fechaCalibracion": "2026-03-31",
                         "empresaCalibradora": "Metrologia Andina"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoInterno").value("BB-PAT-003"))
                .andExpect(jsonPath("$.fechaCalibracion").value("2026-03-31"))
                .andExpect(jsonPath("$.empresaCalibradora").value("Metrologia Andina"));

        ArgumentCaptor<SaveReferenceStandardCommand> comando = ArgumentCaptor.forClass(SaveReferenceStandardCommand.class);
        verify(servicio).create(comando.capture());
        assertThat(comando.getValue().ficha()).isEqualTo(ficha);
    }

    @Test
    @DisplayName("sin ficha llega la vacía, no una nula")
    void sinFicha() throws Exception {
        when(servicio.create(any())).thenReturn(ReferenceStandard.create(
                "Juego de pesas F1", "Kern", "SN-1", "CAL-1", LocalDate.of(2027, 3, 31), ReferenceStandardSheet.VACIA));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modelo").doesNotExist());

        ArgumentCaptor<SaveReferenceStandardCommand> comando = ArgumentCaptor.forClass(SaveReferenceStandardCommand.class);
        verify(servicio).create(comando.capture());
        assertThat(comando.getValue().ficha()).isEqualTo(ReferenceStandardSheet.VACIA);
    }

    @Test
    @DisplayName("sin vencimiento responde 400 sin llegar al servicio")
    void sinVencimiento() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"N\", \"serie\": \"S\", \"certificado\": \"C\"}"))
                .andExpect(status().isBadRequest());

        verify(servicio, never()).create(any());
    }

    @Test
    @DisplayName("una serie ocupada responde 409 con su código")
    void serieOcupada() throws Exception {
        when(servicio.create(any())).thenThrow(new ReferenceStandardSerialTakenException("SN-1", "Otro"));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_018"));
    }

    @Test
    @DisplayName("uno que no existe responde 404 con su código")
    void inexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(servicio.findById(id)).thenThrow(new ReferenceStandardNotFoundException(id));

        mockMvc.perform(get(RUTA + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_017"));
    }

    @Test
    @DisplayName("PATCH lleva el identificador de la ruta al comando")
    void corregir() throws Exception {
        UUID id = UUID.randomUUID();
        when(servicio.update(any())).thenReturn(
                ReferenceStandard.rehydrate(id, "Juego de pesas F1", "Kern", "SN-1", "CAL-1", LocalDate.of(2027, 3, 31),
                        ReferenceStandardSheet.VACIA, true));

        mockMvc.perform(patch(RUTA + "/" + id).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk());

        ArgumentCaptor<SaveReferenceStandardCommand> comando = ArgumentCaptor.forClass(SaveReferenceStandardCommand.class);
        verify(servicio).update(comando.capture());
        assertThat(comando.getValue().id()).isEqualTo(id);
    }

    @Test
    @DisplayName("DELETE retira y responde 204")
    void retirar() throws Exception {
        mockMvc.perform(delete(RUTA + "/" + UUID.randomUUID())).andExpect(status().isNoContent());
    }
}
