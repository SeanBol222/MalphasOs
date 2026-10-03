package com.malphasos.malphasos.equipment.infrastructure.output.mapper;

import com.malphasos.malphasos.equipment.domain.brand.Brand;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationMode;
import com.malphasos.malphasos.equipment.domain.equipmentType.TypeVerification;
import com.malphasos.malphasos.equipment.domain.equipmentType.VerificationPoint;
import com.malphasos.malphasos.equipment.domain.magnitude.Magnitude;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.BrandEntity;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.EquipmentTypeEntity;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.MagnitudeEntity;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.MeasurementUnitEntity;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.TypeVerificationEntity;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.VerificationPointEntity;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.ManufacturerEntity;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
        return EquipmentType.rehydrate(
                entity.getId(),
                entity.getNombre(),
                entity.getDefinicionTecnica(),
                entity.getRecomendacionesCuidado(),
                entity.getTecnologiaPredominante(),
                entity.getVoltaje(),
                entity.getAmperaje(),
                entity.getVerificaciones().stream().map(this::toDomain).toList(),
                entity.getValorUnitarioMantenimiento(),
                entity.isEstadoActivo());
    }

    public TypeVerification toDomain(TypeVerificationEntity entity) {
        return TypeVerification.rehydrate(
                entity.getId(),
                toDomain(entity.getMagnitud()),
                toDomain(entity.getUnidad()),
                VerificationMode.desdeEsquema(entity.getModalidad()),
                entity.getCantidadDatos(),
                entity.getPuntos().stream()
                        .map(punto -> VerificationPoint.rehydrate(
                                punto.getId(), punto.getValor(), punto.isEstadoActivo()))
                        .toList(),
                entity.isEstadoActivo());
    }

    public List<EquipmentType> toEquipmentTypeList(List<EquipmentTypeEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    /**
     * El tipo de equipo como fila, <b>poniendo al día solo lo que ya existe</b>.
     *
     * <p><b>Por qué en dos pasadas, y no es precaución teórica.</b> Hibernate vacía los {@code INSERT}
     * antes que los {@code UPDATE}, de modo que al redeclarar la verificación de una magnitud —la fila
     * vieja pasa a inactiva, la nueva nace activa— las dos están activas en ese instante y el índice
     * único parcial {@code UQ_verificacion_magnitud_activa_por_tipo} salta. Se vio fallar: las dos
     * pruebas de reconfiguración de {@code EquipmentCatalogPersistenceTest} caen con
     * {@code duplicate key} si esto se hace de una sola pasada.
     *
     * <p><b>Y es la misma trampa que el módulo de reportes pagó el 2026-09-27</b>, que aquí estaba
     * latente desde el 2026-09-26: el índice de los puntos tenía el mismo problema y <b>ninguna prueba
     * lo ejercía</b>, porque todas las reconfiguraciones de la batería cambiaban el valor del punto y
     * el índice es parcial, así que la fila retirada dejaba de competir. Con la clave en (tipo,
     * magnitud), que no cambia al reconfigurar, la trampa salta en el caso normal.
     *
     * <p>Un índice único parcial no se puede declarar diferido en PostgreSQL —{@code DEFERRABLE} es de
     * las restricciones, no de los índices, y una restricción no admite {@code WHERE}—, así que el
     * orden hay que imponerlo aquí: esto actualiza, el adaptador vacía la sesión, y
     * {@link #addNewVerifications} inserta.
     *
     * <p>Recibe la entidad existente cuando la hay, también para no duplicar filas hijas: construir una
     * entidad nueva en cada guardado haría que Hibernate insertara duplicados de las verificaciones, que
     * es lo que le pasó al adaptador de clientes con sus contactos y al de órdenes con su alcance.
     */
    public EquipmentTypeEntity toEntity(EquipmentType tipo, EquipmentTypeEntity existente) {
        EquipmentTypeEntity entity = existente != null ? existente : new EquipmentTypeEntity();

        entity.setId(tipo.getId());
        entity.setNombre(tipo.getNombre());
        entity.setDefinicionTecnica(tipo.getDefinicionTecnica());
        entity.setRecomendacionesCuidado(tipo.getRecomendacionesCuidado());
        entity.setTecnologiaPredominante(tipo.getTecnologiaPredominante());
        entity.setVoltaje(tipo.getVoltaje());
        entity.setAmperaje(tipo.getAmperaje());
        entity.setValorUnitarioMantenimiento(tipo.getValorUnitarioMantenimiento());
        entity.setEstadoActivo(tipo.isEstadoActivo());

        Map<UUID, TypeVerificationEntity> filas = entity.getVerificaciones().stream()
                .collect(Collectors.toMap(TypeVerificationEntity::getId, fila -> fila));

        for (TypeVerification verificacion : tipo.getVerificaciones()) {
            TypeVerificationEntity fila = filas.get(verificacion.id());

            if (fila == null) {
                continue;
            }

            // Lo que cambia aqui es sobre todo el estado: una verificacion pasa de activa a retirada
            // cuando el tipo se reconfigura. La modalidad y la cantidad se ponen al dia igual, porque
            // nada impide que un dia se corrijan en sitio.
            fila.setModalidad(verificacion.modalidad().valorEnEsquema());
            fila.setCantidadDatos(verificacion.cantidadDatos());
            fila.setEstadoActivo(verificacion.estadoActivo());

            volcarPuntos(verificacion, fila);
        }

        return entity;
    }

    /**
     * Inserta las verificaciones que aún no tienen fila, con sus puntos.
     *
     * <p>El adaptador la llama <b>después</b> de vaciar la sesión, que es lo que deja el
     * {@code UPDATE} de la retirada por delante del {@code INSERT} de la sustituta.
     *
     * <p>Los puntos nuevos viajan siempre con una verificación nueva y nunca se añaden a una existente:
     * {@code declareVerifications} crea objetos nuevos con identidad nueva, de modo que una fila que ya
     * estaba solo puede retirarse. Por eso no hace falta una tercera pasada para los puntos.
     */
    public void addNewVerifications(EquipmentType tipo, EquipmentTypeEntity entity) {
        Set<UUID> yaEstan = entity.getVerificaciones().stream()
                .map(TypeVerificationEntity::getId)
                .collect(Collectors.toSet());

        for (TypeVerification verificacion : tipo.getVerificaciones()) {
            if (yaEstan.contains(verificacion.id())) {
                continue;
            }

            entity.getVerificaciones().add(toEntity(verificacion, entity));
        }
    }

    /**
     * Una verificación como fila nueva, colgada de su tipo.
     *
     * <p>Los puntos se construyen después de la fila porque cada uno apunta a ella: es la relación la
     * que se guarda, y sin el lado dueño Hibernate insertaría la fila con la llave foránea nula.
     */
    private TypeVerificationEntity toEntity(
            TypeVerification verificacion, EquipmentTypeEntity tipoEquipo) {

        TypeVerificationEntity entity = new TypeVerificationEntity(
                verificacion.id(),
                tipoEquipo,
                toEntity(verificacion.magnitud()),
                toEntity(verificacion.unidad()),
                verificacion.modalidad().valorEnEsquema(),
                verificacion.cantidadDatos(),
                new java.util.ArrayList<>(),
                verificacion.estadoActivo());

        for (VerificationPoint punto : verificacion.puntos()) {
            entity.getPuntos().add(new VerificationPointEntity(
                    punto.id(), entity, punto.valor(), punto.estadoActivo()));
        }

        return entity;
    }

    /** Pone al día los puntos que ya tienen fila. En la práctica, los retira. */
    private void volcarPuntos(TypeVerification verificacion, TypeVerificationEntity fila) {
        Map<UUID, VerificationPointEntity> puntos = fila.getPuntos().stream()
                .collect(Collectors.toMap(VerificationPointEntity::getId, punto -> punto));

        for (VerificationPoint punto : verificacion.puntos()) {
            VerificationPointEntity filaDelPunto = puntos.get(punto.id());

            if (filaDelPunto != null) {
                filaDelPunto.setValor(punto.valor());
                filaDelPunto.setEstadoActivo(punto.estadoActivo());
            }
        }
    }

    // --------------------------------------------------------- catalogo metrologico

    public Magnitude toDomain(MagnitudeEntity entity) {
        return new Magnitude(
                entity.getId(), entity.getCodigo(), entity.getNombre(), entity.isEstadoActivo());
    }

    public List<Magnitude> toMagnitudeList(List<MagnitudeEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    public MeasurementUnit toDomain(MeasurementUnitEntity entity) {
        return new MeasurementUnit(
                entity.getId(),
                entity.getMagnitud().getId(),
                entity.getSimbolo(),
                entity.getNombre(),
                entity.isEstadoActivo());
    }

    public List<MeasurementUnit> toUnitList(List<MeasurementUnitEntity> entities) {
        return entities.stream().map(this::toDomain).toList();
    }

    /**
     * El catálogo <b>no se escribe nunca</b>: estas dos existen solo para rellenar la llave foránea de
     * una verificación. Hibernate no las persiste —{@code @ManyToOne} no cascadea— y solo lee su
     * identificador para la columna.
     */
    private MagnitudeEntity toEntity(Magnitude magnitud) {
        return new MagnitudeEntity(
                magnitud.id(), magnitud.codigo(), magnitud.nombre(), magnitud.estadoActivo());
    }

    private MeasurementUnitEntity toEntity(MeasurementUnit unidad) {
        MagnitudeEntity magnitud = new MagnitudeEntity();
        magnitud.setId(unidad.magnitudId());

        return new MeasurementUnitEntity(
                unidad.id(), magnitud, unidad.simbolo(), unidad.nombre(), unidad.estadoActivo());
    }
}
