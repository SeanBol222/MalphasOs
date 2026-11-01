package com.malphasos.malphasos.client.application.services.client.commands;

import java.util.UUID;

/**
 * Nombramiento de una persona como supervisora de un cliente, o el cambio de su cargo si ya lo era.
 *
 * @param cargo cómo se la nombra en los documentos; nulo, sin cargo
 */
public record AppointSupervisorCommand(UUID idCliente, UUID idPersona, String cargo) {
}
