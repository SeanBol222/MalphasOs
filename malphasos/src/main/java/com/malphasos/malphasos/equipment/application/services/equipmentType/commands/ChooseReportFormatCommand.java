package com.malphasos.malphasos.equipment.application.services.equipmentType.commands;

import com.malphasos.malphasos.equipment.domain.equipmentType.ReportFormat;
import java.util.UUID;

/** Elegir con qué formato se imprime el reporte de un tipo de equipo (V32). */
public record ChooseReportFormatCommand(UUID id, ReportFormat formato) {
}
