package com.malphasos.malphasos.workorder.infrastructure.input.rest;

import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.AddEquipmentToWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.AssignWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.CancelWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.ExecuteWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.RemoveEquipmentFromWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.ScheduleWorkOrderCommand;
import com.malphasos.malphasos.workorder.application.services.workOrder.commands.StartWorkOrderCommand;
import com.malphasos.malphasos.workorder.infrastructure.input.mapper.WorkOrderRestMapper;
import com.malphasos.malphasos.workorder.infrastructure.input.model.request.WorkOrderEquipmentRequest;
import com.malphasos.malphasos.workorder.infrastructure.input.model.request.WorkOrderScheduleRequest;
import com.malphasos.malphasos.workorder.infrastructure.input.model.response.WorkOrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de las órdenes de trabajo.
 *
 * <p><b>No hay operación de cambio general.</b> Una orden no se «edita»: se le añaden o quitan
 * equipos, se le asigna un ingeniero y avanza de estado. Cada uno es un hecho distinto, con su
 * ruta, su autoridad y su evento — y por eso el módulo no expone un {@code PATCH} sobre la orden
 * entera que los confundiría a todos.
 *
 * <p>Las tres autoridades del realm se reparten según lo que cada operación significa: consultar es
 * {@code work-order.read}, planificar —programar, cambiar el alcance, cancelar y avanzar— es
 * {@code work-order.write}, y poner la orden en manos de alguien es {@code work-order.assign}. Que
 * asignar tenga la suya es lo que permite que un coordinador reparta trabajo sin poder alterar lo
 * que se va a hacer.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/work-orders")
@Tag(name = "WorkOrder", description = "Mantenimientos programados y su alcance")
public class WorkOrderRestAdapter {

    private final WorkOrderServicePort workOrderServicePort;
    private final WorkOrderRestMapper mapper;

    @Operation(summary = "Listar ordenes de trabajo",
            description = "Los parametros filtran por cliente, sede, ingeniero o equipo. "
                    + "Solo se admite uno a la vez.")
    @PreAuthorize("hasAuthority('work-order.read')")
    @GetMapping
    public List<WorkOrderResponse> getAll(
            @Parameter(description = "Filtra por cliente") @RequestParam(required = false) UUID idCliente,
            @Parameter(description = "Filtra por sede") @RequestParam(required = false) UUID idSede,
            @Parameter(description = "Filtra por ingeniero asignado") @RequestParam(required = false) UUID idIngeniero,
            @Parameter(description = "Filtra por equipo incluido en el alcance") @RequestParam(required = false) UUID idEquipoCliente) {

        long filtros = java.util.stream.Stream
                .of(idCliente, idSede, idIngeniero, idEquipoCliente)
                .filter(java.util.Objects::nonNull)
                .count();

        if (filtros > 1) {
            throw new IllegalArgumentException(
                    "Solo se admite un filtro a la vez, y se recibieron " + filtros);
        }

        if (idCliente != null) {
            return mapper.toList(workOrderServicePort.findByClient(idCliente));
        }
        if (idSede != null) {
            return mapper.toList(workOrderServicePort.findByHeadquarter(idSede));
        }
        if (idIngeniero != null) {
            return mapper.toList(workOrderServicePort.findByEngineer(idIngeniero));
        }
        if (idEquipoCliente != null) {
            return mapper.toList(workOrderServicePort.findByEquipment(idEquipoCliente));
        }

        return mapper.toList(workOrderServicePort.findAll());
    }

    @Operation(summary = "Obtener una orden por su identificador")
    @PreAuthorize("hasAuthority('work-order.read')")
    @GetMapping("/{id}")
    public WorkOrderResponse getById(@PathVariable UUID id) {
        return mapper.toResponse(workOrderServicePort.findById(id));
    }

    @Operation(summary = "Programar un mantenimiento",
            description = "La orden nace creada, sin ingeniero y sin equipos. "
                    + "El cliente y la sede deben estar activos, y la sede ser de ese cliente.")
    @PreAuthorize("hasAuthority('work-order.write')")
    @PostMapping
    public ResponseEntity<WorkOrderResponse> schedule(
            @Valid @RequestBody WorkOrderScheduleRequest request) {

        WorkOrderResponse creada = mapper.toResponse(workOrderServicePort.schedule(
                new ScheduleWorkOrderCommand(
                        request.idCliente(),
                        request.idSede(),
                        request.fechaMantenimiento(),
                        request.periodicidad(),
                        request.tipoServicio())));

        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @Operation(summary = "Anadir un equipo al alcance",
            description = "El area se toma de donde esta el equipo ahora; no se envia. "
                    + "El equipo debe estar activo, en un area abierta y ser del mismo cliente.")
    @PreAuthorize("hasAuthority('work-order.write')")
    @PostMapping("/{id}/equipments")
    public ResponseEntity<WorkOrderResponse> addEquipment(
            @PathVariable UUID id, @Valid @RequestBody WorkOrderEquipmentRequest request) {

        WorkOrderResponse actualizada = mapper.toResponse(workOrderServicePort.addEquipment(
                new AddEquipmentToWorkOrderCommand(id, request.idEquipoCliente())));

        return ResponseEntity.status(HttpStatus.CREATED).body(actualizada);
    }

    @Operation(summary = "Sacar un equipo del alcance",
            description = "No borra el historial: la fila queda inactiva.")
    @PreAuthorize("hasAuthority('work-order.write')")
    @DeleteMapping("/{id}/equipments/{idEquipoCliente}")
    public WorkOrderResponse removeEquipment(
            @PathVariable UUID id, @PathVariable UUID idEquipoCliente) {

        return mapper.toResponse(workOrderServicePort.removeEquipment(
                new RemoveEquipmentFromWorkOrderCommand(id, idEquipoCliente)));
    }

    @Operation(summary = "Poner la orden en manos de un ingeniero",
            description = "La persona debe estar activa y ser de tipo ingeniero.")
    @PreAuthorize("hasAuthority('work-order.assign')")
    @PatchMapping("/{id}/engineer/{idIngeniero}")
    public WorkOrderResponse assign(@PathVariable UUID id, @PathVariable UUID idIngeniero) {
        return mapper.toResponse(
                workOrderServicePort.assign(new AssignWorkOrderCommand(id, idIngeniero)));
    }

    @Operation(summary = "Arrancar el trabajo",
            description = "Exige ingeniero asignado y al menos un equipo en el alcance.")
    @PreAuthorize("hasAuthority('work-order.write')")
    @PatchMapping("/{id}/start")
    public WorkOrderResponse start(@PathVariable UUID id) {
        return mapper.toResponse(workOrderServicePort.start(new StartWorkOrderCommand(id)));
    }

    @Operation(summary = "Dar el trabajo por terminado",
            description = "Desde aqui la orden ya no admite cambios de alcance ni de ingeniero.")
    @PreAuthorize("hasAuthority('work-order.write')")
    @PatchMapping("/{id}/execute")
    public WorkOrderResponse execute(@PathVariable UUID id) {
        return mapper.toResponse(workOrderServicePort.execute(new ExecuteWorkOrderCommand(id)));
    }

    @Operation(summary = "Cancelar una orden",
            description = "No la borra: la deja inactiva. No es lo mismo que ejecutarla.")
    @PreAuthorize("hasAuthority('work-order.write')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id) {
        workOrderServicePort.cancel(new CancelWorkOrderCommand(id));
    }
}
