package com.malphasos.malphasos.bootstrap.exception.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

/**
 * Cuerpo de respuesta estándar para los errores transversales del API.
 *
 * <p>Es un record porque una respuesta de error, una vez construida, no debe modificarse:
 * describe un hecho que ya ocurrió.
 *
 * @param code      código estable del catálogo, para que el cliente identifique el error sin
 *                  depender del mensaje
 * @param message   descripción legible del error
 * @param details   detalles adicionales; en errores de validación, un mensaje por campo inválido
 * @param timestamp momento en que se construyó la respuesta
 */
@Builder
public record GlobalErrorResponse(
        String code,
        String message,
        List<String> details,
        LocalDateTime timestamp) {
}
