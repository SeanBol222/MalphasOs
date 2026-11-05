package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/** Una unidad física del inventario de un cliente. */
@Builder
@Schema(name = "ClientEquipmentResponse")
public record ClientEquipmentResponse(
        UUID id,
        String serie,
        @Schema(description = "Si la serie es la provisional que MalphasOS puso porque el equipo se registro sin "
                + "ella. Es la unica que se puede reemplazar, por la real")
        boolean serieProvisional,
        @Schema(description = "HV-<sigla>-0001, asignado al registrar el equipo y fijo")
        String numeroHojaVida,
        String numeroInventario,
        LocalDate fechaCompra,
        Long valorCompra,
        String codigoInterno,
        String proveedor,
        UUID idModelo,
        UUID idAreaServicio,
        boolean estadoActivo) {
}
