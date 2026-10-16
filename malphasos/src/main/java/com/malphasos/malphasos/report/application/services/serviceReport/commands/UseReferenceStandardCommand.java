package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import java.util.UUID;

/** Elegir el patrón del catálogo contra el que se toman las lecturas de un reporte (V27). */
public record UseReferenceStandardCommand(UUID id, UUID idEquipoPatron) {
}
