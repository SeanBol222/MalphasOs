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
        LocalDate vencimientoCertificado,
        @Schema(description = "Opcional", example = "MS-200")
        @Size(max = 80, message = "El modelo no puede pasar de 80 caracteres")
        String modelo,
        @Schema(description = "Opcional: el codigo del inventario de la empresa", example = "BB-PAT-003")
        @Size(max = 40, message = "El codigo interno no puede pasar de 40 caracteres")
        String codigoInterno,
        @Schema(description = "Opcional, como se imprime", example = "1 g a 2 kg")
        @Size(max = 120, message = "El rango no puede pasar de 120 caracteres")
        String rango,
        @Schema(description = "Opcional, como se imprime", example = "0,1 mg")
        @Size(max = 80, message = "La resolucion no puede pasar de 80 caracteres")
        String resolucion,
        @Schema(description = "Opcional: no posterior al vencimiento del certificado")
        LocalDate fechaCalibracion,
        @Schema(description = "Opcional", example = "Metrologia Andina S.A.S.")
        @Size(max = 120, message = "La empresa calibradora no puede pasar de 120 caracteres")
        String empresaCalibradora) {
}
