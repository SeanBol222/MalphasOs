package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import com.malphasos.malphasos.equipment.application.ports.input.EquipmentTypeServicePort;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.ChangeVerificationModeCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.CreateEquipmentTypeCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.DeactivateEquipmentTypeCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.UpdateEquipmentTypeCommand;
import com.malphasos.malphasos.equipment.infrastructure.input.mapper.EquipmentRestMapper;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.EquipmentTypeCreateRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.EquipmentTypeUpdateRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.VerificationModeRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.EquipmentTypeResponse;
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

/**
 * API de tipos de equipo.
 *
 * <p>La modalidad de verificación tiene ruta propia y no viaja en el {@code PATCH} general: cambia
 * lo que el tipo es, no solo sus datos. Declararla vuelve verificable el tipo; quitarla lo revierte.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/equipment-types")
@Tag(name = "EquipmentType", description = "Gestion de tipos de equipo")
public class EquipmentTypeRestAdapter {

    private final EquipmentTypeServicePort equipmentTypeServicePort;
    private final EquipmentRestMapper mapper;

    @Operation(summary = "Listar todos los tipos de equipo")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping
    public List<EquipmentTypeResponse> getAll() {
        return mapper.toEquipmentTypeList(equipmentTypeServicePort.findAll());
    }

    @Operation(summary = "Obtener un tipo por su identificador")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping("/{id}")
    public EquipmentTypeResponse getById(@PathVariable UUID id) {
        return mapper.toResponse(equipmentTypeServicePort.findById(id));
    }

    @Operation(summary = "Registrar un tipo de equipo",
            description = "Si se indica la modalidad de verificacion, el tipo queda como verificable.")
    @PreAuthorize("hasAuthority('admin.full')")
    @PostMapping
    public ResponseEntity<EquipmentTypeResponse> create(
            @Valid @RequestBody EquipmentTypeCreateRequest request) {

        EquipmentTypeResponse creado = mapper.toResponse(equipmentTypeServicePort.create(
                new CreateEquipmentTypeCommand(
                        request.nombre(),
                        request.definicionTecnica(),
                        request.recomendacionesCuidado(),
                        request.tecnologiaPredominante(),
                        request.voltaje(),
                        request.amperaje(),
                        request.modalidadVerificacion(),
                        request.valorUnitarioMantenimiento())));

        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @Operation(summary = "Cambiar las caracteristicas de un tipo",
            description = "Los campos ausentes conservan su valor. La modalidad tiene ruta propia.")
    @PreAuthorize("hasAuthority('admin.full')")
    @PatchMapping("/{id}")
    public EquipmentTypeResponse update(
            @PathVariable UUID id, @Valid @RequestBody EquipmentTypeUpdateRequest request) {

        return mapper.toResponse(equipmentTypeServicePort.update(new UpdateEquipmentTypeCommand(
                id,
                request.nombre(),
                request.definicionTecnica(),
                request.recomendacionesCuidado(),
                request.tecnologiaPredominante(),
                request.voltaje(),
                request.amperaje(),
                request.valorUnitarioMantenimiento())));
    }

    @Operation(summary = "Declarar como se verifica el tipo",
            description = "Una modalidad ausente significa que el tipo deja de verificarse.")
    @PreAuthorize("hasAuthority('admin.full')")
    @PatchMapping("/{id}/verification-mode")
    public EquipmentTypeResponse changeVerificationMode(
            @PathVariable UUID id, @Valid @RequestBody VerificationModeRequest request) {

        return mapper.toResponse(equipmentTypeServicePort.changeVerificationMode(
                new ChangeVerificationModeCommand(id, request.modalidad())));
    }

    @Operation(summary = "Retirar un tipo de equipo", description = "No lo borra: lo deja inactivo.")
    @PreAuthorize("hasAuthority('admin.full')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        equipmentTypeServicePort.deactivate(new DeactivateEquipmentTypeCommand(id));
    }
}
