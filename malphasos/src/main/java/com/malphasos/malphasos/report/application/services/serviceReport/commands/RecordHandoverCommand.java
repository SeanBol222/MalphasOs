package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import java.util.UUID;

/** Quién recibió el equipo (V31). Con los tres en blanco se borra lo que hubiera. */
public record RecordHandoverCommand(UUID id, String nombre, String cedula, String cargo) {
}
