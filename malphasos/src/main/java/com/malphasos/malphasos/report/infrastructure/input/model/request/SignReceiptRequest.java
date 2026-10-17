package com.malphasos.malphasos.report.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Quien recibe el servicio firma el recibido (V39): quién es, en texto como en la entrega (V31), y la firma
 * tal como la produce el lienzo.
 */
@Schema(name = "SignReceiptRequest")
public record SignReceiptRequest(
        @NotBlank(message = "Hay que decir quien firma")
        @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres")
        @Schema(example = "Ana Perez")
        String nombre,
        @Size(max = 20, message = "La cedula no puede pasar de 20 caracteres")
        String cedula,
        @Size(max = 80, message = "El cargo no puede pasar de 80 caracteres")
        @Schema(example = "Jefe de enfermeria")
        String cargo,
        @NotBlank(message = "La firma es obligatoria")
        @Schema(description = "PNG de hasta 256 KB como data: URI", example = "data:image/png;base64,iVBORw0KGgo=")
        String imagen,
        @Schema(description = "RF-21: la misma firma en los reportes de la orden que aun no tengan, abiertos o"
                + " cerrados. Opcional; sin ella, solo este")
        Boolean aplicarALaOrden) {

    /** Sin la casilla, solo este reporte. Un {@code boolean} primitivo rechazaría el cuerpo que la omite. */
    public boolean aplicaALaOrden() {
        return Boolean.TRUE.equals(aplicarALaOrden);
    }
}
