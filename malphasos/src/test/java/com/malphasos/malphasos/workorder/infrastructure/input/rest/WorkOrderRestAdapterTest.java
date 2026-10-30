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
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderHasOpenReportsException;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.AddEquipmentToWorkOrderCommand;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderNotFoundException;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderWithoutEngineerException;
import com.malphasos.malphasos.workorder.domain.exception.WorkOrderWithoutEquipmentException;
import com.malphasos.malphasos.workorder.domain.workOrder.Periodicity;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import com.malphasos.malphasos.workorder.application.ports.output.WorkOrderReportsPort;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.workorder.infrastructure.input.model.request.WorkOrderEquipmentRequest;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.ScheduleWorkOrderCommand;
import com.malphasos.malphasos.workorder.infrastructure.input.model.request.WorkOrderScheduleRequest;
import com.malphasos.malphasos.shared.application.model.ReadScope;
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
                Periodicity.TRIMESTRAL, ServiceType.PREVENTIVO, null);
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
    @DisplayName("la respuesta lleva el numero de la orden y si esta en garantia")
    void numeroYGarantia() throws Exception {
        WorkOrder orden = WorkOrder.schedule(CLIENTE, SEDE, LocalDate.now().plusDays(1),
                Periodicity.MENSUAL, ServiceType.CORRECTIVO, true);
        when(workOrderServicePort.schedule(any())).thenReturn(orden);

        mockMvc.perform(post("/v1/api/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(unaPeticion())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(orden.numero()))
                .andExpect(jsonPath("$.garantia").value(true));
    }

    @Test
    @DisplayName("la garantia viaja al comando, y sin decirla es falsa")
    void laGarantiaViajaAlComando() throws Exception {
        when(workOrderServicePort.schedule(any())).thenReturn(unaOrden());
        org.mockito.ArgumentCaptor<ScheduleWorkOrderCommand> comando =
                org.mockito.ArgumentCaptor.forClass(ScheduleWorkOrderCommand.class);

        mockMvc.perform(post("/v1/api/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new WorkOrderScheduleRequest(CLIENTE, SEDE,
                                LocalDate.now().plusDays(1), Periodicity.MENSUAL, ServiceType.CORRECTIVO, true))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/api/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(unaPeticion())))
                .andExpect(status().isCreated());

        verify(workOrderServicePort, org.mockito.Mockito.times(2)).schedule(comando.capture());
        org.assertj.core.api.Assertions.assertThat(comando.getAllValues())
                .extracting(ScheduleWorkOrderCommand::garantia).containsExactly(true, false);
    }

    @Test
    @DisplayName("una orden sin cliente se rechaza sin llegar al servicio")
    void sinCliente() throws Exception {
        mockMvc.perform(post("/v1/api/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new WorkOrderScheduleRequest(
                                null, SEDE, LocalDate.now(), Periodicity.ANUAL,
                                ServiceType.PREVENTIVO, null))))
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

        verify(workOrderServicePort, never()).findByClient(any(), any());
    }

    @Test
    @DisplayName("una orden inexistente responde 404 con el codigo del catalogo")
    void ordenInexistente() throws Exception {
        UUID id = UUID.randomUUID();
        when(workOrderServicePort.findById(id, ReadScope.unrestricted())).thenThrow(new WorkOrderNotFoundException(id));

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
                .thenThrow(new IllegalStateException("Una orden ya ejecutada no admite empezar"));

        mockMvc.perform(patch("/v1/api/work-orders/" + id + "/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_WORK_ORDER_003"));
    }

    @Test
    @DisplayName("empezar sin ingeniero responde 409 con su propio código, no con el de estado")
    void empezarSinIngeniero() throws Exception {
        // Con el código de estado la pantalla le decía a una orden creada que una orden se inicia
        // estando creada, sin decir que faltaba el ingeniero (2026-10-26).
        UUID id = UUID.randomUUID();
        when(workOrderServicePort.start(any())).thenThrow(new WorkOrderWithoutEngineerException());

        mockMvc.perform(patch("/v1/api/work-orders/" + id + "/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_WORK_ORDER_010"));
    }

    @Test
    @DisplayName("empezar sin equipos responde 409 con su propio código")
    void empezarSinEquipos() throws Exception {
        UUID id = UUID.randomUUID();
        when(workOrderServicePort.start(any())).thenThrow(new WorkOrderWithoutEquipmentException());

        mockMvc.perform(patch("/v1/api/work-orders/" + id + "/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_WORK_ORDER_011"));
    }

    @Test
    @DisplayName("ejecutar con reportes abiertos responde 409 con su propio código y cuántos faltan")
    void reportesAbiertos() throws Exception {
        // Código propio y no el 003: la pantalla tiene que decir «faltan reportes», no «el estado no
        // lo permite», que no dice qué hacer.
        UUID id = UUID.randomUUID();
        when(workOrderServicePort.execute(any())).thenThrow(new WorkOrderHasOpenReportsException(id, 2));

        mockMvc.perform(patch("/v1/api/work-orders/" + id + "/execute"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_WORK_ORDER_009"))
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("2 equipos")));
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
    @DisplayName("los equipos de la orden son sus reportes activos, cada uno con el suyo (V50)")
    void losEquiposSonSusReportes() throws Exception {
        UUID equipo = UUID.randomUUID();
        UUID reporte = UUID.randomUUID();
        WorkOrder orden = unaOrden();
        when(workOrderServicePort.findById(orden.getId(), ReadScope.unrestricted())).thenReturn(orden);
        when(workOrderServicePort.equipmentOf(List.of(orden.getId()))).thenReturn(java.util.Map.of(orden.getId(),
                List.of(new WorkOrderReportsPort.ActiveReport(reporte, equipo, "ABC123", false))));

        mockMvc.perform(get("/v1/api/work-orders/" + orden.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipos[0].idEquipoCliente").value(equipo.toString()))
                .andExpect(jsonPath("$.equipos[0].idReporteServicio").value(reporte.toString()));
    }

    @Test
    @DisplayName("un listado pide los equipos de todas sus ordenes en una sola consulta")
    void elListadoPideLosEquiposDeUnaVez() throws Exception {
        WorkOrder una = unaOrden();
        WorkOrder otra = unaOrden();
        UUID equipo = UUID.randomUUID();
        when(workOrderServicePort.findAll(ReadScope.unrestricted())).thenReturn(List.of(una, otra));
        when(workOrderServicePort.equipmentOf(List.of(una.getId(), otra.getId()))).thenReturn(java.util.Map.of(
                otra.getId(), List.of(new WorkOrderReportsPort.ActiveReport(UUID.randomUUID(), equipo, "ABC123", true))));

        mockMvc.perform(get("/v1/api/work-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].equipos").isEmpty())
                .andExpect(jsonPath("$[1].equipos[0].idEquipoCliente").value(equipo.toString()));

        verify(workOrderServicePort, org.mockito.Mockito.times(1)).equipmentOf(any());
    }

    @Test
    @DisplayName("listar sin filtros devuelve todas")
    void listarSinFiltros() throws Exception {
        when(workOrderServicePort.findAll(ReadScope.unrestricted())).thenReturn(List.of(unaOrden()));

        mockMvc.perform(get("/v1/api/work-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estadoEjecucion").value("CREADA"));
    }

    @Test
    @DisplayName("la agenda: desde y hasta llegan al servicio como fechas, sin ingeniero")
    void agendaDeUnPeriodo() throws Exception {
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 10, 31);
        when(workOrderServicePort.findInPeriod(desde, hasta, null, ReadScope.unrestricted()))
                .thenReturn(List.of(unaOrden()));

        mockMvc.perform(get("/v1/api/work-orders").param("desde", "2026-10-01").param("hasta", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fechaMantenimiento").exists());

        verify(workOrderServicePort, never()).findAll(any());
    }

    @Test
    @DisplayName("la agenda se combina con el ingeniero: solo mis ordenes")
    void agendaDeUnIngeniero() throws Exception {
        UUID ingeniero = UUID.randomUUID();
        LocalDate desde = LocalDate.of(2026, 10, 5);
        LocalDate hasta = LocalDate.of(2026, 10, 11);
        when(workOrderServicePort.findInPeriod(desde, hasta, ingeniero, ReadScope.unrestricted()))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/api/work-orders")
                        .param("desde", "2026-10-05")
                        .param("hasta", "2026-10-11")
                        .param("idIngeniero", ingeniero.toString()))
                .andExpect(status().isOk());

        // Sin el periodo, idIngeniero solo iria a findByEngineer: que no se use prueba que gano la agenda.
        verify(workOrderServicePort).findInPeriod(desde, hasta, ingeniero, ReadScope.unrestricted());
        verify(workOrderServicePort, never()).findByEngineer(any(), any());
    }

    @Test
    @DisplayName("un periodo con cliente, sede o equipo se rechaza: solo se combina con el ingeniero")
    void agendaConOtroFiltro() throws Exception {
        for (String filtro : List.of("idCliente", "idSede", "idEquipoCliente")) {
            mockMvc.perform(get("/v1/api/work-orders")
                            .param("desde", "2026-10-01")
                            .param("hasta", "2026-10-31")
                            .param(filtro, UUID.randomUUID().toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ERR_WORK_ORDER_002"));
        }

        verify(workOrderServicePort, never()).findInPeriod(any(), any(), any(), any());
    }

    @Test
    @DisplayName("un periodo invalido que el servicio rechaza responde 400")
    void agendaInvalida() throws Exception {
        when(workOrderServicePort.findInPeriod(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1), null,
                        ReadScope.unrestricted()))
                .thenThrow(new IllegalArgumentException("El periodo termina antes de empezar"));

        mockMvc.perform(get("/v1/api/work-orders").param("desde", "2026-10-31").param("hasta", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_WORK_ORDER_002"));
    }
}
