package com.malphasos.malphasos.company.infrastructure.input.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.company.application.ports.input.ServiceCompanyServicePort;
import com.malphasos.malphasos.company.application.services.serviceCompany.commands.ChangeCompanyLogoCommand;
import com.malphasos.malphasos.company.application.services.serviceCompany.commands.SaveServiceCompanyCommand;
import com.malphasos.malphasos.company.domain.exception.ServiceCompanyNotConfiguredException;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyDetails;
import com.malphasos.malphasos.company.domain.serviceCompany.CompanyLogo;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import com.malphasos.malphasos.location.domain.exception.CityNotFoundException;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Contrato HTTP de la empresa que presta el servicio (2026-10-17). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ServiceCompanyRestAdapterTest {

    private static final String RUTA = "/v1/api/company";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1};

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ServiceCompanyServicePort servicio;

    private static ServiceCompany unaEmpresa(CompanyLogo logo) {
        return ServiceCompany.rehydrate(UUID.randomUUID(),
                new CompanyDetails("Ejemplo S.A.S.", "900", null, null, List.of("601 1"), null, null,
                        "La garantía", null, "FT-1", null),
                logo);
    }

    @Test
    @DisplayName("GET sin empresa configurada responde 404 con su código")
    void sinConfigurar() throws Exception {
        when(servicio.find()).thenReturn(Optional.empty());

        mockMvc.perform(get(RUTA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_COMPANY_001"));
    }

    @Test
    @DisplayName("GET trae sus datos y el logo como data: URI")
    void obtener() throws Exception {
        CompanyLogo logo = CompanyLogo.of(PNG);
        when(servicio.find()).thenReturn(Optional.of(unaEmpresa(logo)));

        mockMvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ejemplo S.A.S."))
                .andExpect(jsonPath("$.telefonos[0]").value("601 1"))
                .andExpect(jsonPath("$.garantia").value("La garantía"))
                .andExpect(jsonPath("$.logo").value(logo.dataUri()))
                .andExpect(jsonPath("$.correo").doesNotExist());
    }

    @Test
    @DisplayName("PATCH manda todos los campos al comando")
    void guardar() throws Exception {
        when(servicio.save(any())).thenReturn(unaEmpresa(null));

        mockMvc.perform(patch(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                        {"nombre": "Ejemplo S.A.S.", "telefonos": ["601 1"], "garantia": "La garantía",
                         "codigoInforme": "FT-2"}
                        """))
                .andExpect(status().isOk());

        ArgumentCaptor<SaveServiceCompanyCommand> comando = ArgumentCaptor.forClass(SaveServiceCompanyCommand.class);
        verify(servicio).save(comando.capture());
        assertThat(comando.getValue().garantia()).isEqualTo("La garantía");
        assertThat(comando.getValue().telefonos()).containsExactly("601 1");
        assertThat(comando.getValue().codigoInforme()).isEqualTo("FT-2");
    }

    @Test
    @DisplayName("PATCH con una ciudad que no existe responde 404 con su propio código (V44)")
    void ciudadQueNoExiste() throws Exception {
        UUID ciudad = UUID.randomUUID();
        when(servicio.save(any())).thenThrow(new CityNotFoundException(ciudad));

        mockMvc.perform(patch(RUTA).contentType(MediaType.APPLICATION_JSON).content("""
                        {"nombre": "Ejemplo S.A.S.", "idCiudad": "%s"}
                        """.formatted(ciudad)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_COMPANY_003"));

        ArgumentCaptor<SaveServiceCompanyCommand> comando = ArgumentCaptor.forClass(SaveServiceCompanyCommand.class);
        verify(servicio).save(comando.capture());
        assertThat(comando.getValue().idCiudad()).isEqualTo(ciudad);
    }

    @Test
    @DisplayName("PATCH sin nombre responde 400 sin llegar al servicio")
    void sinNombre() throws Exception {
        mockMvc.perform(patch(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": \" \"}"))
                .andExpect(status().isBadRequest());

        verify(servicio, never()).save(any());
    }

    @Test
    @DisplayName("el logo se sube como archivo, y sus bytes llegan tal cual al comando")
    void subirLogo() throws Exception {
        when(servicio.changeLogo(any())).thenReturn(unaEmpresa(CompanyLogo.of(PNG)));

        mockMvc.perform(multipart(HttpMethod.PATCH, RUTA + "/logo")
                        .file(new MockMultipartFile("archivo", "logo.png", "image/png", PNG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logo").value(CompanyLogo.of(PNG).dataUri()));

        ArgumentCaptor<ChangeCompanyLogoCommand> comando = ArgumentCaptor.forClass(ChangeCompanyLogoCommand.class);
        verify(servicio).changeLogo(comando.capture());
        assertThat(comando.getValue().contenido()).isEqualTo(PNG);
    }

    @Test
    @DisplayName("un logo que no es imagen responde 400 con el código de datos inválidos")
    void logoInvalido() throws Exception {
        when(servicio.changeLogo(any())).thenThrow(
                new IllegalArgumentException("El logo de la empresa tiene que ser PNG, JPG o SVG"));

        mockMvc.perform(multipart(HttpMethod.PATCH, RUTA + "/logo")
                        .file(new MockMultipartFile("archivo", "logo.png", "image/png", "GIF89a".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_COMPANY_002"));
    }

    @Test
    @DisplayName("quitar el logo responde 204; sin empresa, 404")
    void quitarLogo() throws Exception {
        mockMvc.perform(delete(RUTA + "/logo")).andExpect(status().isNoContent());
        verify(servicio).removeLogo();

        when(servicio.removeLogo()).thenThrow(new ServiceCompanyNotConfiguredException());
        mockMvc.perform(delete(RUTA + "/logo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_COMPANY_001"));
    }

    @Test
    @DisplayName("el grupo company del contrato publica sus rutas")
    void openApi() throws Exception {
        String docs = mockMvc.perform(get("/v3/api-docs/company"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(docs).contains("/v1/api/company", "/v1/api/company/logo");
    }
}
