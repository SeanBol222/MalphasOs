package com.malphasos.malphasos.dataload.application.exception;

import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;

/**
 * Una carga validada que MalphasOS rechazó al escribirla: una regla de un agregado o de la base que la
 * validación no ve. La transacción se deshace y no queda nada escrito; el problema dice dónde fue.
 */
public class DataLoadRejectedException extends RuntimeException {

    private final transient DataLoadProblem problema;

    public DataLoadRejectedException(DataLoadProblem problema, Throwable causa) {
        super(problema.mensaje(), causa);
        this.problema = problema;
    }

    public DataLoadProblem problema() {
        return problema;
    }
}
