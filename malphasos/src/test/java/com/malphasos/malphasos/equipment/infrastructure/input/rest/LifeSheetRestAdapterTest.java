package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheet;
import com.malphasos.malphasos.equipment.application.ports.input.LifeSheetServicePort;
import com.malphasos.malphasos.equipment.domain.model.TechnicalSheet;
import com.malphasos.malphasos.shared.config.ServiceCompanyLogo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El membrete de la hoja de vida sale de la configuración de la empresa (2026-10-16): la de pruebas es
 * la empresa inventada de src/test/resources/application.yaml. Nada lo comprobaba hasta entonces.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LifeSheetRestAdapterTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private LifeSheetServicePort servicio;
    @MockitoBean private ServiceCompanyLogo logo;

    private static LifeSheet hoja(UUID id) {
        return new LifeSheet(
                new LifeSheet.Identificacion(id, "HV-CDN-0001", "SN-1", null, null, null, null, null,
                        "Clínica del Norte", "900.111.222-3", "Sede Norte", "Calle 1", "Bogotá", "Urgencias", true,
                        List.of(), List.of(), List.of()),
                new LifeSheet.Tecnica("Báscula", null, null, null, null, null, "Kern", "MS-200", null,
                        TechnicalSheet.EMPTY, List.of()),
                new LifeSheet.Fabricante("Kern", null),
                List.of());
    }

    @Test
    @DisplayName("el membrete trae los datos configurados de la empresa, y nulo lo que no se configuró")
    void elMembrete() throws Exception {
        UUID id = UUID.randomUUID();
        when(servicio.findByEquipment(eq(id), any())).thenReturn(hoja(id));
        when(logo.dataUri()).thenReturn(Optional.of("data:image/png;base64,AAAA"));

        mockMvc.perform(get("/v1/api/client-equipments/{id}/life-sheet", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empresa.nombre").value("Servicios Biomédicos de Ejemplo S.A.S."))
                .andExpect(jsonPath("$.empresa.nit").value("900.000.000-0"))
                .andExpect(jsonPath("$.empresa.movil").value("300 000 0000"))
                .andExpect(jsonPath("$.empresa.logo").value("data:image/png;base64,AAAA"))
                .andExpect(jsonPath("$.empresa.garantia").doesNotExist())
                .andExpect(jsonPath("$.empresa.codigoInforme").doesNotExist());
    }

    @Test
    @DisplayName("sin logo configurado, el membrete no lo trae")
    void sinLogo() throws Exception {
        UUID id = UUID.randomUUID();
        when(servicio.findByEquipment(eq(id), any())).thenReturn(hoja(id));
        when(logo.dataUri()).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/api/client-equipments/{id}/life-sheet", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empresa.logo").doesNotExist());
    }
}
