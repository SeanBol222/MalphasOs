package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import com.malphasos.malphasos.equipment.application.ports.input.EquipmentTypeServicePort;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.DeclareVerificationsCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.TypeVerificationCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.VerificationPointCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.CreateEquipmentTypeCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.DeactivateEquipmentTypeCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.UpdateEquipmentTypeCommand;
import com.malphasos.malphasos.equipment.infrastructure.input.mapper.EquipmentRestMapper;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.EquipmentTypeCreateRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.EquipmentTypeUpdateRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.DeclareVerificationsRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.TypeVerificationRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.VerificationPointRequest;
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
 * <p>Las verificaciones tienen ruta propia y no viajan en el {@code PATCH} general: cambian lo que el
 * tipo <i>es</i>, no solo sus datos. Declarar alguna vuelve verificable el tipo; dejar la lista vacía lo
 * revierte.
 *
 * <p>La ruta se llamaba {@code /verification-mode} y recibía una sola modalidad. Desde el 2026-10-03 es
 * {@code /verifications} y recibe la lista: un termohigrómetro se verifica en dos magnitudes.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/equipment-types")
@Tag(name = "EquipmentType", description = "Gestion de tipos de equipo")
public class EquipmentTypeRestAdapter {

    private final EquipmentTypeServicePort equipmentTypeServicePort;
    private final EquipmentRestMapper mapper;

    @Operation(summary = "Listar todos los tipos de equipo")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping
    public List<EquipmentTypeResponse> getAll() {
        return mapper.toEquipmentTypeList(equipmentTypeServicePort.findAll());
    }

    @Operation(summary = "Obtener un tipo por su identificador")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping("/{id}")
    public EquipmentTypeResponse getById(@PathVariable UUID id) {
        return mapper.toResponse(equipmentTypeServicePort.findById(id));
    }

    @Operation(summary = "Registrar un tipo de equipo",
            description = "Si se indica alguna verificacion, el tipo queda como verificable.")
    @PreAuthorize("hasAuthority('equipment.write')")
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
                        verificacionesDe(request.verificaciones()),
                        request.valorUnitarioMantenimiento())));

        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @Operation(summary = "Cambiar las caracteristicas de un tipo",
            description = "Los campos ausentes conservan su valor. Las verificaciones tienen ruta propia.")
    @PreAuthorize("hasAuthority('equipment.write')")
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

    @Operation(summary = "Declarar que se verifica en el tipo",
            description = "Una lista vacia significa que el tipo deja de verificarse. Las verificaciones"
                    + " anteriores se retiran, no se borran.")
    @PreAuthorize("hasAuthority('equipment.write')")
    @PatchMapping("/{id}/verifications")
    public EquipmentTypeResponse declareVerifications(
            @PathVariable UUID id, @Valid @RequestBody DeclareVerificationsRequest request) {

        return mapper.toResponse(equipmentTypeServicePort.declareVerifications(
                new DeclareVerificationsCommand(id, verificacionesDe(request.verificaciones()))));
    }

    /** Traduce el cuerpo a comandos. Una lista ausente es una lista vacia, no un nulo. */
    private List<TypeVerificationCommand> verificacionesDe(List<TypeVerificationRequest> peticiones) {
        return peticiones == null
                ? List.of()
                : peticiones.stream()
                        .map(peticion -> new TypeVerificationCommand(
                                peticion.magnitudId(),
                                peticion.unidadId(),
                                peticion.modalidad(),
                                peticion.cantidadDatos(),
                                puntosDe(peticion.puntos())))
                        .toList();
    }

    private List<VerificationPointCommand> puntosDe(List<VerificationPointRequest> puntos) {
        return puntos == null
                ? List.of()
                : puntos.stream()
                        .map(punto -> new VerificationPointCommand(punto.valor()))
                        .toList();
    }

    @Operation(summary = "Retirar un tipo de equipo", description = "No lo borra: lo deja inactivo.")
    @PreAuthorize("hasAuthority('equipment.write')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        equipmentTypeServicePort.deactivate(new DeactivateEquipmentTypeCommand(id));
    }
}
