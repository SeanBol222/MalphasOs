package com.malphasos.malphasos.equipment.infrastructure.input.mapper;

import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import com.malphasos.malphasos.equipment.application.services.model.commands.TechnicalSheetCommand;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.equipment.domain.model.TechnicalSheet;
import com.malphasos.malphasos.equipment.infrastructure.input.model.request.TechnicalSheetRequest;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.TechnicalSheetResponse;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.BrandResponse;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.ClientEquipmentResponse;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.EquipmentResponse;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.EquipmentTypeResponse;
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import com.malphasos.malphasos.equipment.domain.magnitude.Magnitude;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.MagnitudeResponse;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.MeasurementUnitResponse;
import com.malphasos.malphasos.equipment.infrastructure.input.model.response.TypeVerificationResponse;
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
                .uso(tipo.getUso())
                .limpiezaCotidiana(tipo.getLimpiezaCotidiana())
                .verificable(tipo.isVerificable())
                // Solo las activas, y dentro de cada una solo sus puntos activos: lo retirado se guarda
                // por los reportes que se hicieron con ello, y devolverlo lo pondria a competir con lo
                // de ahora.
                .verificaciones(tipo.verificacionesActivas().stream().map(this::toResponse).toList())
                .valorUnitarioMantenimiento(tipo.getValorUnitarioMantenimiento())
                .estadoActivo(tipo.isEstadoActivo())
                .build();
    }

    /**
     * Una verificación, con el nombre de su magnitud y el símbolo de su unidad.
     *
     * <p>Los nombres salen de las piezas que la verificación lleva dentro, así que esto no consulta
     * nada: es la razón por la que el dominio las embebe en vez de guardar solo identificadores.
     */
    public TypeVerificationResponse toResponse(TypeVerification verificacion) {
        return new TypeVerificationResponse(
                verificacion.id(),
                verificacion.magnitud().id(),
                verificacion.magnitud().nombre(),
                verificacion.unidad().id(),
                verificacion.unidad().simbolo(),
                verificacion.unidad().nombre(),
                verificacion.modalidad(),
                verificacion.cantidadDatos(),
                verificacion.puntosActivos().stream()
                        .map(punto -> new VerificationPointResponse(punto.id(), punto.valor()))
                        .toList());
    }

    public MagnitudeResponse toResponse(Magnitude magnitud) {
        return new MagnitudeResponse(magnitud.id(), magnitud.codigo(), magnitud.nombre());
    }

    public List<MagnitudeResponse> toMagnitudeList(List<Magnitude> magnitudes) {
        return magnitudes.stream().map(this::toResponse).toList();
    }

    public MeasurementUnitResponse toResponse(MeasurementUnit unidad) {
        return new MeasurementUnitResponse(unidad.id(), unidad.simbolo(), unidad.nombre());
    }

    public List<MeasurementUnitResponse> toUnitList(List<MeasurementUnit> unidades) {
        return unidades.stream().map(this::toResponse).toList();
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
        return new ModelResponse(modelo.getId(), modelo.getNombre(), modelo.getInvima(),
                modelo.getIdFabricante(), modelo.getIdEquipo(), toResponse(modelo.getFichaTecnica()),
                modelo.isEstadoActivo());
    }

    public TechnicalSheetResponse toResponse(TechnicalSheet ficha) {
        return new TechnicalSheetResponse(ficha.riesgo(), ficha.caracteristicas(), ficha.alimentacion(),
                ficha.voltaje(), ficha.potencia(), ficha.amperaje(), ficha.frecuencia());
    }

    /** Lo que llega por HTTP, sin validar todavia: lo valida el dominio al construir la ficha. */
    public TechnicalSheetCommand toCommand(TechnicalSheetRequest ficha) {
        return ficha == null
                ? null
                : new TechnicalSheetCommand(ficha.riesgo(), ficha.caracteristicas(), ficha.alimentacion(),
                        ficha.voltaje(), ficha.potencia(), ficha.amperaje(), ficha.frecuencia());
    }

    public List<ModelResponse> toModelList(List<Model> modelos) {
        return modelos.stream().map(this::toResponse).toList();
    }

    public ClientEquipmentResponse toResponse(ClientEquipment unidad) {
        return ClientEquipmentResponse.builder()
                .id(unidad.getId())
                .serie(unidad.getSerie())
                .numeroHojaVida(unidad.getNumeroHojaVida())
                .numeroInventario(unidad.getNumeroInventario())
                .fechaCompra(unidad.getFechaCompra())
                .valorCompra(unidad.getValorCompra())
                .codigoInterno(unidad.getCodigoInterno())
                .proveedor(unidad.getProveedor())
                .idModelo(unidad.getIdModelo())
                .idAreaServicio(unidad.getIdAreaServicio())
                .estadoActivo(unidad.isEstadoActivo())
                .build();
    }

    public List<ClientEquipmentResponse> toClientEquipmentList(List<ClientEquipment> unidades) {
        return unidades.stream().map(this::toResponse).toList();
    }
}
