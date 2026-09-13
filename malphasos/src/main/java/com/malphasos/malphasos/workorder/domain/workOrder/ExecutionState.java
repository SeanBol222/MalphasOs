package com.malphasos.malphasos.workorder.domain.workOrder;

/**
 * En qué punto de su vida está una orden de trabajo.
 *
 * <p>El esquema fija con un {@code CHECK} <b>qué valores existen</b>, pero no puede fijar <b>cómo se
 * pasa de uno a otro</b>: una restricción de columna mira la fila que se escribe, no la que había
 * antes. Por eso el avance vive aquí, y el agregado lo consulta antes de cambiar de estado.
 *
 * <p>El recorrido es en un solo sentido y sin saltos. Una orden ejecutada no vuelve a ejecución,
 * porque el trabajo ya se hizo y su registro es historia; si hace falta intervenir otra vez, se
 * crea otra orden. Y no se pasa de creada a ejecutada de golpe, porque entonces nadie sabría cuándo
 * empezó.
 */
public enum ExecutionState {

    /** Programada, todavía sin empezar. Es el estado en que nace toda orden. */
    CREADA,

    /** El ingeniero está trabajando sobre los equipos de la orden. */
    EN_EJECUCION,

    /** El trabajo terminó. Es un estado final: desde aquí no se avanza ni se retrocede. */
    EJECUTADA;

    /**
     * El estado que sigue a este, o {@code null} si es final.
     *
     * <p>Se declara aquí y no en el agregado para que la secuencia se lea de un vistazo en el mismo
     * sitio donde están los valores.
     */
    public ExecutionState siguiente() {
        return switch (this) {
            case CREADA -> EN_EJECUCION;
            case EN_EJECUCION -> EJECUTADA;
            case EJECUTADA -> null;
        };
    }

    /** Si desde este estado se puede avanzar al indicado. */
    public boolean avanzaA(ExecutionState destino) {
        return destino != null && destino == siguiente();
    }

    /** Si la orden ya terminó y no admite más cambios. */
    public boolean esFinal() {
        return siguiente() == null;
    }
}
