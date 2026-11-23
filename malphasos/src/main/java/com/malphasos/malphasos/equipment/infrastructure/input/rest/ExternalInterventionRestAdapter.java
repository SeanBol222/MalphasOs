package com.malphasos.malphasos.equipment.infrastructure.input.rest;

import com.malphasos.malphasos.client.infrastructure.input.security.ReadScopeResolver;
import com.malphasos.malphasos.equipment.application.ports.input.ExternalInterventionServicePort;
import com.malphasos.malphasos.equipment.application.services.externalIntervention.commands.RecordExternalInterventionCommand;
import com.malphasos.malphasos.equipment.domain.externalIntervention.ExternalServiceType;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.ExternalInterventionRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.ExternalInterventionResponse;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que se le hizo a un equipo fuera de MalphasOS (V61, 2026-11-23): se anota, se lista y se retira.
 * Cuelga de la ruta del equipo, como su historial. Escribir pide {@code equipment-history.write}, que tiene
 * también el supervisor del cliente; el alcance lo limita a los equipos de sus clientes.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/client-equipments/{id}/external-interventions")
@Tag(name = "ExternalIntervention", description = "Lo que otras empresas le hicieron a un equipo")
public class ExternalInterventionRestAdapter {

    private final ExternalInterventionServicePort externalInterventionServicePort;
    private final ReadScopeResolver readScopeResolver;

    @Operation(summary = "Las intervenciones externas de un equipo", description = "De la mas reciente a la mas antigua.")
    @PreAuthorize("hasAuthority('equipment.read')")
    @GetMapping
    public List<ExternalInterventionResponse> getByEquipment(
            @Parameter(description = "Identificador del equipo instalado") @PathVariable UUID id,
            Authentication autenticacion) {
        return externalInterventionServicePort.findByEquipment(id, readScopeResolver.scopeFor(autenticacion)).stream()
                .map(ExternalInterventionResponse::from)
                .toList();
    }

    @Operation(summary = "Anotar algo que otra empresa le hizo al equipo",
            description = "400 si la fecha es futura o falta un dato; 404 si el equipo no existe o no es de un "
                    + "cliente de quien llama.")
    @PreAuthorize("hasAuthority('equipment-history.write')")
    @PostMapping
    public ResponseEntity<ExternalInterventionResponse> record(
            @Parameter(description = "Identificador del equipo instalado") @PathVariable UUID id,
            @Valid @RequestBody ExternalInterventionRequest request,
            Authentication autenticacion) {
        RecordExternalInterventionCommand command = new RecordExternalInterventionCommand(id, request.realizada(),
                tipo(request.tipoServicio()), request.descripcion(), request.empresa(),
                resultado(request.resultado()), request.documento());

        return ResponseEntity.status(HttpStatus.CREATED).body(ExternalInterventionResponse.from(
                externalInterventionServicePort.record(command, readScopeResolver.scopeFor(autenticacion))));
    }

    @Operation(summary = "Retirar una intervencion externa mal anotada", description = "No la borra.")
    @PreAuthorize("hasAuthority('equipment-history.write')")
    @DeleteMapping("/{idIntervencion}")
    public ResponseEntity<Void> deactivate(
            @Parameter(description = "Identificador del equipo instalado") @PathVariable UUID id,
            @Parameter(description = "La intervencion externa") @PathVariable UUID idIntervencion,
            Authentication autenticacion) {
        externalInterventionServicePort.deactivate(id, idIntervencion, readScopeResolver.scopeFor(autenticacion));

        return ResponseEntity.noContent().build();
    }

    private static ExternalServiceType tipo(String valor) {
        try {
            return ExternalServiceType.valueOf(valor.strip());
        } catch (IllegalArgumentException desconocido) {
            throw new IllegalArgumentException("Tipo de servicio desconocido: " + valor);
        }
    }

    private static InterventionResult resultado(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return InterventionResult.valueOf(valor.strip());
        } catch (IllegalArgumentException desconocido) {
            throw new IllegalArgumentException("Resultado desconocido: " + valor);
        }
    }
}
