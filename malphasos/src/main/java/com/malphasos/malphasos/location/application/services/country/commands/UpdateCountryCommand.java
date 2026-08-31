package com.malphasos.malphasos.location.application.services.country.commands;

import java.util.UUID;

/**
 * Petición de cambio sobre un país.
 *
 * <p>Un campo nulo significa "déjalo como está". Con eso, una actualización total y una parcial son
 * la misma operación y el API decide cuál ofrece. El original tenía dos caminos casi idénticos
 * —{@code update} y {@code patchUpdate}, con su comando propio cada uno— que terminaban llamando a
 * dos métodos del agregado que hacían lo mismo, y uno de ellos etiquetaba su evento de otra forma.
 *
 * <p>El código ISO no se puede cambiar: identifica al país frente al resto del mundo.
 */
public record UpdateCountryCommand(UUID id, String nombre) {
}
