package com.malphasos.malphasos.client.application.services.manager.commands;

import com.malphasos.malphasos.client.domain.manager.ManagerType;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationRequest;
import java.util.UUID;

/**
 * Alta de un encargado que todavía no existe como persona en el sistema.
 *
 * <p>Es el caso habitual: el cliente presenta a quien va a responder por una sede, y esa persona no
 * está registrada. El servicio la crea primero a través del contrato que publica el módulo de
 * personas, y usa el identificador devuelto como identidad del encargado.
 *
 * <p>Transporta el {@code PersonCommunicationRequest} de ese contrato en lugar de repetir sus ocho
 * campos: es el tipo que el módulo de personas publica para exactamente esto.
 */
public record RegisterManagerCommand(
        PersonCommunicationRequest persona, ManagerType tipo, UUID idAsignacion) {
}
