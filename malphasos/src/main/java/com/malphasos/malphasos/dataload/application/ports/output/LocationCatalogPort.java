package com.malphasos.malphasos.dataload.application.ports.output;

import java.util.List;

/** El catálogo de países y ciudades de MalphasOS, que una carga no crea: solo lo nombra. */
public interface LocationCatalogPort {

    /** Si hay un país activo con ese código ISO 3166-1 alfa-3. */
    boolean countryExists(String codigoIso);

    /** Los nombres de las ciudades activas de ese país, como están escritos en el catálogo. */
    List<String> citiesOf(String codigoIso);
}
