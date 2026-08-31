package com.malphasos.malphasos.location.infrastructure.output;

import com.malphasos.malphasos.location.application.ports.output.CountryPersistencePort;
import com.malphasos.malphasos.location.domain.country.Country;
import com.malphasos.malphasos.location.infrastructure.output.mapper.CountryPersistenceMapper;
import com.malphasos.malphasos.location.infrastructure.output.repository.CountryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementa el almacén de países sobre JPA.
 *
 * <p>{@code save} sirve tanto para el alta como para el cambio: la entidad se construye con el
 * identificador que ya trae el agregado, de modo que Hibernate la reconoce y actualiza la fila
 * existente. El original tenía además un {@code update(id, country)} que recibía el identificador
 * por separado del agregado que ya lo lleva dentro, y un {@code delete(id)} que borraba la fila de
 * verdad pese al borrado lógico del resto del sistema.
 */
@Component
@RequiredArgsConstructor
public class CountryPersistenceAdapter implements CountryPersistencePort {

    private final CountryRepository countryRepository;
    private final CountryPersistenceMapper countryPersistenceMapper;

    @Override
    public List<Country> findAll() {
        return countryPersistenceMapper.toDomainList(countryRepository.findAll());
    }

    @Override
    public Optional<Country> findById(UUID id) {
        return countryRepository.findById(id).map(countryPersistenceMapper::toDomain);
    }

    @Override
    public Country save(Country country) {
        return countryPersistenceMapper.toDomain(
                countryRepository.save(countryPersistenceMapper.toEntity(country)));
    }
}
