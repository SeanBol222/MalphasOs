package com.malphasos.malphasos.equipment.infrastructure.input.errors;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

@Builder
@Schema(name = "EquipmentErrorResponse")
public record EquipmentErrorResponse(
        String code, String message, List<String> details, LocalDateTime timestamp) {

    static EquipmentErrorResponse of(EquipmentErrorCatalog error, List<String> details) {
        return EquipmentErrorResponse.builder()
                .code(error.getCode())
                .message(error.getMessage())
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
