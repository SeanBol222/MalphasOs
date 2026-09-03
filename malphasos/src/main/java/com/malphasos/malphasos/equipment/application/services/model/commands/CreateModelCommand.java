package com.malphasos.malphasos.equipment.application.services.model.commands;

import java.util.UUID;

/** Alta de un modelo. El registro INVIMA es opcional: se tramita después. */
public record CreateModelCommand(String invima, UUID idFabricante, UUID idEquipo) {
}
