package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Incorpora una unidad al inventario. El área de servicio va en la ruta. */
@Schema(name = "ClientEquipmentRegisterRequest")
public record ClientEquipmentRegisterRequest(
        @NotBlank(message = "El numero de serie es obligatorio")
        @Size(max = 50) String serie,

        @NotNull(message = "El modelo es obligatorio") UUID idModelo,

        @Size(max = 50) String numeroInventario,

        @PastOrPresent(message = "Un equipo no se compro en el futuro") LocalDate fechaCompra,

        @PositiveOrZero(message = "El valor de compra no puede ser negativo") Long valorCompra,
        @Size(max = 50) @Schema(description = "El codigo que el cliente le pone a su maquina")
        String codigoInterno,
        @Size(max = 100) @Schema(description = "Quien vendio esta maquina") String proveedor) {
}
