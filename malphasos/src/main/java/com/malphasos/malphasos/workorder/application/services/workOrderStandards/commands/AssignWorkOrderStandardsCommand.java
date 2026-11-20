package com.malphasos.malphasos.workorder.application.services.workOrderStandards.commands;

import java.util.List;
import java.util.UUID;

/** Fija los patrones que la orden lleva para un tipo de equipo: la lista entera. */
public record AssignWorkOrderStandardsCommand(UUID idOrdenTrabajo, UUID idTipoEquipo, List<UUID> patrones) {
}
