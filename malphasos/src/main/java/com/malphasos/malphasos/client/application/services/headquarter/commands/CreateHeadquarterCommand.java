package com.malphasos.malphasos.client.application.services.headquarter.commands;

import com.malphasos.malphasos.client.domain.headquarter.Address;
import java.util.UUID;

/** Apertura de una sede. El identificador lo genera el dominio. */
public record CreateHeadquarterCommand(String nombre, Address direccion, UUID idCliente, UUID idCiudad) {
}
