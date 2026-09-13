package com.malphasos.malphasos.workorder.infrastructure.input.errors;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

@Builder
@Schema(name = "WorkOrderErrorResponse")
public record WorkOrderErrorResponse(
        String code, String message, List<String> details, LocalDateTime timestamp) {

    static WorkOrderErrorResponse of(WorkOrderErrorCatalog error, List<String> details) {
        return WorkOrderErrorResponse.builder()
                .code(error.getCode())
                .message(error.getMessage())
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
