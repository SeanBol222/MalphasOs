package com.malphasos.malphasos.company.infrastructure.input.errors;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

@Builder
@Schema(name = "CompanyErrorResponse")
public record CompanyErrorResponse(String code, String message, List<String> details, LocalDateTime timestamp) {

    static CompanyErrorResponse of(CompanyErrorCatalog error, List<String> details) {
        return CompanyErrorResponse.builder()
                .code(error.getCode())
                .message(error.getMessage())
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
