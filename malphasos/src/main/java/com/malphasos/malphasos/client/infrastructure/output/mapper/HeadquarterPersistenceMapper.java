package com.malphasos.malphasos.client.infrastructure.output.mapper;

import com.malphasos.malphasos.client.domain.headquarter.Address;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.client.infrastructure.output.entities.HeadquarterEntity;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Traduce entre el agregado y su fila. A mano, porque el agregado se construye por
 * {@code rehydrate} y no ofrece setters ni builder.
 *
 * <p>Aquí además la dirección se descompone y se recompone: es un valor en el dominio y tres
 * columnas en la tabla.
 */
@Component
public class HeadquarterPersistenceMapper {

    public Headquarter toDomain(HeadquarterEntity entity) {
        return Headquarter.rehydrate(
                entity.getId(),
                entity.getNombre(),
                new Address(entity.getCalle(), entity.getCarrera(), entity.getNumero()),
                entity.getIdCliente(),
                entity.getIdCiudad(),
                entity.isEstadoActivo());
    }

    public List<Headquarter> toDomainList(List<HeadquarterEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    public HeadquarterEntity toEntity(Headquarter sede) {
        Address direccion = sede.getDireccion();

        return new HeadquarterEntity(
                sede.getId(),
                sede.getNombre(),
                direccion.calle(),
                direccion.carrera(),
                direccion.numero(),
                sede.getIdCliente(),
                sede.getIdCiudad(),
                sede.isEstadoActivo());
    }
}
