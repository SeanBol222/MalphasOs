package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import com.malphasos.malphasos.equipment.application.ports.input.BrandServicePort;
import com.malphasos.malphasos.equipment.application.services.brand.commands.CreateBrandCommand;
import com.malphasos.malphasos.equipment.application.services.brand.commands.DeactivateBrandCommand;
import com.malphasos.malphasos.equipment.application.services.brand.commands.RenameBrandCommand;
import com.malphasos.malphasos.equipment.infrastructure.input.mapper.EquipmentRestMapper;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.NamedRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.BrandResponse;
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

/** API de marcas. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/brands")
@Tag(name = "Brand", description = "Gestion de marcas de equipos")
public class BrandRestAdapter {

    private final BrandServicePort brandServicePort;
    private final EquipmentRestMapper mapper;

    @Operation(summary = "Listar todas las marcas")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping
    public List<BrandResponse> getAll() {
        return mapper.toBrandList(brandServicePort.findAll());
    }

    @Operation(summary = "Obtener una marca por su identificador")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping("/{id}")
    public BrandResponse getById(@PathVariable UUID id) {
        return mapper.toResponse(brandServicePort.findById(id));
    }

    @Operation(summary = "Registrar una marca")
    @PreAuthorize("hasAuthority('equipment.write')")
    @PostMapping
    public ResponseEntity<BrandResponse> create(@Valid @RequestBody NamedRequest request) {
        BrandResponse creada = mapper.toResponse(
                brandServicePort.create(new CreateBrandCommand(request.nombre())));

        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @Operation(summary = "Cambiar el nombre de una marca")
    @PreAuthorize("hasAuthority('equipment.write')")
    @PatchMapping("/{id}")
    public BrandResponse rename(@PathVariable UUID id, @Valid @RequestBody NamedRequest request) {
        return mapper.toResponse(brandServicePort.rename(new RenameBrandCommand(id, request.nombre())));
    }

    @Operation(summary = "Retirar una marca", description = "No la borra: la deja inactiva.")
    @PreAuthorize("hasAuthority('equipment.write')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        brandServicePort.deactivate(new DeactivateBrandCommand(id));
    }
}
