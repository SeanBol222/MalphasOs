package com.malphasos.malphasos.report.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * La verificación completa de un reporte.
 *
 * <p>Sustituye la anterior entera: una tabla de lecturas se corrige y se manda completa, no celda a
 * celda. Mandar la lista vacía no vacía la verificación, es un error — vaciar no es registrar.
 */
@Schema(name = "RecordVerificationRequest")
public record RecordVerificationRequest(
        @NotEmpty(message = "Una verificacion sin lecturas no es una verificacion")
        @Valid List<VerificationReadingRequest> lecturas) {
}
