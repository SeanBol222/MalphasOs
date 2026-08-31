package com.malphasos.malphasos.location.application.ports.output;

import com.malphasos.malphasos.location.domain.city.City;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que la capa de aplicación necesita de un almacén de ciudades.
 *
 * <p>Sin {@code delete} ni {@code update}, por los mismos motivos que en
 * {@link CountryPersistencePort}: aquí nada se borra, y el agregado ya lleva su identificador.
 */
public interface CityPersistencePort {

    List<City> findAll();

    Optional<City> findById(UUID id);

    List<City> findByCountry(UUID idPais);

    City save(City city);
}
