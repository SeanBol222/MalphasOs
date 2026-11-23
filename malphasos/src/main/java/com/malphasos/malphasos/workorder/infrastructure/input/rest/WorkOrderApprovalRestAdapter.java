package com.malphasos.malphasos.workorder.infrastructure.input.rest;

import com.malphasos.malphasos.client.infrastructure.input.security.ReadScopeResolver;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderApprovalServicePort;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.application.services.approval.ApprovalStart;
import com.malphasos.malphasos.workorder.application.services.approval.commands.ApproveFromAccountCommand;
import com.malphasos.malphasos.workorder.application.services.approval.commands.ApproveOnSiteCommand;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import com.malphasos.malphasos.workorder.infrastructure.input.mapper.WorkOrderRestMapper;
import com.malphasos.malphasos.workorder.infrastructure.input.model.request.WorkOrderApprovalRequest;
import com.malphasos.malphasos.workorder.infrastructure.input.model.response.ApprovalStartResponse;
import com.malphasos.malphasos.workorder.infrastructure.input.model.response.WorkOrderApprovalResponse;
import com.malphasos.malphasos.workorder.infrastructure.input.model.response.WorkOrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La aprobación de una orden por el supervisor de su cliente (V54, 2026-11-01).
 *
 * <p>Aprobar pide {@code work-order.approve}, que tienen los tres grupos: el supervisor aprueba desde su
 * cuenta, y el ingeniero o el administrador registran la aprobación en sitio. Que no sea
 * {@code work-order.write} es lo que deja al supervisor aprobar sin poder tocar la orden.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/work-orders")
@Tag(name = "WorkOrderApproval", description = "La aprobacion de una orden ejecutada por el supervisor del cliente")
public class WorkOrderApprovalRestAdapter {

    private static final String PREFIJO_PNG = "data:image/png;base64,";

    private final WorkOrderApprovalServicePort approvalServicePort;
    private final WorkOrderServicePort workOrderServicePort;
    private final WorkOrderRestMapper mapper;
    private final ReadScopeResolver readScopeResolver;
    private final ApprovalStart approvalStart;

    @Operation(summary = "La aprobacion vigente de una orden", description = "204 si no esta aprobada.")
    @PreAuthorize("hasAuthority('work-order.read')")
    @GetMapping("/{id}/approval")
    public ResponseEntity<WorkOrderApprovalResponse> getApproval(@PathVariable UUID id, Authentication autenticacion) {
        return approvalServicePort.findActive(id, readScopeResolver.scopeFor(autenticacion))
                .map(WorkOrderApprovalResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Aprobar en sitio",
            description = "El supervisor dibuja su firma en la sesion de quien registra la aprobacion. La "
                    + "orden tiene que estar ejecutada, sin otra aprobacion vigente, y quien aprueba tiene "
                    + "que supervisar a su cliente.")
    @PreAuthorize("hasAuthority('work-order.approve')")
    @PostMapping("/{id}/approval")
    public ResponseEntity<WorkOrderApprovalResponse> approveOnSite(
            @PathVariable UUID id, @Valid @RequestBody WorkOrderApprovalRequest request, Authentication autenticacion) {

        var aprobacion = approvalServicePort.approveOnSite(
                new ApproveOnSiteCommand(id, request.idSupervisor(), png(request.imagen()), sesion(autenticacion)),
                readScopeResolver.scopeFor(autenticacion));

        return ResponseEntity.status(HttpStatus.CREATED).body(WorkOrderApprovalResponse.from(aprobacion));
    }

    @Operation(summary = "Aprobar desde mi cuenta",
            description = "Quien llama es el supervisor, y se usa la firma que guardo. 409 si no tiene ninguna.")
    @PreAuthorize("hasAuthority('work-order.approve')")
    @PostMapping("/{id}/approval/mine")
    public ResponseEntity<WorkOrderApprovalResponse> approveFromAccount(
            @PathVariable UUID id, Authentication autenticacion) {

        UUID yo = sesion(autenticacion);
        if (yo == null) {
            throw new IllegalArgumentException("La sesion no corresponde a ninguna persona: no puede aprobar por si misma");
        }
        var aprobacion = approvalServicePort.approveFromAccount(
                new ApproveFromAccountCommand(id, yo), readScopeResolver.scopeFor(autenticacion));

        return ResponseEntity.status(HttpStatus.CREATED).body(WorkOrderApprovalResponse.from(aprobacion));
    }

    @Operation(summary = "Ordenes por aprobar",
            description = "Las ejecutadas, activas y sin aprobacion vigente al alcance de quien llama: a un "
                    + "supervisor, las de los clientes que supervisa.")
    @PreAuthorize("hasAuthority('work-order.read')")
    @GetMapping("/pending-approval")
    public List<WorkOrderResponse> pending(Authentication autenticacion) {
        List<WorkOrder> pendientes = approvalServicePort.pending(readScopeResolver.scopeFor(autenticacion));

        return mapper.toList(
                pendientes, workOrderServicePort.equipmentOf(pendientes.stream().map(WorkOrder::getId).toList()));
    }

    @Operation(summary = "Desde cuando se aprueban las ordenes",
            description = "El dia desde el que una orden ejecutada espera aprobacion; lo ejecutado antes, "
                    + "como el historial cargado de otro sistema, no la espera. Nulo si no hay corte.")
    @PreAuthorize("hasAuthority('work-order.read')")
    @GetMapping("/approval-start")
    public ApprovalStartResponse approvalStart() {
        return new ApprovalStartResponse(approvalStart.desde().orElse(null));
    }

    private static byte[] png(String imagen) {
        if (!imagen.startsWith(PREFIJO_PNG)) {
            throw new IllegalArgumentException("La firma tiene que llegar como " + PREFIJO_PNG + "...");
        }
        try {
            return Base64.getDecoder().decode(imagen.substring(PREFIJO_PNG.length()));
        } catch (IllegalArgumentException base64Roto) {
            throw new IllegalArgumentException("La firma no es un base64 valido");
        }
    }

    /** El {@code sub} del token como persona, o nulo si no lo es: nunca un identificador del cuerpo. */
    private static UUID sesion(Authentication autenticacion) {
        String sub = autenticacion instanceof JwtAuthenticationToken token
                ? token.getToken().getSubject()
                : autenticacion == null ? null : autenticacion.getName();
        try {
            return sub == null ? null : UUID.fromString(sub);
        } catch (IllegalArgumentException noEsUnaPersona) {
            return null;
        }
    }
}
