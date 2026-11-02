package com.malphasos.malphasos.report.infrastructure.input.model.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import java.util.UUID;
import com.malphasos.malphasos.report.application.services.serviceReport.commands.ReceiptScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Quien recibe el servicio firma el recibido (V39): quién es, en texto como en la entrega (V31) o, desde V55,
 * como persona registrada —el supervisor del cliente—, y la firma tal como la produce el lienzo. Una de las
 * dos, nunca las dos ni ninguna.
 */
@Schema(name = "SignReceiptRequest")
public record SignReceiptRequest(
        @Size(max = 120, message = "El nombre no puede pasar de 120 caracteres")
        @Schema(description = "Quien firma, escrito. Vacio si firma una persona registrada", example = "Ana Perez")
        String nombre,
        @Size(max = 20, message = "La cedula no puede pasar de 20 caracteres")
        String cedula,
        @Size(max = 80, message = "El cargo no puede pasar de 80 caracteres")
        @Schema(example = "Jefe de enfermeria")
        String cargo,
        @NotBlank(message = "La firma es obligatoria")
        @Schema(description = "PNG de hasta 256 KB como data: URI", example = "data:image/png;base64,iVBORw0KGgo=")
        String imagen,
        @Schema(description = "RF-21: a cuantos reportes va la misma firma. REPORTE, solo este; AREA, los de la"
                + " orden en el area de este equipo; ORDEN, todos los de la orden. En los demas solo se pone si"
                + " aun no tienen, abiertos o cerrados. Opcional; sin el, REPORTE")
        ReceiptScope alcance,
        @Schema(description = "V55: quien firma, si es una persona registrada que supervisa al cliente."
                + " Entonces nombre, cedula y cargo van vacios")
        UUID idPersonaRecibe) {

    /**
     * Quien firma tiene que decirse: su nombre escrito o la persona registrada. Se rechaza aquí, con el
     * código de datos inválidos, antes de llegar al servicio: una firma sin nadie no dice quién firmó.
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Hay que decir quien firma: su nombre o la persona registrada que recibe")
    public boolean isQuienFirmaDicho() {
        return idPersonaRecibe != null || (nombre != null && !nombre.isBlank());
    }
}
