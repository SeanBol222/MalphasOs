package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import com.malphasos.malphasos.equipment.application.ports.input.ModelServicePort;
import com.malphasos.malphasos.equipment.application.services.model.commands.ChangeModelInvimaCommand;
import com.malphasos.malphasos.equipment.application.services.model.commands.CreateModelCommand;
import com.malphasos.malphasos.equipment.application.services.model.commands.DeactivateModelCommand;
import com.malphasos.malphasos.equipment.infrastructure.input.mapper.EquipmentRestMapper;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.InvimaRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.ModelCreateRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.ModelResponse;
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
 * API de modelos.
 *
 * <p>Lo único que cambia de un modelo es su registro INVIMA, que se tramita después del alta. Su
 * fabricante y su asociación marca-tipo son inmutables.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/models")
@Tag(name = "Model", description = "Gestion de modelos de equipos")
public class ModelRestAdapter {

    private final ModelServicePort modelServicePort;
    private final EquipmentRestMapper mapper;

    @Operation(summary = "Listar modelos",
            description = "Con el parametro idEquipo, solo los de esa asociacion marca-tipo.")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping
    public List<ModelResponse> getAll(
            @Parameter(description = "Filtra por asociacion marca-tipo")
            @RequestParam(required = false) UUID idEquipo) {

        return mapper.toModelList(idEquipo == null
                ? modelServicePort.findAll()
                : modelServicePort.findByEquipment(idEquipo));
    }

    @Operation(summary = "Obtener un modelo por su identificador")
    @PreAuthorize("hasAuthority('admin.full')")
    @GetMapping("/{id}")
    public ModelResponse getById(@PathVariable UUID id) {
        return mapper.toResponse(modelServicePort.findById(id));
    }

    @Operation(summary = "Registrar un modelo",
            description = "La asociacion marca-tipo debe estar activa.")
    @PreAuthorize("hasAuthority('admin.full')")
    @PostMapping
    public ResponseEntity<ModelResponse> create(@Valid @RequestBody ModelCreateRequest request) {
        ModelResponse creado = mapper.toResponse(modelServicePort.create(new CreateModelCommand(
                request.invima(), request.idFabricante(), request.idEquipo())));

        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @Operation(summary = "Anotar o corregir el registro INVIMA",
            description = "Un registro ausente deja el modelo sin el.")
    @PreAuthorize("hasAuthority('admin.full')")
    @PutMapping("/{id}/invima")
    public ModelResponse changeInvima(@PathVariable UUID id, @Valid @RequestBody InvimaRequest request) {
        return mapper.toResponse(
                modelServicePort.changeInvima(new ChangeModelInvimaCommand(id, request.invima())));
    }

    @Operation(summary = "Retirar un modelo", description = "No lo borra: lo deja inactivo.")
    @PreAuthorize("hasAuthority('admin.full')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        modelServicePort.deactivate(new DeactivateModelCommand(id));
    }
}
