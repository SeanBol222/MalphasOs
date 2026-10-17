package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.company.application.ports.input.ServiceCompanyServicePort;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyDetails;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyLogo;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheet;
import com.malphasos.malphasos.equipment.application.ports.input.LifeSheetServicePort;
import com.malphasos.malphasos.equipment.domain.model.TechnicalSheet;
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
 * El membrete de la hoja de vida sale de la empresa (2026-10-16). Desde el 2026-10-17 la empresa vive en
 * la base y la edita un administrador, así que aquí se dobla su puerto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LifeSheetRestAdapterTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private LifeSheetServicePort servicio;
    @MockitoBean private ServiceCompanyServicePort empresa;

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

    /** Un PNG de verdad: el logo deduce su tipo de los bytes. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0};

    private static ServiceCompany unaEmpresa(CompanyLogo logo) {
        return ServiceCompany.rehydrate(UUID.randomUUID(),
                new CompanyDetails("Servicios Biomédicos de Ejemplo S.A.S.", "900.000.000-0", null, null,
                        List.of(), "300 000 0000", null, null, null, null, null),
                logo);
    }

    @Test
    @DisplayName("el membrete trae los datos de la empresa, y nulo lo que no tiene")
    void elMembrete() throws Exception {
        UUID id = UUID.randomUUID();
        when(servicio.findByEquipment(eq(id), any())).thenReturn(hoja(id));
        CompanyLogo logo = CompanyLogo.of(PNG);
        when(empresa.find()).thenReturn(Optional.of(unaEmpresa(logo)));

        mockMvc.perform(get("/v1/api/client-equipments/{id}/life-sheet", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empresa.nombre").value("Servicios Biomédicos de Ejemplo S.A.S."))
                .andExpect(jsonPath("$.empresa.nit").value("900.000.000-0"))
                .andExpect(jsonPath("$.empresa.movil").value("300 000 0000"))
                .andExpect(jsonPath("$.empresa.logo").value(logo.dataUri()))
                .andExpect(jsonPath("$.empresa.garantia").doesNotExist())
                .andExpect(jsonPath("$.empresa.codigoInforme").doesNotExist());
    }

    @Test
    @DisplayName("sin logo, el membrete no lo trae")
    void sinLogo() throws Exception {
        UUID id = UUID.randomUUID();
        when(servicio.findByEquipment(eq(id), any())).thenReturn(hoja(id));
        when(empresa.find()).thenReturn(Optional.of(unaEmpresa(null)));

        mockMvc.perform(get("/v1/api/client-equipments/{id}/life-sheet", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empresa.nombre").value("Servicios Biomédicos de Ejemplo S.A.S."))
                .andExpect(jsonPath("$.empresa.logo").doesNotExist());
    }

    @Test
    @DisplayName("sin empresa configurada la hoja de vida sale igual, sin membrete")
    void sinEmpresa() throws Exception {
        UUID id = UUID.randomUUID();
        when(servicio.findByEquipment(eq(id), any())).thenReturn(hoja(id));
        when(empresa.find()).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/api/client-equipments/{id}/life-sheet", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificacion.serie").value("SN-1"))
                .andExpect(jsonPath("$.empresa").doesNotExist());
    }
}
