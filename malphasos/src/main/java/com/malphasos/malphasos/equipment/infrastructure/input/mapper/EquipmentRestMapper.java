package com.malphasos.malphasos.equipment.infrastructure.input.mapper;

import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.BrandResponse;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.ClientEquipmentResponse;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.EquipmentResponse;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.EquipmentTypeResponse;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.VerificationPointResponse;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.ManufacturerResponse;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.ModelResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Traduce los agregados a la respuesta del API, solo en esa dirección.
 *
 * <p>A mano y no con MapStruct porque el tipo de equipo expone {@code verificable}, que en el
 * agregado no es un campo sino un método derivado.
 */
@Component
public class EquipmentRestMapper {

    public ManufacturerResponse toResponse(Manufacturer fabricante) {
        return new ManufacturerResponse(fabricante.getId(), fabricante.getNombre(),
                fabricante.getIdPais(), fabricante.isEstadoActivo());
    }

    public List<ManufacturerResponse> toManufacturerList(List<Manufacturer> fabricantes) {
        return fabricantes.stream().map(this::toResponse).toList();
    }

    public BrandResponse toResponse(Brand marca) {
        return new BrandResponse(marca.getId(), marca.getNombre(), marca.isEstadoActivo());
    }

    public List<BrandResponse> toBrandList(List<Brand> marcas) {
        return marcas.stream().map(this::toResponse).toList();
    }

    public EquipmentTypeResponse toResponse(EquipmentType tipo) {
        return EquipmentTypeResponse.builder()
                .id(tipo.getId())
                .nombre(tipo.getNombre())
                .definicionTecnica(tipo.getDefinicionTecnica())
                .recomendacionesCuidado(tipo.getRecomendacionesCuidado())
                .tecnologiaPredominante(tipo.getTecnologiaPredominante())
                .voltaje(tipo.getVoltaje())
                .amperaje(tipo.getAmperaje())
                .verificable(tipo.isVerificable())
                .modalidadVerificacion(tipo.getModalidadVerificacion())
                .cantidadDatos(tipo.getCantidadDatos())
                // Solo los activos: los retirados se guardan por los reportes que se hicieron con
                // ellos, y devolverlos los pondria a competir con los de ahora.
                .puntosVerificacion(tipo.puntosActivos().stream()
                        .map(punto -> new VerificationPointResponse(
                                punto.id(), punto.valor(), punto.unidad()))
                        .toList())
                .valorUnitarioMantenimiento(tipo.getValorUnitarioMantenimiento())
                .estadoActivo(tipo.isEstadoActivo())
                .build();
    }

    public List<EquipmentTypeResponse> toEquipmentTypeList(List<EquipmentType> tipos) {
        return tipos.stream().map(this::toResponse).toList();
    }

    public EquipmentResponse toResponse(Equipment equipo) {
        return new EquipmentResponse(equipo.getId(), equipo.getIdTipoEquipo(),
                equipo.getIdMarca(), equipo.isEstadoActivo());
    }

    public List<EquipmentResponse> toEquipmentList(List<Equipment> equipos) {
        return equipos.stream().map(this::toResponse).toList();
    }

    public ModelResponse toResponse(Model modelo) {
        return new ModelResponse(modelo.getId(), modelo.getInvima(), modelo.getIdFabricante(),
                modelo.getIdEquipo(), modelo.isEstadoActivo());
    }

    public List<ModelResponse> toModelList(List<Model> modelos) {
        return modelos.stream().map(this::toResponse).toList();
    }

    public ClientEquipmentResponse toResponse(ClientEquipment unidad) {
        return ClientEquipmentResponse.builder()
                .id(unidad.getId())
                .serie(unidad.getSerie())
                .numeroInventario(unidad.getNumeroInventario())
                .fechaCompra(unidad.getFechaCompra())
                .valorCompra(unidad.getValorCompra())
                .idModelo(unidad.getIdModelo())
                .idAreaServicio(unidad.getIdAreaServicio())
                .estadoActivo(unidad.isEstadoActivo())
                .build();
    }

    public List<ClientEquipmentResponse> toClientEquipmentList(List<ClientEquipment> unidades) {
        return unidades.stream().map(this::toResponse).toList();
    }
}
