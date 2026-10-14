package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import com.malphasos.malphasos.equipment.application.ports.input.ProtocolServicePort;
import com.malphasos.malphasos.equipment.application.services.protocol.commands.CreateProtocolCommand;
import com.malphasos.malphasos.equipment.application.services.protocol.commands.DeactivateProtocolCommand;
import com.malphasos.malphasos.equipment.application.services.protocol.commands.UpdateProtocolCommand;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.ProtocolRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.AssignedProtocolResponse;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.ProtocolResponse;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.ProtocolsOfTypeResponse;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API del catálogo de protocolos de mantenimiento preventivo (RF-14).
 *
 * <p>Con las autoridades de equipos y sin una propia, decidido el 2026-10-10: los protocolos describen
 * cómo se mantiene un tipo de equipo, y quien administra el catálogo de equipos administra esto.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/protocols")
@Tag(name = "Protocol", description = "Catalogo de protocolos de mantenimiento preventivo")
public class ProtocolRestAdapter {

    private final ProtocolServicePort protocolServicePort;

    @Operation(summary = "Listar todos los protocolos", description = "Vigentes y retirados, por nombre.")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping
    public List<ProtocolResponse> getAll() {
        return ProtocolResponse.fromList(protocolServicePort.findAll());
    }

    @Operation(summary = "Los protocolos de cada tipo de equipo",
            description = "Para el listado de tipos: en una peticion, los de todos los tipos que tienen "
                    + "alguno, en orden de prioridad. Un tipo sin protocolos no aparece.")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping("/by-type")
    public List<ProtocolsOfTypeResponse> getByType() {
        return protocolServicePort.protocolsByType().entrySet().stream()
                .map(entrada -> new ProtocolsOfTypeResponse(
                        entrada.getKey(), AssignedProtocolResponse.fromList(entrada.getValue())))
                .toList();
    }

    @Operation(summary = "Obtener un protocolo por su identificador")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping("/{id}")
    public ProtocolResponse getById(@PathVariable UUID id) {
        return ProtocolResponse.from(protocolServicePort.findById(id));
    }

    @Operation(summary = "Registrar un protocolo",
            description = "El nombre no puede repetir el de otro vigente sin contar mayusculas ni tildes: 409.")
    @PreAuthorize("hasAuthority('equipment.write')")
    @PostMapping
    public ResponseEntity<ProtocolResponse> create(@Valid @RequestBody ProtocolRequest request) {
        ProtocolResponse creado = ProtocolResponse.from(protocolServicePort.create(
                new CreateProtocolCommand(request.nombre(), request.descripcion())));

        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @Operation(summary = "Cambiar el nombre y la descripcion de un protocolo",
            description = "Los reportes que ya lo copiaron no cambian.")
    @PreAuthorize("hasAuthority('equipment.write')")
    @PatchMapping("/{id}")
    public ProtocolResponse update(@PathVariable UUID id, @Valid @RequestBody ProtocolRequest request) {
        return ProtocolResponse.from(protocolServicePort.update(
                new UpdateProtocolCommand(id, request.nombre(), request.descripcion())));
    }

    @Operation(summary = "Retirar un protocolo",
            description = "No lo borra. Si esta asignado a algun tipo de equipo vigente responde 409.")
    @PreAuthorize("hasAuthority('equipment.write')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        protocolServicePort.deactivate(new DeactivateProtocolCommand(id));
    }
}
