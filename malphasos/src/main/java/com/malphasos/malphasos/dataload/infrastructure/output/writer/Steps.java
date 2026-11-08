package com.malphasos.malphasos.dataload.infrastructure.output.writer;

import com.malphasos.malphasos.dataload.application.exception.DataLoadRejectedException;
import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import java.util.function.Supplier;

/**
 * Cada escritura de la carga, con el sitio del archivo del que sale: si un agregado la rechaza, el
 * rechazo dice qué registro fue y por qué, en vez de una excepción sin contexto.
 */
final class Steps {

    private Steps() {}

    static <T> T paso(String archivo, String seccion, int indice, Supplier<T> escritura) {
        try {
            return escritura.get();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw rechazo(archivo, seccion + indice, "MalphasOS no lo aceptó: " + e.getMessage());
        }
    }

    static DataLoadRejectedException rechazo(String archivo, String ruta, String mensaje) {
        return new DataLoadRejectedException(new DataLoadProblem(archivo, ruta, mensaje), null);
    }
}
