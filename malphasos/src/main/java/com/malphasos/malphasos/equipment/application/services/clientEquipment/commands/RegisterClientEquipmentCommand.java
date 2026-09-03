package com.malphasos.malphasos.equipment.application.services.clientEquipment.commands;

import java.time.LocalDate;
import java.util.UUID;

/** Incorpora una unidad al inventario de un área de servicio de un cliente. */
public record RegisterClientEquipmentCommand(
        String serie,
        UUID idModelo,
        UUID idAreaServicio,
        String numeroInventario,
        LocalDate fechaCompra,
        Long valorCompra) {
}
