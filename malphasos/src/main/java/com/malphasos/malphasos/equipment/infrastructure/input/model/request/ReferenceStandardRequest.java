package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Alta o cambio de un equipo patrón: van todos los campos, porque así se corrige o se renueva. */
@Schema(name = "ReferenceStandardRequest")
public record ReferenceStandardRequest(
        @Schema(example = "Juego de pesas clase F1")
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres")
        String nombre,
        @Schema(description = "Opcional", example = "Kern")
        @Size(max = 80, message = "La marca no puede pasar de 80 caracteres")
        String marca,
        @NotBlank(message = "La serie es obligatoria")
        @Size(max = 60, message = "La serie no puede pasar de 60 caracteres")
        String serie,
        @Schema(description = "Numero del certificado de calibracion", example = "CAL-2026-0412")
        @NotBlank(message = "El certificado es obligatorio")
        @Size(max = 80, message = "El certificado no puede pasar de 80 caracteres")
        String certificado,
        @Schema(description = "Hasta cuando vale el certificado")
        @NotNull(message = "El vencimiento del certificado es obligatorio")
        LocalDate vencimientoCertificado) {
}
