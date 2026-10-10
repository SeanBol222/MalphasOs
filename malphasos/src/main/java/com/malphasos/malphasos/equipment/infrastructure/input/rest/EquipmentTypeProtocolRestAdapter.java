package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import com.malphasos.malphasos.equipment.application.ports.input.ProtocolServicePort;
import com.malphasos.malphasos.equipment.application.services.protocol.commands.AssignProtocolsCommand;
import com.malphasos.malphasos.equipment.domain.protocol.ProtocolAssignment;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.AssignProtocolsRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.AssignedProtocolResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los protocolos del preventivo de un tipo de equipo, en orden de prioridad.
 *
 * <p>Un controlador aparte del de tipos porque es otro agregado, y un sub-recurso del tipo porque no
 * existe sin él. Se cambia con {@code PATCH} y la lista entera: la prioridad sale de la posición.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/equipment-types/{id}/protocols")
@Tag(name = "EquipmentType", description = "Gestion de tipos de equipo")
public class EquipmentTypeProtocolRestAdapter {

    private final ProtocolServicePort protocolServicePort;

    @Operation(summary = "Listar los protocolos de un tipo de equipo, en orden de prioridad")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping
    public List<AssignedProtocolResponse> getAll(@PathVariable UUID id) {
        return AssignedProtocolResponse.fromList(protocolServicePort.protocolsOf(id));
    }

    @Operation(summary = "Fijar los protocolos de un tipo de equipo",
            description = "La lista entera, en el orden de su prioridad: el primero es la prioridad 1. "
                    + "Vacia, los quita todos.")
    @PreAuthorize("hasAuthority('equipment.write')")
    @PatchMapping
    public List<AssignedProtocolResponse> assign(
            @PathVariable UUID id, @Valid @RequestBody AssignProtocolsRequest request) {

        List<ProtocolAssignment.Requested> pedidos = request.protocolos().stream()
                .map(item -> new ProtocolAssignment.Requested(item.idProtocolo(), item.indicacion()))
                .toList();

        return AssignedProtocolResponse.fromList(
                protocolServicePort.assign(new AssignProtocolsCommand(id, pedidos)));
    }
}
