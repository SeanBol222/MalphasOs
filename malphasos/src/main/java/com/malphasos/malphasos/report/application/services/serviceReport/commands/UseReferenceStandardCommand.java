package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import java.util.UUID;

/** Poner un patrón del catálogo en una verificación de un reporte (V27; por verificación desde V30). */
public record UseReferenceStandardCommand(UUID id, UUID idVerificacion, UUID idEquipoPatron) {
}
