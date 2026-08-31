package com.malphasos.malphasos.location.infrastructure.output.mapper;

import com.malphasos.malphasos.location.domain.city.City;
import com.malphasos.malphasos.location.infrastructure.output.entities.CityEntity;
import java.util.List;
import org.springframework.stereotype.Component;

/** Traduce entre el agregado y su fila. A mano, por el mismo motivo que su gemelo de países. */
@Component
public class CityPersistenceMapper {

    public City toDomain(CityEntity entity) {
        return City.rehydrate(
                entity.getId(), entity.getNombre(), entity.getIdPais(), entity.isEstadoActivo());
    }

    public List<City> toDomainList(List<CityEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    public CityEntity toEntity(City ciudad) {
        return new CityEntity(
                ciudad.getId(), ciudad.getNombre(), ciudad.getIdPais(), ciudad.isEstadoActivo());
    }
}
