package com.malphasos.malphasos.dataload.infrastructure.output.existing;

import com.malphasos.malphasos.dataload.application.ports.output.LocationCatalogPort;
import com.malphasos.malphasos.location.application.ports.output.CityPersistencePort;
import com.malphasos.malphasos.location.application.ports.output.CountryPersistencePort;
import com.malphasos.malphasos.location.domain.city.City;
import com.malphasos.malphasos.location.domain.country.Country;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Lee el catálogo de ubicación con los puertos de persistencia de {@code location}, sin modificarlo. */
@Component
@RequiredArgsConstructor
public class LocationCatalogAdapter implements LocationCatalogPort {

    private final CountryPersistencePort countryPersistencePort;
    private final CityPersistencePort cityPersistencePort;

    @Override
    @Transactional(readOnly = true)
    public boolean countryExists(String codigoIso) {
        return pais(codigoIso).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> citiesOf(String codigoIso) {
        return pais(codigoIso)
                .map(pais -> cityPersistencePort.findByCountry(pais.getId()).stream()
                        .filter(City::isEstadoActivo)
                        .map(City::getNombre)
                        .toList())
                .orElse(List.of());
    }

    private Optional<Country> pais(String codigoIso) {
        return countryPersistencePort.findAll().stream()
                .filter(Country::isEstadoActivo)
                .filter(pais -> pais.getCodigoIso().equals(codigoIso))
                .findFirst();
    }
}
