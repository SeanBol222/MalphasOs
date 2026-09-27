package com.malphasos.malphasos.report.infrastructure.input.errors;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

@Builder
@Schema(name = "ServiceReportErrorResponse")
public record ServiceReportErrorResponse(
        String code, String message, List<String> details, LocalDateTime timestamp) {

    static ServiceReportErrorResponse of(ServiceReportErrorCatalog error, List<String> details) {
        return ServiceReportErrorResponse.builder()
                .code(error.getCode())
                .message(error.getMessage())
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
