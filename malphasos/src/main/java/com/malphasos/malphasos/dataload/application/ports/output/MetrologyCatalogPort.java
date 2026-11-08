package com.malphasos.malphasos.dataload.application.ports.output;

import java.util.List;
import java.util.Optional;

/** El catálogo metrológico de MalphasOS —magnitudes y sus unidades—, que una carga no crea: solo lo nombra. */
public interface MetrologyCatalogPort {

    /** Los símbolos de las unidades activas de la magnitud con ese código; vacío si la magnitud no está. */
    Optional<List<String>> unitsOf(String codigoMagnitud);
}
