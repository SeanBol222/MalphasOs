package com.malphasos.malphasos.location.infrastructure.input.errors;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

@Builder
@Schema(name = "LocationErrorResponse")
public record LocationErrorResponse(
        String code, String message, List<String> details, LocalDateTime timestamp) {

    static LocationErrorResponse of(LocationErrorCatalog error, List<String> details) {
        return LocationErrorResponse.builder()
                .code(error.getCode())
                .message(error.getMessage())
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
