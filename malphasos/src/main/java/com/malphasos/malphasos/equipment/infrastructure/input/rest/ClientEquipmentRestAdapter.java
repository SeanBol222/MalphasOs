package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.DecommissionClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.RegisterClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.RelocateClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.UpdateClientEquipmentCommand;
import com.malphasos.malphasos.equipment.infrastructure.input.mapper.EquipmentRestMapper;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.ClientEquipmentRegisterRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.ClientEquipmentUpdateRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.ClientEquipmentResponse;
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
import org.springframework.web.bind.annotation.*;

/**
 * API del inventario de equipos de los clientes.
 *
 * <p>El alta cuelga de la ruta del área de servicio, porque una unidad siempre está instalada en
 * alguna. El traslado tiene ruta propia: es el hecho que más importa de una unidad, porque cambia
 * quién responde por ella.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api")
@Tag(name = "ClientEquipment", description = "Inventario de equipos de los clientes")
public class ClientEquipmentRestAdapter {

    private final ClientEquipmentServicePort clientEquipmentServicePort;
    private final EquipmentRestMapper mapper;

    @Operation(summary = "Inventario de un area de servicio")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping("/service-areas/{idAreaServicio}/equipments")
    public List<ClientEquipmentResponse> getByServiceArea(
            @Parameter(description = "Identificador del area") @PathVariable UUID idAreaServicio) {

        return mapper.toClientEquipmentList(
                clientEquipmentServicePort.findByServiceArea(idAreaServicio));
    }

    @Operation(summary = "Incorporar una unidad al inventario de un area",
            description = "El area debe estar activa y el modelo no puede estar retirado.")
    @PreAuthorize("hasAuthority('admin.full')")
    @PostMapping("/service-areas/{idAreaServicio}/equipments")
    public ResponseEntity<ClientEquipmentResponse> register(
            @PathVariable UUID idAreaServicio,
            @Valid @RequestBody ClientEquipmentRegisterRequest request) {

        ClientEquipmentResponse creada = mapper.toResponse(clientEquipmentServicePort.register(
                new RegisterClientEquipmentCommand(
                        request.serie(),
                        request.idModelo(),
                        idAreaServicio,
                        request.numeroInventario(),
                        request.fechaCompra(),
                        request.valorCompra())));

        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @Operation(summary = "Listar todas las unidades")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping("/client-equipments")
    public List<ClientEquipmentResponse> getAll() {
        return mapper.toClientEquipmentList(clientEquipmentServicePort.findAll());
    }

    @Operation(summary = "Obtener una unidad por su identificador")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping("/client-equipments/{id}")
    public ClientEquipmentResponse getById(@PathVariable UUID id) {
        return mapper.toResponse(clientEquipmentServicePort.findById(id));
    }

    @Operation(summary = "Corregir los datos de compra de una unidad",
            description = "Los campos ausentes conservan su valor.")
    @PreAuthorize("hasAuthority('admin.full')")
    @PatchMapping("/client-equipments/{id}")
    public ClientEquipmentResponse update(
            @PathVariable UUID id, @Valid @RequestBody ClientEquipmentUpdateRequest request) {

        return mapper.toResponse(clientEquipmentServicePort.update(new UpdateClientEquipmentCommand(
                id, request.numeroInventario(), request.fechaCompra(), request.valorCompra())));
    }

    @Operation(summary = "Trasladar una unidad a otra area de servicio",
            description = "El area de destino debe estar activa.")
    @PreAuthorize("hasAuthority('admin.full')")
    @PutMapping("/client-equipments/{id}/service-area/{idAreaServicio}")
    public ClientEquipmentResponse relocate(
            @PathVariable UUID id, @PathVariable UUID idAreaServicio) {

        return mapper.toResponse(clientEquipmentServicePort.relocate(
                new RelocateClientEquipmentCommand(id, idAreaServicio)));
    }

    @Operation(summary = "Dar de baja una unidad", description = "No la borra: la deja inactiva.")
    @PreAuthorize("hasAuthority('admin.full')")
    @DeleteMapping("/client-equipments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decommission(@PathVariable UUID id) {
        clientEquipmentServicePort.decommission(new DecommissionClientEquipmentCommand(id));
    }
}
