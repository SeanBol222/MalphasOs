package com.malphasos.malphasos.client.application.services.headquarter.commands;

import com.malphasos.malphasos.client.domain.headquarter.Address;
import java.util.UUID;

/**
 * Cambio sobre una sede. Un campo nulo deja el valor como está.
 *
 * <p>El cliente no aparece: una sede no se traspasa entre clientes.
 */
public record UpdateHeadquarterCommand(UUID id, String nombre, Address direccion, UUID idCiudad) {
}
