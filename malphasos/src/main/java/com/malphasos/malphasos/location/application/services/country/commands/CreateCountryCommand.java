package com.malphasos.malphasos.location.application.services.country.commands;

/**
 * Petición de alta de un país.
 *
 * <p>No lleva identificador: lo genera el dominio. En el original el comando lo transportaba, de
 * modo que quien llamara al API elegía la llave primaria del registro.
 */
public record CreateCountryCommand(String codigoIso, String nombre) {
}
