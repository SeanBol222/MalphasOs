package com.malphasos.malphasos.client.infrastructure.output.mapper;

import com.malphasos.malphasos.client.domain.manager.Manager;
import com.malphasos.malphasos.client.domain.manager.ManagerType;
import com.malphasos.malphasos.client.infrastructure.output.entities.ManagerEntity;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Traduce entre el agregado y su fila.
 *
 * <p>Es el único mapper del módulo donde las dos formas no se corresponden campo a campo. El
 * agregado guarda <b>una</b> asignación y el tipo dice a qué apunta; la tabla tiene <b>dos</b>
 * columnas de las que solo una vale. Traducir en un sentido es elegir columna según el tipo, y en
 * el otro, quedarse con la que no está vacía.
 *
 * <p>La forma del agregado es la que no admite estados imposibles: en la tabla, sin el {@code CHECK}
 * que las ata al tipo, cabría una fila con las dos columnas llenas.
 */
@Component
public class ManagerPersistenceMapper {

    public Manager toDomain(ManagerEntity entity) {
        ManagerType tipo = ManagerType.valueOf(entity.getTipo());

        return Manager.rehydrate(
                entity.getIdPersona(),
                tipo,
                tipo == ManagerType.HEADQUARTER ? entity.getIdSede() : entity.getIdAreaServicio(),
                entity.isEstadoActivo());
    }

    public List<Manager> toDomainList(List<ManagerEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    public ManagerEntity toEntity(Manager encargado) {
        return new ManagerEntity(
                encargado.getIdPersona(),
                encargado.getTipo().name(),
                encargado.getIdSede(),
                encargado.getIdAreaServicio(),
                encargado.isEstadoActivo());
    }
}
