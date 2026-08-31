package com.malphasos.malphasos.client.application.services.client.commands;

import com.malphasos.malphasos.client.domain.client.IdentificationType;
import java.util.UUID;

/** Alta de un cliente. El identificador lo genera el dominio. */
public record CreateClientCommand(
        String documento, IdentificationType tipoIdentificacion, String razonSocial, UUID idPais) {
}
