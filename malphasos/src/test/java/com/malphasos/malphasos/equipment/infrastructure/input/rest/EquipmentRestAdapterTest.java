package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.client.domain.exception.ServiceAreaNotFoundException;
import com.malphasos.malphasos.equipment.application.ports.input.*;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.RegisterClientEquipmentCommand;
import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import com.malphasos.malphasos.equipment.domain.exception.CrossClientRelocationException;
import com.malphasos.malphasos.equipment.domain.exception.ModelNotFoundException;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.*;
import java.math.BigDecimal;
import java.time.LocalDate;
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

/** Contrato HTTP del catálogo de equipos. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EquipmentRestAdapterTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;

    @MockitoBean private ManufacturerServicePort manufacturerServicePort;
    @MockitoBean private BrandServicePort brandServicePort;
    @MockitoBean private EquipmentTypeServicePort equipmentTypeServicePort;
    @MockitoBean private EquipmentServicePort equipmentServicePort;
    @MockitoBean private ModelServicePort modelServicePort;
    @MockitoBean private ClientEquipmentServicePort clientEquipmentServicePort;

    private EquipmentType unTipo(VerificationMode modalidad) {
        return EquipmentType.rehydrate(UUID.randomUUID(), "Monitor", "Def", "Cuid", "Electronica",
                110, new BigDecimal("2.50"), modalidad, 150_000L, true);
    }

    @Test
    @DisplayName("POST de una marca valida responde 201")
    void crearMarca() throws Exception {
        when(brandServicePort.create(any())).thenReturn(Brand.rehydrate(UUID.randomUUID(), "Philips", true));

        mockMvc.perform(post("/v1/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new NamedRequest("Philips"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Philips"));
    }

    @Test
    @DisplayName("una marca sin nombre se rechaza sin llegar al servicio")
    void marcaSinNombre() throws Exception {
        mockMvc.perform(post("/v1/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new NamedRequest("  "))))
                .andExpect(status().isBadRequest());

        verify(brandServicePort, never()).create(any());
    }

    @Test
    @DisplayName("el tipo expone verificable derivado de la modalidad")
    void verificableDerivado() throws Exception {
        when(equipmentTypeServicePort.findAll())
                .thenReturn(List.of(unTipo(VerificationMode.PATRON_CONSTANTE)));

        mockMvc.perform(get("/v1/api/equipment-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verificable").value(true))
                .andExpect(jsonPath("$[0].modalidadVerificacion").value("PATRON_CONSTANTE"))
                .andExpect(jsonPath("$[0].amperaje").value(2.50));
    }

    @Test
    @DisplayName("sin modalidad, el tipo se expone como no verificable")
    void noVerificable() throws Exception {
        when(equipmentTypeServicePort.findAll()).thenReturn(List.of(unTipo(null)));

        mockMvc.perform(get("/v1/api/equipment-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verificable").value(false))
                .andExpect(jsonPath("$[0].modalidadVerificacion").doesNotExist());
    }

    @Test
    @DisplayName("la modalidad tiene ruta propia, y quitarla revierte el tipo")
    void quitarModalidad() throws Exception {
        UUID id = UUID.randomUUID();
        when(equipmentTypeServicePort.changeVerificationMode(any())).thenReturn(unTipo(null));

        mockMvc.perform(patch("/v1/api/equipment-types/" + id + "/verification-mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new VerificationModeRequest(null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificable").value(false));
    }

    @Test
    @DisplayName("un valor de mantenimiento negativo se rechaza")
    void valorNegativo() throws Exception {
        mockMvc.perform(post("/v1/api/equipment-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new EquipmentTypeCreateRequest(
                                "Monitor", "Def", "Cuid", "Electronica", null, null, null, -1L))))
                .andExpect(status().isBadRequest());

        verify(equipmentTypeServicePort, never()).create(any());
    }

    @Test
    @DisplayName("la asociacion marca-tipo no ofrece operacion de cambio")
    void asociacionSinPatch() throws Exception {
        // Sus dos referencias son inmutables: si esta mal se retira y se crea la correcta.
        mockMvc.perform(patch("/v1/api/equipments/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("el alta de una unidad toma el area de la ruta, no del cuerpo")
    void areaDesdeLaRuta() throws Exception {
        UUID area = UUID.randomUUID();
        UUID modelo = UUID.randomUUID();
        when(clientEquipmentServicePort.register(any())).thenReturn(ClientEquipment.rehydrate(
                UUID.randomUUID(), "SN-001", modelo, area, null, null, null, true));

        mockMvc.perform(post("/v1/api/service-areas/" + area + "/equipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ClientEquipmentRegisterRequest(
                                "SN-001", modelo, "INV-42", LocalDate.now().minusYears(1), 5_000_000L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idAreaServicio").value(area.toString()));

        ArgumentCaptor<RegisterClientEquipmentCommand> comando =
                ArgumentCaptor.forClass(RegisterClientEquipmentCommand.class);
        verify(clientEquipmentServicePort).register(comando.capture());

        assertThat(comando.getValue().idAreaServicio()).isEqualTo(area);
    }

    @Test
    @DisplayName("una fecha de compra futura se rechaza sin llegar al servicio")
    void fechaFutura() throws Exception {
        mockMvc.perform(post("/v1/api/service-areas/" + UUID.randomUUID() + "/equipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ClientEquipmentRegisterRequest(
                                "SN-001", UUID.randomUUID(), null, LocalDate.now().plusDays(1), null))))
                .andExpect(status().isBadRequest());

        verify(clientEquipmentServicePort, never()).register(any());
    }

    @Test
    @DisplayName("un area de servicio inexistente responde 404, no un conflicto de datos")
    void areaInexistente() throws Exception {
        UUID area = UUID.randomUUID();
        when(clientEquipmentServicePort.register(any()))
                .thenThrow(new ServiceAreaNotFoundException(area));

        mockMvc.perform(post("/v1/api/service-areas/" + area + "/equipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ClientEquipmentRegisterRequest(
                                "SN-001", UUID.randomUUID(), null, null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_009"));
    }

    @Test
    @DisplayName("un modelo inexistente responde 404 con el codigo del catalogo")
    void modeloInexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(modelServicePort.findById(id)).thenThrow(new ModelNotFoundException(id));

        mockMvc.perform(get("/v1/api/models/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_005"));
    }

    @Test
    @DisplayName("una regla del servicio se traduce a 400, no a 500")
    void reglaDelServicio() throws Exception {
        when(clientEquipmentServicePort.register(any()))
                .thenThrow(new IllegalArgumentException("No se puede incorporar una unidad de un modelo retirado"));

        mockMvc.perform(post("/v1/api/service-areas/" + UUID.randomUUID() + "/equipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new ClientEquipmentRegisterRequest(
                                "SN-001", UUID.randomUUID(), null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_007"));
    }

    @Test
    @DisplayName("trasladar a un area de otro cliente responde 409 con el codigo propio")
    void trasladarAAreaDeOtroCliente() throws Exception {
        UUID id = UUID.randomUUID();
        UUID areaDestino = UUID.randomUUID();
        when(clientEquipmentServicePort.relocate(any())).thenThrow(new CrossClientRelocationException(
                id, areaDestino, UUID.randomUUID(), UUID.randomUUID()));

        mockMvc.perform(patch("/v1/api/client-equipments/" + id + "/service-area/" + areaDestino))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_EQUIPMENT_010"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("los seis recursos aparecen en el grupo de OpenAPI del modulo")
    void recursosDocumentados() throws Exception {
        // Un patron de grupo que no case con ninguna ruta real no falla: deja el recurso fuera de
        // Swagger en silencio. Tres de estos seis se habian quedado fuera por esa via.
        String docs = mockMvc.perform(get("/v3/api-docs/equipment"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(docs)
                .describedAs("Todo recurso del catalogo debe estar en su grupo de OpenAPI")
                .contains("/v1/api/manufacturers")
                .contains("/v1/api/brands")
                .contains("/v1/api/equipment-types")
                .contains("/v1/api/equipments")
                .contains("/v1/api/models")
                .contains("/v1/api/client-equipments");
    }

    @Test
    @DisplayName("DELETE de una unidad responde 204 y la da de baja sin borrarla")
    void darDeBaja() throws Exception {
        mockMvc.perform(delete("/v1/api/client-equipments/" + UUID.randomUUID()))
                .andExpect(status().isNoContent());

        verify(clientEquipmentServicePort).decommission(any());
    }
}
