package com.malphasos.malphasos.equipment.infrastructure.output.mapper;

import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.ClientEquipmentEntity;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.EquipmentEntity;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.ModelEntity;
import java.util.List;
import org.springframework.stereotype.Component;

/** Traduce entre los agregados de la cadena y sus filas. A mano, como el resto. */
@Component
public class EquipmentChainPersistenceMapper {

    public Equipment toDomain(EquipmentEntity entity) {
        return Equipment.rehydrate(
                entity.getId(), entity.getIdTipoEquipo(), entity.getIdMarca(), entity.isEstadoActivo());
    }

    public List<Equipment> toEquipmentList(List<EquipmentEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    public EquipmentEntity toEntity(Equipment equipo) {
        return new EquipmentEntity(
                equipo.getId(), equipo.getIdTipoEquipo(), equipo.getIdMarca(), equipo.isEstadoActivo());
    }

    public Model toDomain(ModelEntity entity) {
        return Model.rehydrate(entity.getId(), entity.getInvima(), entity.getIdFabricante(),
                entity.getIdEquipo(), entity.isEstadoActivo());
    }

    public List<Model> toModelList(List<ModelEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    public ModelEntity toEntity(Model modelo) {
        return new ModelEntity(modelo.getId(), modelo.getInvima(), modelo.getIdFabricante(),
                modelo.getIdEquipo(), modelo.isEstadoActivo());
    }

    public ClientEquipment toDomain(ClientEquipmentEntity entity) {
        return ClientEquipment.rehydrate(
                entity.getId(),
                entity.getSerie(),
                entity.getIdModelo(),
                entity.getIdAreaServicio(),
                entity.getNumeroInventario(),
                entity.getFechaCompra(),
                entity.getValorCompra(),
                entity.isEstadoActivo());
    }

    public List<ClientEquipment> toClientEquipmentList(List<ClientEquipmentEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    public ClientEquipmentEntity toEntity(ClientEquipment unidad) {
        return new ClientEquipmentEntity(
                unidad.getId(),
                unidad.getSerie(),
                unidad.getNumeroInventario(),
                unidad.getFechaCompra(),
                unidad.getValorCompra(),
                unidad.getIdModelo(),
                unidad.getIdAreaServicio(),
                unidad.isEstadoActivo());
    }
}
