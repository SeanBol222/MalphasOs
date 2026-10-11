package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.client.application.model.ServiceAreaLocation;
import com.malphasos.malphasos.equipment.application.model.overview.EquipmentOverview;
import com.malphasos.malphasos.equipment.application.model.overview.EquipmentOverviewQuery;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentOverviewServicePort;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Contrato HTTP del listado de equipos: qué parámetros llegan al servicio y qué forma tiene la respuesta. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EquipmentOverviewRestAdapterTest {

    private static final String RUTA = "/v1/api/client-equipments/overview";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private EquipmentOverviewServicePort servicio;

    private EquipmentOverviewQuery consultaRecibida() {
        ArgumentCaptor<EquipmentOverviewQuery> consulta = ArgumentCaptor.forClass(EquipmentOverviewQuery.class);
        verify(servicio).list(consulta.capture(), any());

        return consulta.getValue();
    }

    @Test
    @DisplayName("sin parametros pide la primera pagina de los equipos en servicio, sin filtros")
    void porDefecto() throws Exception {
        when(servicio.list(any(), any())).thenReturn(new EquipmentOverview(List.of(), 0, 0, 25));

        mockMvc.perform(get(RUTA)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));

        assertThat(consultaRecibida())
                .isEqualTo(new EquipmentOverviewQuery(null, null, null, null, false, 0, 25));
    }

    @Test
    @DisplayName("cada filtro llega al servicio con su nombre")
    void filtros() throws Exception {
        UUID cliente = UUID.randomUUID();
        UUID tipo = UUID.randomUUID();
        when(servicio.list(any(), any())).thenReturn(new EquipmentOverview(List.of(), 0, 2, 10));

        mockMvc.perform(get(RUTA)
                        .param("q", "balanza")
                        .param("idCliente", cliente.toString())
                        .param("idTipoEquipo", tipo.toString())
                        .param("resultado", "FUERA_DE_SERVICIO")
                        .param("dadosDeBaja", "true")
                        .param("pagina", "2")
                        .param("tamano", "10"))
                .andExpect(status().isOk());

        assertThat(consultaRecibida()).isEqualTo(new EquipmentOverviewQuery(
                "balanza", cliente, tipo, InterventionResult.FUERA_DE_SERVICIO, true, 2, 10));
    }

    @Test
    @DisplayName("los identificadores llegan como conjunto, separados por comas")
    void identificadores() throws Exception {
        UUID uno = UUID.randomUUID();
        UUID dos = UUID.randomUUID();
        when(servicio.list(any(), any())).thenReturn(new EquipmentOverview(List.of(), 0, 0, 25));

        mockMvc.perform(get(RUTA).param("ids", uno + "," + dos)).andExpect(status().isOk());

        assertThat(consultaRecibida().ids()).containsExactlyInAnyOrder(uno, dos);
    }

    @Test
    @DisplayName("un resultado que no existe responde 400 sin llegar al servicio")
    void resultadoInvalido() throws Exception {
        mockMvc.perform(get(RUTA).param("resultado", "ROTO")).andExpect(status().isBadRequest());

        verify(servicio, never()).list(any(), any());
    }

    @Test
    @DisplayName("la fila lleva la ubicacion aplanada y el ultimo servicio")
    void forma() throws Exception {
        UUID id = UUID.randomUUID();
        UUID area = UUID.randomUUID();
        UUID sede = UUID.randomUUID();
        UUID cliente = UUID.randomUUID();
        when(servicio.list(any(), any())).thenReturn(new EquipmentOverview(
                List.of(new EquipmentOverview.Row(id, "HV-CDN-0001", "WB1240337", UUID.randomUUID(), "Balanza",
                        "Kern", "PCB 3500-2", true, area, LocalDateTime.of(2026, 10, 6, 9, 42),
                        InterventionResult.OPERATIVO,
                        new ServiceAreaLocation(area, "Laboratorio", sede, "Sede principal", cliente,
                                "Clinica del Norte", "CDN"))),
                31, 0, 25));

        mockMvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(31))
                .andExpect(jsonPath("$.filas[0].id").value(id.toString()))
                .andExpect(jsonPath("$.filas[0].numeroHojaVida").value("HV-CDN-0001"))
                .andExpect(jsonPath("$.filas[0].tipoEquipo").value("Balanza"))
                .andExpect(jsonPath("$.filas[0].modelo").value("PCB 3500-2"))
                .andExpect(jsonPath("$.filas[0].cliente").value("Clinica del Norte"))
                .andExpect(jsonPath("$.filas[0].idCliente").value(cliente.toString()))
                .andExpect(jsonPath("$.filas[0].sede").value("Sede principal"))
                .andExpect(jsonPath("$.filas[0].idSede").value(sede.toString()))
                .andExpect(jsonPath("$.filas[0].areaServicio").value("Laboratorio"))
                .andExpect(jsonPath("$.filas[0].ultimoServicio").value("2026-10-06T09:42:00"))
                .andExpect(jsonPath("$.filas[0].resultado").value("OPERATIVO"));
    }
}
