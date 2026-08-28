package com.malphasos.malphasos.person.infrastructure.input.errors;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

/**
 * Cuerpo de respuesta de los errores del módulo de personas.
 *
 * <p>Comparte forma con el error transversal para que el cliente no tenga que distinguir entre
 * ambos. Es un record porque una respuesta de error no debe cambiar tras construirse.
 */
@Builder
@Schema(name = "PersonErrorResponse")
public record PersonErrorResponse(
        String code, String message, List<String> details, LocalDateTime timestamp) {

    /** Construye la respuesta a partir de una entrada del catálogo, sin detalles adicionales. */
    static PersonErrorResponse of(PersonErrorCatalog error) {
        return of(error, List.of());
    }

    static PersonErrorResponse of(PersonErrorCatalog error, List<String> details) {
        return PersonErrorResponse.builder()
                .code(error.getCode())
                .message(error.getMessage())
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
