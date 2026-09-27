package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * Declara cómo se verifica un tipo, o que deja de verificarse si la modalidad es nula.
 *
 * <p><b>Los tres datos van juntos</b> —modalidad, cuántas lecturas por punto y en qué valores—, y no es
 * por comodidad: por separado existiría el instante en que un tipo dice verificarse contra un patrón
 * constante sin decir contra qué valor, y ese estado no debe poder escribirse.
 */
@Schema(name = "VerificationModeRequest")
public record VerificationModeRequest(
        @Schema(description = "Ausente o nula significa que el tipo deja de verificarse")
        VerificationMode modalidad,

        @Min(value = 1, message = "Se toma al menos una lectura por punto")
        @Max(value = 100, message = "No se toman mas de 100 lecturas por punto")
        @Schema(description = "Lecturas que se toman EN CADA PUNTO. Solo con modalidad constante",
                example = "3")
        Integer cantidadDatos,

        @Valid
        @Schema(description = "Valores constantes en los que se verifica. Solo con modalidad constante")
        List<VerificationPointRequest> puntosVerificacion) {
}
