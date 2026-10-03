package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;

/**
 * Declara qué se verifica en un tipo de equipo, o que deja de verificarse si la lista llega vacía.
 *
 * <p><b>Se manda la lista entera y no una verificación suelta</b>, y no es por comodidad: por separado
 * existiría el instante en que un tipo dice verificar temperatura contra un patrón constante sin decir
 * contra qué valor, y ese estado no debe poder escribirse. Las anteriores se retiran, no se borran: con
 * ellas se firmaron reportes.
 *
 * <p>Se llamaba {@code VerificationModeRequest} y llevaba una sola modalidad con sus puntos. Dejó de
 * servir el 2026-10-03, cuando un tipo pasó a verificarse en varias magnitudes.
 */
@Schema(name = "DeclareVerificationsRequest")
public record DeclareVerificationsRequest(
        @Valid
        @Schema(description = "Una lista vacia o ausente significa que el tipo deja de verificarse")
        List<TypeVerificationRequest> verificaciones) {
}
