package com.malphasos.malphasos.equipment.infrastructure.output.mapper;

import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.equipment.domain.model.RiskClass;
import com.malphasos.malphasos.equipment.domain.model.TechnicalSheet;
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
        return Model.rehydrate(entity.getId(), entity.getNombre(), entity.getInvima(),
                entity.getIdFabricante(), entity.getIdEquipo(), fichaDe(entity), entity.isEstadoActivo());
    }

    public List<Model> toModelList(List<ModelEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    public ModelEntity toEntity(Model modelo) {
        TechnicalSheet ficha = modelo.getFichaTecnica();
        ModelEntity entity = new ModelEntity();

        entity.setId(modelo.getId());
        entity.setNombre(modelo.getNombre());
        entity.setInvima(modelo.getInvima());
        entity.setIdFabricante(modelo.getIdFabricante());
        entity.setIdEquipo(modelo.getIdEquipo());
        entity.setClaseRiesgo(ficha.riesgo() == null ? null : ficha.riesgo().name());
        entity.setCaracteristicas(ficha.caracteristicas());
        entity.setAlimentacion(ficha.alimentacion());
        entity.setVoltaje(ficha.voltaje());
        entity.setPotencia(ficha.potencia());
        entity.setAmperaje(ficha.amperaje());
        entity.setFrecuencia(ficha.frecuencia());
        entity.setEstadoActivo(modelo.isEstadoActivo());

        return entity;
    }

    /** Sin pasar por TechnicalSheet.of: leer no valida, igual que rehydrate. */
    private static TechnicalSheet fichaDe(ModelEntity entity) {
        return new TechnicalSheet(
                entity.getClaseRiesgo() == null ? null : RiskClass.valueOf(entity.getClaseRiesgo()),
                entity.getCaracteristicas(),
                entity.getAlimentacion(),
                entity.getVoltaje(),
                entity.getPotencia(),
                entity.getAmperaje(),
                entity.getFrecuencia());
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
                entity.getCodigoInterno(),
                entity.getProveedor(),
                entity.getNumeroHojaVida(),
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
                unidad.getCodigoInterno(),
                unidad.getProveedor(),
                unidad.getNumeroHojaVida(),
                unidad.getIdModelo(),
                unidad.getIdAreaServicio(),
                unidad.isEstadoActivo());
    }
}
