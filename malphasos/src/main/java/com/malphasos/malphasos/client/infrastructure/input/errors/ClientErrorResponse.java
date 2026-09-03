package com.malphasos.malphasos.client.infrastructure.input.errors;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

@Builder
@Schema(name = "ClientErrorResponse")
public record ClientErrorResponse(
        String code, String message, List<String> details, LocalDateTime timestamp) {

    static ClientErrorResponse of(ClientErrorCatalog error, List<String> details) {
        return ClientErrorResponse.builder()
                .code(error.getCode())
                .message(error.getMessage())
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
