package com.malphasos.malphasos.workorder.infrastructure.input.rest;

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
import com.malphasos.malphasos.client.domain.exception.HeadquarterNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.AddEquipmentToWorkOrderCommand;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderNotFoundException;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.workorder.infrastructure.input.model.request.WorkOrderEquipmentRequest;
import com.malphasos.malphasos.workorder.infrastructure.input.model.request.WorkOrderScheduleRequest;
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

/** Contrato HTTP de las órdenes de trabajo. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WorkOrderRestAdapterTest {

    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID SEDE = UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;

    @MockitoBean private WorkOrderServicePort workOrderServicePort;

    private static WorkOrder unaOrden() {
        return WorkOrder.schedule(CLIENTE, SEDE, LocalDate.now().plusDays(1),
                Periodicity.TRIMESTRAL, ServiceType.PREVENTIVO);
    }

    private static WorkOrderScheduleRequest unaPeticion() {
        return new WorkOrderScheduleRequest(CLIENTE, SEDE, LocalDate.now().plusDays(1),
                Periodicity.TRIMESTRAL, ServiceType.PREVENTIVO);
    }

    @Test
    @DisplayName("POST de una orden valida responde 201")
    void programar() throws Exception {
        when(workOrderServicePort.schedule(any())).thenReturn(unaOrden());

        mockMvc.perform(post("/v1/api/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(unaPeticion())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoEjecucion").value("CREADA"))
                .andExpect(jsonPath("$.idIngeniero").isEmpty())
                .andExpect(jsonPath("$.equipos").isEmpty());
    }

    @Test
    @DisplayName("una orden sin cliente se rechaza sin llegar al servicio")
    void sinCliente() throws Exception {
        mockMvc.perform(post("/v1/api/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new WorkOrderScheduleRequest(
                                null, SEDE, LocalDate.now(), Periodicity.ANUAL,
                                ServiceType.PREVENTIVO))))
                .andExpect(status().isBadRequest());

        verify(workOrderServicePort, never()).schedule(any());
    }

    @Test
    @DisplayName("la peticion de anadir un equipo no tiene sitio para el area")
    void elAreaNoViajaEnLaPeticion() throws Exception {
        // Es la regla mas importante de este modulo: el area la pone el servidor consultando donde
        // esta el equipo. Si el JSON la aceptara, alguien podria declarar una donde no estuvo.
        assertThat(WorkOrderEquipmentRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("idEquipoCliente");
    }

    @Test
    @DisplayName("POST de un equipo pasa al servicio solo la orden y el equipo")
    void anadirEquipo() throws Exception {
        UUID orden = UUID.randomUUID();
        UUID equipo = UUID.randomUUID();
        when(workOrderServicePort.addEquipment(any())).thenReturn(unaOrden());

        mockMvc.perform(post("/v1/api/work-orders/" + orden + "/equipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new WorkOrderEquipmentRequest(equipo))))
                .andExpect(status().isCreated());

        ArgumentCaptor<AddEquipmentToWorkOrderCommand> comando =
                ArgumentCaptor.forClass(AddEquipmentToWorkOrderCommand.class);
        verify(workOrderServicePort).addEquipment(comando.capture());

        assertThat(comando.getValue().id()).isEqualTo(orden);
        assertThat(comando.getValue().idEquipoCliente()).isEqualTo(equipo);
    }

    @Test
    @DisplayName("mas de un filtro a la vez se rechaza")
    void filtrosExcluyentes() throws Exception {
        mockMvc.perform(get("/v1/api/work-orders")
                        .param("idCliente", CLIENTE.toString())
                        .param("idSede", SEDE.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_WORK_ORDER_002"));

        verify(workOrderServicePort, never()).findByClient(any());
    }

    @Test
    @DisplayName("una orden inexistente responde 404 con el codigo del catalogo")
    void ordenInexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(workOrderServicePort.findById(id)).thenThrow(new WorkOrderNotFoundException(id));

        mockMvc.perform(get("/v1/api/work-orders/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_WORK_ORDER_001"));
    }

    @Test
    @DisplayName("cada referencia externa lleva su propio codigo, no uno compartido")
    void cadaReferenciaConSuCodigo() throws Exception {
        UUID id = UUID.randomUUID();
        when(workOrderServicePort.schedule(any()))
                .thenThrow(new HeadquarterNotFoundException(SEDE));

        mockMvc.perform(post("/v1/api/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(unaPeticion())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_WORK_ORDER_005"));

        when(workOrderServicePort.addEquipment(any()))
                .thenThrow(new ClientEquipmentNotFoundException(id));

        mockMvc.perform(post("/v1/api/work-orders/" + id + "/equipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(
                                new WorkOrderEquipmentRequest(UUID.randomUUID()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_WORK_ORDER_007"));
    }

    @Test
    @DisplayName("un estado que no admite la operacion responde 409, no 400")
    void conflictoDeEstado() throws Exception {
        // Los datos son validos y no falta ninguno: lo que choca es el momento. Compartir el codigo
        // de "datos invalidos" impediria distinguir "lo escribiste mal" de "ahora no se puede".
        UUID id = UUID.randomUUID();
        when(workOrderServicePort.start(any()))
                .thenThrow(new IllegalStateException("No se puede empezar una orden sin equipos"));

        mockMvc.perform(patch("/v1/api/work-orders/" + id + "/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_WORK_ORDER_003"));
    }

    @Test
    @DisplayName("una regla del servicio se traduce a 400, no a 500")
    void reglaDelServicio() throws Exception {
        when(workOrderServicePort.schedule(any())).thenThrow(
                new IllegalArgumentException("La sede no es del cliente"));

        mockMvc.perform(post("/v1/api/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(unaPeticion())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_WORK_ORDER_002"));
    }

    @Test
    @DisplayName("DELETE de una orden responde 204 y la cancela sin borrarla")
    void cancelar() throws Exception {
        mockMvc.perform(delete("/v1/api/work-orders/" + UUID.randomUUID()))
                .andExpect(status().isNoContent());

        verify(workOrderServicePort).cancel(any());
    }

    @Test
    @DisplayName("la orden no ofrece una operacion de cambio general")
    void sinPatchGeneral() throws Exception {
        // Una orden no se edita: se le anaden o quitan equipos, se le asigna un ingeniero y avanza
        // de estado. Cada uno es un hecho distinto y tiene su ruta.
        mockMvc.perform(patch("/v1/api/work-orders/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("el recurso aparece en el grupo de OpenAPI del modulo")
    void recursoDocumentado() throws Exception {
        // Un patron de grupo que no case con ninguna ruta no falla: deja el recurso fuera de
        // Swagger en silencio, y asi se colaron cuatro antes de que hubiera pruebas.
        String docs = mockMvc.perform(get("/v3/api-docs/work-order"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(docs).contains("/v1/api/work-orders");
    }

    @Test
    @DisplayName("el alcance se expone con el area congelada de cada equipo")
    void elAlcanceExponeElArea() throws Exception {
        UUID equipo = UUID.randomUUID();
        UUID area = UUID.randomUUID();
        WorkOrder orden = unaOrden();
        orden.addEquipment(equipo, area);
        when(workOrderServicePort.findById(orden.getId())).thenReturn(orden);

        mockMvc.perform(get("/v1/api/work-orders/" + orden.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipos[0].idEquipoCliente").value(equipo.toString()))
                .andExpect(jsonPath("$.equipos[0].idAreaServicio").value(area.toString()));
    }

    @Test
    @DisplayName("listar sin filtros devuelve todas")
    void listarSinFiltros() throws Exception {
        when(workOrderServicePort.findAll()).thenReturn(List.of(unaOrden()));

        mockMvc.perform(get("/v1/api/work-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estadoEjecucion").value("CREADA"));
    }
}
