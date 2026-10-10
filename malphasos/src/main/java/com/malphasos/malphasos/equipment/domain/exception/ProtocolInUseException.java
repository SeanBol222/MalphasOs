package com.malphasos.malphasos.equipment.domain.exception;

/**
 * Un protocolo que se quiere retirar sigue asignado a tipos de equipo vigentes.
 *
 * <p>Retirarlo en silencio lo haría desaparecer del preventivo de esos tipos sin que nadie lo decidiera
 * tipo por tipo. Se pide quitarlo de ellos antes.
 */
public class ProtocolInUseException extends RuntimeException {

    public ProtocolInUseException(String nombre, long tipos) {
        super("El protocolo «" + nombre + "» está asignado a " + tipos
                + (tipos == 1 ? " tipo de equipo" : " tipos de equipo")
                + ". Quítelo de ellos antes de retirarlo");
    }
}
