package com.malphasos.malphasos.workorder.infrastructure.input.rest;

import com.malphasos.malphasos.client.infrastructure.input.security.ReadScopeResolver;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderStandardsServicePort;
import com.malphasos.malphasos.workorder.application.services.workOrderStandards.commands.AssignWorkOrderStandardsCommand;
import com.malphasos.malphasos.workorder.domain.workOrderStandards.WorkOrderStandards;
import com.malphasos.malphasos.workorder.infrastructure.input.model.request.AssignWorkOrderStandardsRequest;
import com.malphasos.malphasos.workorder.infrastructure.input.model.response.WorkOrderStandardsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los patrones que una orden lleva a su visita, por tipo de equipo (V60, 2026-11-19).
 *
 * <p>Elegirlos es planificar lo que se va a hacer, así que es {@code work-order.write}, como añadir equipos.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/work-orders")
@Tag(name = "WorkOrder", description = "Mantenimientos programados y su alcance")
public class WorkOrderStandardsRestAdapter {

    private final WorkOrderStandardsServicePort workOrderStandardsServicePort;
    private final ReadScopeResolver readScopeResolver;

    @Operation(summary = "Los patrones de la orden por tipo de equipo",
            description = "Uno por cada tipo con equipos en la orden, con la lista vacia si no lleva ninguno.")
    @PreAuthorize("hasAuthority('work-order.read')")
    @GetMapping("/{id}/reference-standards")
    public List<WorkOrderStandardsResponse> standardsOf(@PathVariable UUID id, Authentication autenticacion) {
        return workOrderStandardsServicePort.standardsOf(id, readScopeResolver.scopeFor(autenticacion)).stream()
                .map(WorkOrderStandardsRestAdapter::responder)
                .toList();
    }

    @Operation(summary = "Fijar los patrones que la orden lleva para un tipo",
            description = "La lista entera, que sustituye la anterior. El tipo debe tener equipos en la orden, y "
                    + "cada patron estar activo y admitido por el catalogo en alguna verificacion del tipo. Los "
                    + "que se anaden llegan a los reportes abiertos de ese tipo y los que se quitan salen de ellos, "
                    + "sin tocar los que el ingeniero puso a mano. No se cambian en una orden ejecutada.")
    @PreAuthorize("hasAuthority('work-order.write')")
    @PatchMapping("/{id}/equipment-types/{idTipoEquipo}/reference-standards")
    public WorkOrderStandardsResponse assign(@PathVariable UUID id, @PathVariable UUID idTipoEquipo,
            @Valid @RequestBody AssignWorkOrderStandardsRequest request) {

        return responder(workOrderStandardsServicePort.assign(
                new AssignWorkOrderStandardsCommand(id, idTipoEquipo, request.patrones())));
    }

    private static WorkOrderStandardsResponse responder(WorkOrderStandards patrones) {
        return new WorkOrderStandardsResponse(patrones.getIdTipoEquipo(), patrones.getPatrones().stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList());
    }
}
