package com.malphasos.malphasos.equipment.application.services.model.commands;

import java.util.UUID;

/** Anota o corrige el registro INVIMA de un modelo. Un valor nulo lo deja sin registro. */
public record ChangeModelInvimaCommand(UUID id, String invima) {
}
