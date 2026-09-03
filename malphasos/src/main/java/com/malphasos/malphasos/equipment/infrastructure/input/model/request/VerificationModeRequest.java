package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import io.swagger.v3.oas.annotations.media.Schema;

/** Declara cómo se verifica un tipo, o que deja de verificarse si la modalidad es nula. */
@Schema(name = "VerificationModeRequest")
public record VerificationModeRequest(
        @Schema(description = "Ausente o nula significa que el tipo deja de verificarse")
        VerificationMode modalidad) {
}
