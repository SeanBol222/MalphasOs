package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import java.util.UUID;

/** Quitar un patrón de una verificación de un reporte (V30). */
public record RemoveReferenceStandardCommand(UUID id, UUID idVerificacion, UUID idEquipoPatron) {
}
