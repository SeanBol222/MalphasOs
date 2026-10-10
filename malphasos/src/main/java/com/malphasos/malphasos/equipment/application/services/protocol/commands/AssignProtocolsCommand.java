package com.malphasos.malphasos.equipment.application.services.protocol.commands;

import com.malphasos.malphasos.equipment.domain.protocol.ProtocolAssignment;
import java.util.List;
import java.util.UUID;

/**
 * Los protocolos de un tipo de equipo, en el orden de su prioridad: el primero es la prioridad 1.
 * Una lista vacía los quita todos.
 */
public record AssignProtocolsCommand(UUID idTipoEquipo, List<ProtocolAssignment.Requested> protocolos) {
}
