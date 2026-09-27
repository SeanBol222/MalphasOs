package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Alta de un tipo de equipo.
 *
 * <p>No hay campo "verificable": lo determina la modalidad. Si viene, el tipo se verifica.
 *
 * <p>La cantidad de lecturas y los puntos van con la modalidad porque son la misma decisión. Las
 * modalidades constantes los exigen; la variable no los admite, porque no hay nada constante que
 * declarar y cuántas lecturas tomar lo decide el ingeniero en campo.
 */
@Schema(name = "EquipmentTypeCreateRequest")
public record EquipmentTypeCreateRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 50, message = "El nombre no puede pasar de 50 caracteres")
        String nombre,

        @NotBlank(message = "La definicion tecnica es obligatoria")
        @Size(max = 250, message = "La definicion no puede pasar de 250 caracteres")
        String definicionTecnica,

        @NotBlank(message = "Las recomendaciones de cuidado son obligatorias")
        @Size(max = 250, message = "Las recomendaciones no pueden pasar de 250 caracteres")
        String recomendacionesCuidado,

        @NotBlank(message = "La tecnologia predominante es obligatoria")
        @Size(max = 50, message = "La tecnologia no puede pasar de 50 caracteres")
        String tecnologiaPredominante,

        Integer voltaje,
        BigDecimal amperaje,

        @Schema(description = "Como se verifica. Ausente significa que este tipo no se verifica")
        VerificationMode modalidadVerificacion,

        @Min(value = 1, message = "Se toma al menos una lectura por punto")
        @Max(value = 100, message = "No se toman mas de 100 lecturas por punto")
        @Schema(description = "Lecturas que se toman EN CADA PUNTO. Solo con modalidad constante",
                example = "3")
        Integer cantidadDatos,

        @Valid
        @Schema(description = "Valores constantes en los que se verifica. Solo con modalidad constante")
        List<VerificationPointRequest> puntosVerificacion,

        @PositiveOrZero(message = "El valor del mantenimiento no puede ser negativo")
        long valorUnitarioMantenimiento) {
}
