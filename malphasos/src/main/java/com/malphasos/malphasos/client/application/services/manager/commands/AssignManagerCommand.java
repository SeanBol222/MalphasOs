package com.malphasos.malphasos.client.application.services.manager.commands;

import com.malphasos.malphasos.client.domain.manager.ManagerType;
import java.util.UUID;

/**
 * Pone al frente de una sede o de un área a alguien que ya existe como persona.
 *
 * <p>El original no contemplaba este camino: siempre creaba una persona nueva, de modo que un
 * ingeniero de la empresa o el representante legal de un cliente no podían figurar además como
 * encargados sin duplicarse.
 */
public record AssignManagerCommand(UUID idPersona, ManagerType tipo, UUID idAsignacion) {
}
