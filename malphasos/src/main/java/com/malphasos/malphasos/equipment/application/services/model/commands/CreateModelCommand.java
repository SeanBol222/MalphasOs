package com.malphasos.malphasos.equipment.application.services.model.commands;

import java.util.UUID;

/**
 * Alta de un modelo.
 *
 * <p>El <b>nombre</b> es obligatorio —es lo que distingue «IdeaPad 3» de los otros portátiles de
 * Lenovo— y el registro INVIMA es opcional: se tramita después de dar de alta el modelo.
 */
public record CreateModelCommand(
        String nombre, String invima, UUID idFabricante, UUID idEquipo, TechnicalSheetCommand fichaTecnica) {

    /** Un alta sin ficha: se llena cuando alguien lea la placa. */
    public CreateModelCommand(String nombre, String invima, UUID idFabricante, UUID idEquipo) {
        this(nombre, invima, idFabricante, idEquipo, null);
    }
}
