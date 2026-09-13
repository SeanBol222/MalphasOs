package com.malphasos.malphasos.workorder.application.services.workOrder.commands;

import java.util.UUID;

/** Arranca el trabajo. Exige ingeniero y al menos un equipo. */
public record StartWorkOrderCommand(UUID id) {
}
