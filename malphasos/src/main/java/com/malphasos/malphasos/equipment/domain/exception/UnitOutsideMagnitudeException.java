package com.malphasos.malphasos.equipment.domain.exception;

import java.util.UUID;

/**
 * La unidad existe, la magnitud existe, pero la unidad no es de esa magnitud.
 *
 * <p><b>Código propio y no «datos inválidos»</b>, igual que el traslado entre clientes: los dos
 * identificadores son correctos y las dos filas existen; lo que falla es la combinación. Un cliente del
 * API que solo mire el código tiene que poder distinguir «esa unidad no existe» de «esa unidad no sirve
 * para lo que quieres medir», porque lo que hay que corregir en la pantalla es distinto.
 */
public class UnitOutsideMagnitudeException extends RuntimeException {

    public UnitOutsideMagnitudeException(UUID unidadId, UUID magnitudId) {
        super("La unidad " + unidadId + " no pertenece a la magnitud " + magnitudId);
    }
}
