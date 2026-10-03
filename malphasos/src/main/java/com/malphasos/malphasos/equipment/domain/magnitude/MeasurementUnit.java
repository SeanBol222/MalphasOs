package com.malphasos.malphasos.equipment.domain.magnitude;

import java.util.UUID;

/**
 * Unidad en la que se expresa una magnitud: °C para temperatura, %HR para humedad relativa.
 *
 * <p><b>Pertenece a una magnitud, y eso no es una conveniencia de la interfaz.</b> Es lo que permite
 * ofrecer solo las unidades que tienen sentido, y lo que el esquema aprovecha: la foránea de una
 * verificación apunta al par (magnitud, unidad) a la vez, de modo que elegir %HR para una
 * verificación de temperatura es imposible de escribir y no solo de rechazar.
 *
 * <p><b>El símbolo es único dentro de su magnitud, no en toda la tabla.</b> La siembra de {@code V10}
 * lo demuestra: {@code %} es a la vez humedad relativa y concentración. Una unicidad global habría
 * obligado a inventarse un símbolo falso para una de las dos.
 *
 * @param simbolo lo que se imprime junto al número: {@code °C}
 * @param nombre  lo que se lee en una lista: {@code grado Celsius}
 */
public record MeasurementUnit(
        UUID id, UUID magnitudId, String simbolo, String nombre, boolean estadoActivo) {

    public MeasurementUnit {
        if (id == null) {
            throw new IllegalArgumentException("Una unidad necesita su identificador");
        }
        if (magnitudId == null) {
            throw new IllegalArgumentException("Una unidad pertenece a una magnitud");
        }
        if (simbolo == null || simbolo.isBlank()) {
            throw new IllegalArgumentException("Una unidad necesita su simbolo");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Una unidad necesita su nombre");
        }
    }

    /** Si esta unidad es de la magnitud indicada. Lo comprueba el servicio antes de guardar. */
    public boolean esDeLaMagnitud(UUID magnitud) {
        return magnitudId.equals(magnitud);
    }
}
