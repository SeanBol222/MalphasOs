package com.malphasos.malphasos.location.application.services.city;

import com.malphasos.malphasos.location.application.ports.input.CityServicePort;
import com.malphasos.malphasos.location.application.ports.output.CityPersistencePort;
import com.malphasos.malphasos.location.application.ports.output.CountryPersistencePort;
import com.malphasos.malphasos.location.application.services.city.commands.CreateCityCommand;
import com.malphasos.malphasos.location.application.services.city.commands.DeactivateCityCommand;
import com.malphasos.malphasos.location.application.services.city.commands.UpdateCityCommand;
import com.malphasos.malphasos.location.domain.city.City;
import com.malphasos.malphasos.location.domain.exception.CityNotFoundException;
import com.malphasos.malphasos.location.domain.exception.CountryNotFoundException;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta los casos de uso de ciudades.
 *
 * <p>Consulta el almacén de países para comprobar que el país existe antes de crear o trasladar una
 * ciudad. Es una dependencia dentro del mismo contexto acotado, no entre módulos. Podría dejarse en
 * manos de la clave foránea, pero entonces el cliente recibiría un conflicto de integridad —un 409
 * genérico— en lugar de un "ese país no existe": el motivo real quedaría enterrado en un mensaje
 * del motor de base de datos.
 */
@Service
@RequiredArgsConstructor
public class CityService implements CityServicePort {

    private final CityPersistencePort cityPersistencePort;
    private final CountryPersistencePort countryPersistencePort;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public List<City> findAll() {
        return cityPersistencePort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<City> findByCountry(UUID idPais) {
        requireCountry(idPais);

        return cityPersistencePort.findByCountry(idPais);
    }

    @Override
    @Transactional(readOnly = true)
    public City findById(UUID id) {
        return cityPersistencePort.findById(id)
                .orElseThrow(() -> new CityNotFoundException(id));
    }

    @Override
    @Transactional
    public City create(CreateCityCommand command) {
        requireCountry(command.idPais());

        return persistAndPublish(City.create(command.nombre(), command.idPais()));
    }

    @Override
    @Transactional
    public City update(UpdateCityCommand command) {
        City ciudad = findById(command.id());

        if (command.nombre() != null) {
            ciudad.rename(command.nombre());
        }
        if (command.idPais() != null) {
            requireCountry(command.idPais());
            ciudad.relocateTo(command.idPais());
        }

        return persistAndPublish(ciudad);
    }

    @Override
    @Transactional
    public void deactivate(DeactivateCityCommand command) {
        City ciudad = findById(command.id());
        ciudad.deactivate();

        persistAndPublish(ciudad);
    }

    private void requireCountry(UUID idPais) {
        if (idPais == null) {
            throw new IllegalArgumentException("Una ciudad pertenece siempre a un pais");
        }
        if (countryPersistencePort.findById(idPais).isEmpty()) {
            throw new CountryNotFoundException(idPais);
        }
    }

    private City persistAndPublish(City ciudad) {
        City guardada = cityPersistencePort.save(ciudad);
        eventDispatcherPort.dispatchAll(ciudad.pullEvents());

        return guardada;
    }
}
