package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import com.malphasos.malphasos.equipment.application.ports.input.VerificationStandardsServicePort;
import com.malphasos.malphasos.equipment.application.services.verificationStandards.commands.AssignVerificationStandardsCommand;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.AssignVerificationStandardsRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.VerificationStandardsResponse;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Con qué equipos patrón se hace cada verificación de un tipo de equipo (V60). Un sub-recurso del tipo,
 * como sus protocolos, y con las mismas autoridades: quien administra el catálogo de equipos decide con
 * qué se mide cada cosa.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "EquipmentType", description = "Gestion de tipos de equipo")
public class EquipmentTypeStandardsRestAdapter {

    private final VerificationStandardsServicePort verificationStandardsServicePort;

    @Operation(summary = "Listar los patrones de cada verificacion de un tipo de equipo",
            description = "Una entrada por verificacion vigente, en su orden, aunque no tenga ningun patron.")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping("/v1/api/equipment-types/{id}/reference-standards")
    public List<VerificationStandardsResponse> getAll(@PathVariable UUID id) {
        return VerificationStandardsResponse.fromList(verificationStandardsServicePort.standardsOf(id));
    }

    @Operation(summary = "Fijar los patrones de una verificacion",
            description = "La lista entera. Vacia, los quita todos. Un patron retirado no se anade, pero"
                    + " el que ya estaba puede quedarse.")
    @PreAuthorize("hasAuthority('equipment.write')")
    @PatchMapping("/v1/api/equipment-types/{id}/verifications/{idVerificacion}/reference-standards")
    public VerificationStandardsResponse assign(
            @PathVariable UUID id,
            @PathVariable UUID idVerificacion,
            @Valid @RequestBody AssignVerificationStandardsRequest request) {
        return VerificationStandardsResponse.from(verificationStandardsServicePort.assign(
                new AssignVerificationStandardsCommand(id, idVerificacion, request.patrones())));
    }
}
