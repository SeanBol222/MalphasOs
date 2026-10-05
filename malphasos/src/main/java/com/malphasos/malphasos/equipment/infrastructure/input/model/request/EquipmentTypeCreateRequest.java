package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Alta de un tipo de equipo.
 *
 * <p>No hay campo "verificable": lo determina la lista de verificaciones. Si trae alguna, el tipo se
 * verifica.
 *
 * <p><b>Llevaba una modalidad, una cantidad de lecturas y unos puntos sueltos</b> hasta el 2026-10-03,
 * porque se daba por supuesto que un aparato mide una sola cosa. Un termohigrómetro manda dos
 * verificaciones, y antes había que registrarlo como dos tipos de equipo.
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

        @Size(max = 250, message = "El uso no puede pasar de 250 caracteres")
        @Schema(description = "Para que se usa esta clase de equipo", example = "Pesaje de pacientes")
        String uso,
        @Size(max = 250, message = "La limpieza cotidiana no puede pasar de 250 caracteres")
        @Schema(description = "Como se limpia al terminar la jornada")
        String limpiezaCotidiana,

        @Valid
        @Schema(description = "Que se le verifica. Vacia o ausente significa que este tipo no se verifica")
        List<TypeVerificationRequest> verificaciones,

        @PositiveOrZero(message = "El valor del mantenimiento no puede ser negativo")
        long valorUnitarioMantenimiento) {
}
