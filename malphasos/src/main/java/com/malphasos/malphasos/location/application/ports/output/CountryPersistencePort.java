package com.malphasos.malphasos.location.application.ports.output;

import com.malphasos.malphasos.location.domain.country.Country;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que la capa de aplicación necesita de un almacén de países.
 *
 * <p>No declara {@code delete}. En este sistema nada se borra: retirar un país es guardarlo con su
 * estado en falso, y eso es {@code save}. El original sí lo declaraba, y su adaptador lo
 * implementaba con un {@code deleteById} —un borrado físico— pese a que la tabla tiene
 * {@code b_estado_activo} y a que {@code ciudad}, {@code cliente} y {@code fabricante} apuntan a
 * ella.
 *
 * <p>Tampoco declara {@code update} aparte de {@code save}: el original tenía ambos, y el primero
 * recibía el identificador por separado además del agregado que ya lo lleva dentro, con lo que
 * había dos fuentes para el mismo dato y nada que comprobara que coincidían.
 */
public interface CountryPersistencePort {

    List<Country> findAll();

    Optional<Country> findById(UUID id);

    Country save(Country country);
}
