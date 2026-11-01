package com.malphasos.malphasos.workorder.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** La aprobación en sitio: quién la da y la firma que dibujó. */
@Schema(name = "WorkOrderApprovalRequest")
public record WorkOrderApprovalRequest(
        @Schema(description = "El supervisor del cliente que aprueba") @NotNull UUID idSupervisor,
        @Schema(description = "La firma dibujada, como data:image/png;base64,...") @NotBlank String imagen) {
}
