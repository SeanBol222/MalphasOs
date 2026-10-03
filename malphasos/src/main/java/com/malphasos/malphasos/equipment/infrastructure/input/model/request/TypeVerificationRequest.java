package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Una de las cosas que se verifican en un tipo de equipo.
 *
 * <p>Un termohigrómetro manda <b>dos</b>: temperatura en °C y humedad relativa en %HR, cada una con su
 * modalidad, su cantidad de lecturas y sus puntos. Hasta el 2026-10-03 solo cabía una por tipo.
 *
 * <p>La magnitud y la unidad llegan por identificador, y se eligen del catálogo que expone
 * {@code /v1/api/magnitudes}. La unidad tiene que ser <b>de esa</b> magnitud: si no, la respuesta es un
 * 409 con código propio, porque los dos identificadores son correctos y lo que falla es la combinación.
 */
@Schema(name = "TypeVerificationRequest")
public record TypeVerificationRequest(
        @NotNull(message = "La verificacion necesita saber que magnitud mide")
        @Schema(description = "Que se mide. Del catalogo de magnitudes")
        UUID magnitudId,

        @NotNull(message = "La verificacion necesita su unidad")
        @Schema(description = "En que unidad. Tiene que ser una unidad de esa magnitud")
        UUID unidadId,

        @NotNull(message = "La verificacion necesita su modalidad")
        @Schema(description = "Como se verifica esta magnitud en este tipo de equipo")
        VerificationMode modalidad,

        @Min(value = 1, message = "Se toma al menos una lectura por punto")
        @Max(value = 100, message = "No se toman mas de 100 lecturas por punto")
        @Schema(description = "Lecturas que se toman EN CADA PUNTO. Solo con modalidad constante",
                example = "3")
        Integer cantidadDatos,

        @Valid
        @Schema(description = "Valores constantes en los que se verifica. Solo con modalidad constante")
        List<VerificationPointRequest> puntos) {
}
