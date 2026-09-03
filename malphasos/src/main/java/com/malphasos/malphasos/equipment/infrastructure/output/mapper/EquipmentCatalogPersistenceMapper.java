package com.malphasos.malphasos.equipment.infrastructure.output.mapper;

import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.BrandEntity;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.EquipmentTypeEntity;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.ManufacturerEntity;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Traduce entre los agregados del catálogo y sus filas. A mano, porque se construyen por
 * {@code rehydrate} y no ofrecen setters ni builder.
 *
 * <p>El caso con miga es el tipo de equipo: el agregado tiene <b>una</b> modalidad de verificación y
 * la tabla tiene <b>dos</b> columnas, la modalidad y un booleano. El booleano se deriva al guardar y
 * se ignora al leer, de modo que no pueden contradecirse.
 */
@Component
public class EquipmentCatalogPersistenceMapper {

    // ------------------------------------------------------------------ fabricante

    public Manufacturer toDomain(ManufacturerEntity entity) {
        return Manufacturer.rehydrate(
                entity.getId(), entity.getNombre(), entity.getIdPais(), entity.isEstadoActivo());
    }

    public List<Manufacturer> toManufacturerList(List<ManufacturerEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    public ManufacturerEntity toEntity(Manufacturer fabricante) {
        return new ManufacturerEntity(
                fabricante.getId(), fabricante.getNombre(),
                fabricante.getIdPais(), fabricante.isEstadoActivo());
    }

    // ------------------------------------------------------------------ marca

    public Brand toDomain(BrandEntity entity) {
        return Brand.rehydrate(entity.getId(), entity.getNombre(), entity.isEstadoActivo());
    }

    public List<Brand> toBrandList(List<BrandEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    public BrandEntity toEntity(Brand marca) {
        return new BrandEntity(marca.getId(), marca.getNombre(), marca.isEstadoActivo());
    }

    // ------------------------------------------------------------------ tipo de equipo

    public EquipmentType toDomain(EquipmentTypeEntity entity) {
        // El booleano de la fila no se lee: lo verificable se deriva de la modalidad.
        return EquipmentType.rehydrate(
                entity.getId(),
                entity.getNombre(),
                entity.getDefinicionTecnica(),
                entity.getRecomendacionesCuidado(),
                entity.getTecnologiaPredominante(),
                entity.getVoltaje(),
                entity.getAmperaje(),
                VerificationMode.desdeEsquema(entity.getTipoVerificacion()),
                entity.getValorUnitarioMantenimiento(),
                entity.isEstadoActivo());
    }

    public List<EquipmentType> toEquipmentTypeList(List<EquipmentTypeEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    public EquipmentTypeEntity toEntity(EquipmentType tipo) {
        VerificationMode modalidad = tipo.getModalidadVerificacion();

        return new EquipmentTypeEntity(
                tipo.getId(),
                tipo.getNombre(),
                tipo.getDefinicionTecnica(),
                tipo.getRecomendacionesCuidado(),
                tipo.getTecnologiaPredominante(),
                tipo.getVoltaje(),
                tipo.getAmperaje(),
                tipo.isVerificable(),
                modalidad == null ? null : modalidad.valorEnEsquema(),
                tipo.getValorUnitarioMantenimiento(),
                tipo.isEstadoActivo());
    }
}
