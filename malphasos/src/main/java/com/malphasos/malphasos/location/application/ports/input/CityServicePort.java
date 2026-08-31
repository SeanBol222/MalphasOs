package com.malphasos.malphasos.location.application.ports.input;

import com.malphasos.malphasos.location.application.services.city.commands.CreateCityCommand;
import com.malphasos.malphasos.location.application.services.city.commands.DeactivateCityCommand;
import com.malphasos.malphasos.location.application.services.city.commands.UpdateCityCommand;
import com.malphasos.malphasos.location.domain.city.City;
import java.util.List;
import java.util.UUID;

/** Casos de uso sobre ciudades. */
public interface CityServicePort {

    List<City> findAll();

    /** Ciudades de un país. Falla si el país no existe, en vez de devolver una lista vacía. */
    List<City> findByCountry(UUID idPais);

    /** @throws com.malphasos.malphasos.location.domain.exception.CityNotFoundException si no existe */
    City findById(UUID id);

    City create(CreateCityCommand command);

    City update(UpdateCityCommand command);

    /** Retira la ciudad sin borrarla, conservando el historial. */
    void deactivate(DeactivateCityCommand command);
}
