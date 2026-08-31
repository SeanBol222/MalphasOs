package com.malphasos.malphasos.location.application.ports.input;

import com.malphasos.malphasos.location.application.services.country.commands.CreateCountryCommand;
import com.malphasos.malphasos.location.application.services.country.commands.DeactivateCountryCommand;
import com.malphasos.malphasos.location.application.services.country.commands.UpdateCountryCommand;
import com.malphasos.malphasos.location.domain.country.Country;
import java.util.List;
import java.util.UUID;

/**
 * Casos de uso sobre países.
 *
 * <p>Las lecturas reciben el identificador suelto y las escrituras un comando, siguiendo el patrón
 * de {@code equipment_hexagon} que el wiki recoge como el que hay que imitar: un {@code record}
 * inmutable por operación de escritura, distinto del DTO del API.
 */
public interface CountryServicePort {

    List<Country> findAll();

    /** @throws com.malphasos.malphasos.location.domain.exception.CountryNotFoundException si no existe */
    Country findById(UUID id);

    Country create(CreateCountryCommand command);

    Country update(UpdateCountryCommand command);

    /** Retira el país sin borrarlo, conservando el historial. */
    void deactivate(DeactivateCountryCommand command);
}
