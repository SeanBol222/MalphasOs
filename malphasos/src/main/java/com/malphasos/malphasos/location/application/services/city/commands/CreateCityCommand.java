package com.malphasos.malphasos.location.application.services.city.commands;

import java.util.UUID;

/** Petición de alta de una ciudad dentro de un país. El identificador lo genera el dominio. */
public record CreateCityCommand(String nombre, UUID idPais) {
}
