package com.malphasos.malphasos.location.infrastructure.output.mapper;

import com.malphasos.malphasos.location.domain.country.Country;
import com.malphasos.malphasos.location.infrastructure.output.entities.CountryEntity;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Traduce entre el agregado y su fila.
 *
 * <p>Escrito a mano y no con MapStruct, a diferencia del resto de mappers del proyecto. MapStruct
 * construye el objeto destino por setters o por builder, y un agregado de Generación 2 no ofrece
 * ninguno de los dos a propósito: se entra por {@code create}, que registra un evento, o por
 * {@code rehydrate}, que no. Poder generarlo automáticamente exigiría abrir justo la puerta que el
 * agregado cierra. Ver [[migracion-location-hallazgos]] en el wiki.
 */
@Component
public class CountryPersistenceMapper {

    public Country toDomain(CountryEntity entity) {
        return Country.rehydrate(
                entity.getId(), entity.getCodigoIso(), entity.getNombre(), entity.isEstadoActivo());
    }

    public List<Country> toDomainList(List<CountryEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    public CountryEntity toEntity(Country pais) {
        return new CountryEntity(
                pais.getId(), pais.getCodigoIso(), pais.getNombre(), pais.isEstadoActivo());
    }
}
