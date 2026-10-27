package com.malphasos.malphasos.client.application.services.manager.commands;

import com.malphasos.malphasos.client.domain.manager.ManagerType;
import java.util.UUID;

/**
 * Releva a un encargado de una sede o de un área, y solo de esa (2026-10-27).
 *
 * <p>Sustituye al traslado: con varias asignaciones, trasladar es relevar de una y asignar otra.
 */
public record RelieveManagerCommand(UUID idPersona, ManagerType tipo, UUID idAsignacion) {
}
