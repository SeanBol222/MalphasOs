package com.malphasos.malphasos.equipment.domain.equipmentType;

/**
 * Modalidad con la que se hace la verificación metrológica de un tipo de equipo.
 *
 * <p>El original **no modelaba este dato**: la columna {@code n_tipo_verificacion} existía en la
 * tabla y el agregado solo tenía un booleano {@code verifiable}. La modalidad vivía únicamente en la
 * base, de modo que el dominio sabía que un tipo era verificable pero no cómo.
 */
public enum VerificationMode {

    /** Se compara contra un patrón de valor constante. */
    PATRON_CONSTANTE("patron_constante"),

    /** Se compara manteniendo constante el equipo bajo prueba. */
    EQUIPO_CONSTANTE("equipo_constante"),

    /** Patrón y equipo varían durante la verificación. */
    PATRON_EQUIPO_VARIABLE("patron_equipo_variable");

    private final String valorEnEsquema;

    VerificationMode(String valorEnEsquema) {
        this.valorEnEsquema = valorEnEsquema;
    }

    /** Valor tal como lo admite la restricción de la tabla, en minúsculas. */
    public String valorEnEsquema() {
        return valorEnEsquema;
    }

    public static VerificationMode desdeEsquema(String valor) {
        if (valor == null) {
            return null;
        }

        for (VerificationMode modo : values()) {
            if (modo.valorEnEsquema.equals(valor)) {
                return modo;
            }
        }

        throw new IllegalArgumentException("Modalidad de verificacion desconocida: " + valor);
    }
}
