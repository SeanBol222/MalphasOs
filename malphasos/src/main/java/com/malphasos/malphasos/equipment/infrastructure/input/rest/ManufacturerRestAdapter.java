package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import com.malphasos.malphasos.equipment.application.ports.input.ManufacturerServicePort;
import com.malphasos.malphasos.equipment.application.services.manufacturer.commands.CreateManufacturerCommand;
import com.malphasos.malphasos.equipment.application.services.manufacturer.commands.DeactivateManufacturerCommand;
import com.malphasos.malphasos.equipment.application.services.manufacturer.commands.UpdateManufacturerCommand;
import com.malphasos.malphasos.equipment.infrastructure.input.mapper.EquipmentRestMapper;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.ManufacturerRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.ManufacturerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** API de fabricantes. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/manufacturers")
@Tag(name = "Manufacturer", description = "Gestion de fabricantes de equipos")
public class ManufacturerRestAdapter {

    private final ManufacturerServicePort manufacturerServicePort;
    private final EquipmentRestMapper mapper;

    @Operation(summary = "Listar todos los fabricantes")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping
    public List<ManufacturerResponse> getAll() {
        return mapper.toManufacturerList(manufacturerServicePort.findAll());
    }

    @Operation(summary = "Obtener un fabricante por su identificador")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping("/{id}")
    public ManufacturerResponse getById(@PathVariable UUID id) {
        return mapper.toResponse(manufacturerServicePort.findById(id));
    }

    @Operation(summary = "Registrar un fabricante")
    @PreAuthorize("hasAuthority('equipment.write')")
    @PostMapping
    public ResponseEntity<ManufacturerResponse> create(@Valid @RequestBody ManufacturerRequest request) {
        ManufacturerResponse creado = mapper.toResponse(manufacturerServicePort.create(
                new CreateManufacturerCommand(request.nombre(), request.idPais())));

        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @Operation(summary = "Cambiar los datos de un fabricante",
            description = "Los campos ausentes conservan su valor.")
    @PreAuthorize("hasAuthority('equipment.write')")
    @PatchMapping("/{id}")
    public ManufacturerResponse update(
            @PathVariable UUID id, @Valid @RequestBody ManufacturerRequest request) {

        return mapper.toResponse(manufacturerServicePort.update(
                new UpdateManufacturerCommand(id, request.nombre(), request.idPais())));
    }

    @Operation(summary = "Retirar un fabricante", description = "No lo borra: lo deja inactivo.")
    @PreAuthorize("hasAuthority('equipment.write')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        manufacturerServicePort.deactivate(new DeactivateManufacturerCommand(id));
    }
}
