package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Una de las cosas que se verifican en un tipo de equipo.
 *
 * <p><b>Trae el nombre de la magnitud y el símbolo de la unidad, no solo sus identificadores</b>, al
 * contrario que el resto de las respuestas de este módulo —ninguna trae nombres, y está anotado como
 * fricción—. Aquí se rompe con el precedente a propósito: la cabecera de una tabla de verificación dice
 * «Temperatura (°C)», y obligar a la pantalla a cruzar dos catálogos para pintar un encabezado es
 * exactamente el problema que esa fricción describe. No cuesta una consulta extra: la verificación lleva
 * las dos piezas dentro.
 *
 * <p>{@code cantidadDatos} son las lecturas <b>por punto</b>, no en total, y viene vacío con patrón y
 * equipo variables, igual que {@code puntos}.
 */
@Schema(name = "TypeVerificationResponse")
public record TypeVerificationResponse(
        UUID id,
        UUID magnitudId,
        @Schema(example = "Temperatura") String magnitud,
        UUID unidadId,
        @Schema(description = "Lo que se imprime junto al numero", example = "°C") String unidad,
        @Schema(example = "grado Celsius") String unidadNombre,
        VerificationMode modalidad,
        @Schema(description = "Lecturas por punto. Solo con modalidad constante", example = "3")
        Integer cantidadDatos,
        List<VerificationPointResponse> puntos) {
}
