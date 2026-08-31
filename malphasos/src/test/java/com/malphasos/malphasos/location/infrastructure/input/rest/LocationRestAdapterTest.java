package com.malphasos.malphasos.location.infrastructure.input.rest;

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
import com.malphasos.malphasos.location.application.ports.input.CityServicePort;
import com.malphasos.malphasos.location.application.ports.input.CountryServicePort;
import com.malphasos.malphasos.location.application.services.city.commands.UpdateCityCommand;
import com.malphasos.malphasos.location.application.services.country.commands.CreateCountryCommand;
import com.malphasos.malphasos.location.application.services.country.commands.UpdateCountryCommand;
import com.malphasos.malphasos.location.domain.city.City;
import com.malphasos.malphasos.location.domain.country.Country;
import com.malphasos.malphasos.location.domain.exception.CountryNotFoundException;
import com.malphasos.malphasos.location.infrastructure.input.model.request.CityCreateRequest;
import com.malphasos.malphasos.location.infrastructure.input.model.request.CityUpdateRequest;
import com.malphasos.malphasos.location.infrastructure.input.model.request.CountryCreateRequest;
import com.malphasos.malphasos.location.infrastructure.input.model.request.CountryUpdateRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato HTTP del módulo: rutas, códigos de estado, validación de la petición y traducción de
 * excepciones. Los servicios se sustituyen por dobles porque aquí se prueba la capa web.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LocationRestAdapterTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;

    @MockitoBean private CountryServicePort countryServicePort;
    @MockitoBean private CityServicePort cityServicePort;

    private Country unPais(UUID id) {
        return Country.rehydrate(id, "COL", "Colombia", true);
    }

    @Test
    @DisplayName("GET devuelve la lista de paises")
    void listarPaises() throws Exception {
        when(countryServicePort.findAll()).thenReturn(List.of(unPais(UUID.randomUUID())));

        mockMvc.perform(get("/v1/api/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigoIso").value("COL"))
                .andExpect(jsonPath("$[0].nombre").value("Colombia"))
                .andExpect(jsonPath("$[0].estadoActivo").value(true));
    }

    @Test
    @DisplayName("POST de un pais valido responde 201")
    void crearPais() throws Exception {
        UUID id = UUID.randomUUID();
        when(countryServicePort.create(any(CreateCountryCommand.class))).thenReturn(unPais(id));

        mockMvc.perform(post("/v1/api/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CountryCreateRequest("COL", "Colombia"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @DisplayName("un codigo ISO que no son tres letras se rechaza con 400, sin llegar al servicio")
    void codigoIsoInvalido() throws Exception {
        mockMvc.perform(post("/v1/api/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CountryCreateRequest("COLOMBIA", "Colombia"))))
                .andExpect(status().isBadRequest());

        verify(countryServicePort, never()).create(any());
    }

    @Test
    @DisplayName("un nombre en blanco se rechaza con 400")
    void nombreEnBlanco() throws Exception {
        mockMvc.perform(post("/v1/api/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CountryCreateRequest("COL", "  "))))
                .andExpect(status().isBadRequest());

        verify(countryServicePort, never()).create(any());
    }

    @Test
    @DisplayName("PATCH toma el identificador de la ruta y nunca del cuerpo")
    void elIdVieneDeLaRuta() throws Exception {
        UUID id = UUID.randomUUID();
        when(countryServicePort.update(any(UpdateCountryCommand.class))).thenReturn(unPais(id));

        mockMvc.perform(patch("/v1/api/countries/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CountryUpdateRequest("Otro nombre"))))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateCountryCommand> comando =
                ArgumentCaptor.forClass(UpdateCountryCommand.class);
        verify(countryServicePort).update(comando.capture());

        assertThat(comando.getValue().id()).isEqualTo(id);
    }

    @Test
    @DisplayName("un pais inexistente responde 404 con el codigo del catalogo del modulo")
    void paisInexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(countryServicePort.findById(id)).thenThrow(new CountryNotFoundException(id));

        mockMvc.perform(get("/v1/api/countries/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_LOCATION_001"));
    }

    @Test
    @DisplayName("una regla del agregado se traduce a 400, no a 500")
    void reglaDelAgregado() throws Exception {
        when(countryServicePort.create(any(CreateCountryCommand.class)))
                .thenThrow(new IllegalArgumentException("El codigo ISO son tres letras"));

        mockMvc.perform(post("/v1/api/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CountryCreateRequest("COL", "Colombia"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_LOCATION_003"));
    }

    @Test
    @DisplayName("DELETE responde 204 y retira sin borrar")
    void retirarPais() throws Exception {
        mockMvc.perform(delete("/v1/api/countries/" + UUID.randomUUID()))
                .andExpect(status().isNoContent());

        verify(countryServicePort).deactivate(any());
    }

    @Test
    @DisplayName("las ciudades se filtran por pais con el parametro idPais")
    void filtrarCiudadesPorPais() throws Exception {
        UUID pais = UUID.randomUUID();
        when(cityServicePort.findByCountry(pais))
                .thenReturn(List.of(City.rehydrate(UUID.randomUUID(), "Bogota", pais, true)));

        mockMvc.perform(get("/v1/api/cities").param("idPais", pais.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Bogota"));

        verify(cityServicePort, never()).findAll();
    }

    @Test
    @DisplayName("sin el parametro se listan todas las ciudades")
    void listarTodasLasCiudades() throws Exception {
        when(cityServicePort.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/v1/api/cities")).andExpect(status().isOk());

        verify(cityServicePort).findAll();
    }

    @Test
    @DisplayName("una ciudad sin pais se rechaza con 400")
    void ciudadSinPais() throws Exception {
        mockMvc.perform(post("/v1/api/cities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CityCreateRequest("Bogota", null))))
                .andExpect(status().isBadRequest());

        verify(cityServicePort, never()).create(any());
    }

    @Test
    @DisplayName("crear una ciudad en un pais inexistente responde 404, no un conflicto de datos")
    void ciudadEnPaisInexistente() throws Exception {
        UUID fantasma = UUID.randomUUID();
        when(cityServicePort.create(any())).thenThrow(new CountryNotFoundException(fantasma));

        mockMvc.perform(post("/v1/api/cities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CityCreateRequest("Bogota", fantasma))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_LOCATION_001"));
    }

    @Test
    @DisplayName("PATCH de una ciudad transporta ambos campos al comando")
    void patchDeCiudad() throws Exception {
        UUID id = UUID.randomUUID();
        UUID espana = UUID.randomUUID();
        when(cityServicePort.update(any(UpdateCityCommand.class)))
                .thenReturn(City.rehydrate(id, "Cordoba", espana, true));

        mockMvc.perform(patch("/v1/api/cities/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new CityUpdateRequest("Cordoba", espana))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPais").value(espana.toString()));
    }
}
