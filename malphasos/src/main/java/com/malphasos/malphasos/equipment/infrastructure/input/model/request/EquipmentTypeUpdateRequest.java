package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Cambio de las características de un tipo. Un campo ausente deja el valor como está.
 *
 * <p>La modalidad de verificación no aparece: tiene ruta propia porque cambia lo que el tipo es.
 */
@Schema(name = "EquipmentTypeUpdateRequest")
public record EquipmentTypeUpdateRequest(
        @Size(max = 50) String nombre,
        @Size(max = 250) String definicionTecnica,
        @Size(max = 250) String recomendacionesCuidado,
        @Size(max = 50) String tecnologiaPredominante,
        @Size(max = 250) @Schema(description = "Para que se usa; en blanco lo vacia") String uso,
        @Size(max = 250) @Schema(description = "Como se limpia al terminar la jornada; en blanco la vacia")
        String limpiezaCotidiana,
        @PositiveOrZero(message = "El valor del mantenimiento no puede ser negativo")
        Long valorUnitarioMantenimiento) {
}
