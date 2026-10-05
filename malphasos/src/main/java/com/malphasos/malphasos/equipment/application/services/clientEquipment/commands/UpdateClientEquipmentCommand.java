package com.malphasos.malphasos.equipment.application.services.clientEquipment.commands;

import java.time.LocalDate;
import java.util.UUID;

/** Corrige los datos de compra y el inventario. Un campo nulo deja el valor como está. */
public record UpdateClientEquipmentCommand(
        UUID id,
        String numeroInventario,
        LocalDate fechaCompra,
        Long valorCompra,
        String codigoInterno,
        String proveedor) {

    public UpdateClientEquipmentCommand(
            UUID id, String numeroInventario, LocalDate fechaCompra, Long valorCompra) {
        this(id, numeroInventario, fechaCompra, valorCompra, null, null);
    }
}
