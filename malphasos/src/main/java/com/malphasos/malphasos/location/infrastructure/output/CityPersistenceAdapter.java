package com.malphasos.malphasos.location.infrastructure.output;

import com.malphasos.malphasos.location.application.ports.output.CityPersistencePort;
import com.malphasos.malphasos.location.domain.city.City;
import com.malphasos.malphasos.location.infrastructure.output.mapper.CityPersistenceMapper;
import com.malphasos.malphasos.location.infrastructure.output.repository.CityRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Implementa el almacén de ciudades sobre JPA. */
@Component
@RequiredArgsConstructor
public class CityPersistenceAdapter implements CityPersistencePort {

    private final CityRepository cityRepository;
    private final CityPersistenceMapper cityPersistenceMapper;

    @Override
    public List<City> findAll() {
        return cityPersistenceMapper.toDomainList(cityRepository.findAll());
    }

    @Override
    public Optional<City> findById(UUID id) {
        return cityRepository.findById(id).map(cityPersistenceMapper::toDomain);
    }

    @Override
    public List<City> findByCountry(UUID idPais) {
        return cityPersistenceMapper.toDomainList(cityRepository.findByIdPais(idPais));
    }

    @Override
    public City save(City city) {
        return cityPersistenceMapper.toDomain(
                cityRepository.save(cityPersistenceMapper.toEntity(city)));
    }
}
