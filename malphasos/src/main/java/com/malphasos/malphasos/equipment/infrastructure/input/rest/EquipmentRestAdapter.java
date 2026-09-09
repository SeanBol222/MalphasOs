package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import com.malphasos.malphasos.equipment.application.ports.input.EquipmentServicePort;
import com.malphasos.malphasos.equipment.application.services.equipment.commands.CreateEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.equipment.commands.DeactivateEquipmentCommand;
import com.malphasos.malphasos.equipment.infrastructure.input.mapper.EquipmentRestMapper;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.EquipmentCreateRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.EquipmentResponse;
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
 * API de las asociaciones entre marcas y tipos de equipo.
 *
 * <p><b>No hay operación de cambio.</b> Las dos referencias de una asociación son inmutables: si
 * está equivocada se retira y se crea la correcta, porque cambiarla convertiría en mentira todos los
 * modelos que ya cuelgan de ella.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/equipments")
@Tag(name = "Equipment", description = "Asociacion entre marcas y tipos de equipo")
public class EquipmentRestAdapter {

    private final EquipmentServicePort equipmentServicePort;
    private final EquipmentRestMapper mapper;

    @Operation(summary = "Listar asociaciones",
            description = "Con el parametro idMarca, solo las de esa marca.")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping
    public List<EquipmentResponse> getAll(
            @Parameter(description = "Filtra por marca") @RequestParam(required = false) UUID idMarca) {

        return mapper.toEquipmentList(idMarca == null
                ? equipmentServicePort.findAll()
                : equipmentServicePort.findByBrand(idMarca));
    }

    @Operation(summary = "Obtener una asociacion por su identificador")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping("/{id}")
    public EquipmentResponse getById(@PathVariable UUID id) {
        return mapper.toResponse(equipmentServicePort.findById(id));
    }

    @Operation(summary = "Registrar que una marca fabrica un tipo de equipo")
    @PreAuthorize("hasAuthority('equipment.write')")
    @PostMapping
    public ResponseEntity<EquipmentResponse> create(@Valid @RequestBody EquipmentCreateRequest request) {
        EquipmentResponse creada = mapper.toResponse(equipmentServicePort.create(
                new CreateEquipmentCommand(request.idTipoEquipo(), request.idMarca())));

        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @Operation(summary = "Retirar una asociacion", description = "No la borra: la deja inactiva.")
    @PreAuthorize("hasAuthority('equipment.write')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        equipmentServicePort.deactivate(new DeactivateEquipmentCommand(id));
    }
}
