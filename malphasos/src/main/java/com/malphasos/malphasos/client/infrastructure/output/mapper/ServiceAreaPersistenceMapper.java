package com.malphasos.malphasos.client.infrastructure.output.mapper;

import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.client.infrastructure.output.entities.ServiceAreaEntity;
import java.util.List;
import org.springframework.stereotype.Component;

/** Traduce entre el agregado y su fila. */
@Component
public class ServiceAreaPersistenceMapper {

    public ServiceArea toDomain(ServiceAreaEntity entity) {
        return ServiceArea.rehydrate(
                entity.getId(), entity.getNombre(), entity.getIdSede(), entity.isEstadoActivo());
    }

    public List<ServiceArea> toDomainList(List<ServiceAreaEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    public ServiceAreaEntity toEntity(ServiceArea area) {
        return new ServiceAreaEntity(
                area.getId(), area.getNombre(), area.getIdSede(), area.isEstadoActivo());
    }
}
