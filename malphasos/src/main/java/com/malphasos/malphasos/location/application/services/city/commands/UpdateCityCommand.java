package com.malphasos.malphasos.location.application.services.city.commands;

import java.util.UUID;

/**
 * Petición de cambio sobre una ciudad. Un campo nulo significa "déjalo como está".
 *
 * <p>Los dos cambios posibles son hechos distintos y registran eventos distintos: renombrar no
 * afecta a nadie, trasladar de país mueve la cobertura de todas las sedes que hay en la ciudad.
 */
public record UpdateCityCommand(UUID id, String nombre, UUID idPais) {
}
