package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import com.malphasos.malphasos.equipment.application.ports.input.ReferenceStandardServicePort;
import com.malphasos.malphasos.equipment.application.services.referenceStandard.commands.DeactivateReferenceStandardCommand;
import com.malphasos.malphasos.equipment.application.services.referenceStandard.commands.SaveReferenceStandardCommand;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.ReferenceStandardRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.ReferenceStandardResponse;
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
 * API del catálogo de equipos patrón (2026-10-14). Con las autoridades de equipos, como los protocolos:
 * quien administra el catálogo de equipos administra también los patrones con los que se verifican.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/reference-standards")
@Tag(name = "ReferenceStandard", description = "Catalogo de equipos patron")
public class ReferenceStandardRestAdapter {

    private final ReferenceStandardServicePort referenceStandardServicePort;

    @Operation(summary = "Listar los equipos patron", description = "Vigentes y retirados, por nombre.")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping
    public List<ReferenceStandardResponse> getAll() {
        return ReferenceStandardResponse.fromList(referenceStandardServicePort.findAll());
    }

    @Operation(summary = "Obtener un equipo patron por su identificador")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping("/{id}")
    public ReferenceStandardResponse getById(@PathVariable UUID id) {
        return ReferenceStandardResponse.from(referenceStandardServicePort.findById(id));
    }

    @Operation(summary = "Registrar un equipo patron",
            description = "La serie no puede repetir la de otro vigente: 409.")
    @PreAuthorize("hasAuthority('equipment.write')")
    @PostMapping
    public ResponseEntity<ReferenceStandardResponse> create(@Valid @RequestBody ReferenceStandardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ReferenceStandardResponse.from(
                referenceStandardServicePort.create(comando(null, request))));
    }

    @Operation(summary = "Corregir un equipo patron o renovar su certificado",
            description = "Los reportes que ya lo usaron no cambian: copiaron lo que tenia ese dia.")
    @PreAuthorize("hasAuthority('equipment.write')")
    @PatchMapping("/{id}")
    public ReferenceStandardResponse update(@PathVariable UUID id, @Valid @RequestBody ReferenceStandardRequest request) {
        return ReferenceStandardResponse.from(referenceStandardServicePort.update(comando(id, request)));
    }

    @Operation(summary = "Retirar un equipo patron", description = "No lo borra: deja de poder elegirse.")
    @PreAuthorize("hasAuthority('equipment.write')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        referenceStandardServicePort.deactivate(new DeactivateReferenceStandardCommand(id));
    }

    private static SaveReferenceStandardCommand comando(UUID id, ReferenceStandardRequest request) {
        return new SaveReferenceStandardCommand(id, request.nombre(), request.marca(), request.serie(),
                request.certificado(), request.vencimientoCertificado());
    }
}
