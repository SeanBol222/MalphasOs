package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Corrige los datos de compra. Un campo ausente deja el valor como está. */
@Schema(name = "ClientEquipmentUpdateRequest")
public record ClientEquipmentUpdateRequest(
        @Size(max = 50) String numeroInventario,
        @PastOrPresent(message = "Un equipo no se compro en el futuro") LocalDate fechaCompra,
        @PositiveOrZero(message = "El valor de compra no puede ser negativo") Long valorCompra,
        @Size(max = 50) @Schema(description = "El codigo que el cliente le pone a su maquina")
        String codigoInterno,
        @Size(max = 100) @Schema(description = "Quien vendio esta maquina") String proveedor) {
}
