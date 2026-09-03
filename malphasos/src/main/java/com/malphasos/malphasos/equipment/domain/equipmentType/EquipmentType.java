package com.malphasos.malphasos.equipment.domain.equipmentType;

import com.malphasos.malphasos.equipment.domain.equipmentType.events.EquipmentTypeCreatedEvent;
import com.malphasos.malphasos.equipment.domain.equipmentType.events.EquipmentTypeDeactivatedEvent;
import com.malphasos.malphasos.equipment.domain.equipmentType.events.EquipmentTypePayload;
import com.malphasos.malphasos.equipment.domain.equipmentType.events.EquipmentTypeUpdatedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Categoría de equipo, con sus características técnicas y el costo de su mantenimiento.
 *
 * <p><b>No existe un campo {@code verificable}.</b> Se deriva de si hay modalidad de verificación:
 * un tipo es verificable exactamente cuando se sabe cómo verificarlo. El original tenía un booleano
 * y la columna {@code n_tipo_verificacion} en la tabla, sin nada que los atara y sin que el dominio
 * modelara siquiera la segunda — cabía un tipo marcado como verificable del que nadie sabía cómo se
 * verifica. Al derivar el booleano, ese estado deja de ser expresable.
 *
 * <p>Los datos metrológicos y las verificaciones técnicas que el original guardaba aquí llegarán con
 * la segunda tanda del módulo.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EquipmentType extends AggregateRoot {

    @EqualsAndHashCode.Include
    private final UUID id;

    private String nombre;

    private String definicionTecnica;

    private String recomendacionesCuidado;

    private String tecnologiaPredominante;

    /** Voltaje nominal, opcional. */
    private Integer voltaje;

    /** Amperaje nominal, opcional. */
    private BigDecimal amperaje;

    /** Cómo se verifica metrológicamente, o {@code null} si este tipo no se verifica. */
    private VerificationMode modalidadVerificacion;

    private long valorUnitarioMantenimiento;

    private boolean estadoActivo;

    private EquipmentType(
            UUID id,
            String nombre,
            String definicionTecnica,
            String recomendacionesCuidado,
            String tecnologiaPredominante,
            Integer voltaje,
            BigDecimal amperaje,
            VerificationMode modalidadVerificacion,
            long valorUnitarioMantenimiento,
            boolean estadoActivo) {

        this.id = id;
        this.nombre = nombre;
        this.definicionTecnica = definicionTecnica;
        this.recomendacionesCuidado = recomendacionesCuidado;
        this.tecnologiaPredominante = tecnologiaPredominante;
        this.voltaje = voltaje;
        this.amperaje = amperaje;
        this.modalidadVerificacion = modalidadVerificacion;
        this.valorUnitarioMantenimiento = valorUnitarioMantenimiento;
        this.estadoActivo = estadoActivo;
    }

    public static EquipmentType create(
            String nombre,
            String definicionTecnica,
            String recomendacionesCuidado,
            String tecnologiaPredominante,
            Integer voltaje,
            BigDecimal amperaje,
            VerificationMode modalidadVerificacion,
            long valorUnitarioMantenimiento) {

        EquipmentType tipo = new EquipmentType(
                UUID.randomUUID(),
                exigirTexto(nombre, "nombre"),
                exigirTexto(definicionTecnica, "definicion tecnica"),
                exigirTexto(recomendacionesCuidado, "recomendaciones de cuidado"),
                exigirTexto(tecnologiaPredominante, "tecnologia predominante"),
                validarVoltaje(voltaje),
                validarAmperaje(amperaje),
                modalidadVerificacion,
                validarValor(valorUnitarioMantenimiento),
                true);

        tipo.registerEvent(new EquipmentTypeCreatedEvent(
                tipo.metadataFor(EquipmentTypeCreatedEvent.TYPE), tipo.payload()));

        return tipo;
    }

    public static EquipmentType rehydrate(
            UUID id,
            String nombre,
            String definicionTecnica,
            String recomendacionesCuidado,
            String tecnologiaPredominante,
            Integer voltaje,
            BigDecimal amperaje,
            VerificationMode modalidadVerificacion,
            long valorUnitarioMantenimiento,
            boolean estadoActivo) {

        return new EquipmentType(id, nombre, definicionTecnica, recomendacionesCuidado,
                tecnologiaPredominante, voltaje, amperaje, modalidadVerificacion,
                valorUnitarioMantenimiento, estadoActivo);
    }

    /**
     * Si a este tipo de equipo se le hace verificación metrológica.
     *
     * <p>Derivado, no almacenado: es verificable exactamente cuando consta cómo verificarlo.
     */
    public boolean isVerificable() {
        return modalidadVerificacion != null;
    }

    /** Cambia las características. Un valor nulo deja el campo como está. */
    public void update(
            String nombre,
            String definicionTecnica,
            String recomendacionesCuidado,
            String tecnologiaPredominante,
            Integer voltaje,
            BigDecimal amperaje,
            Long valorUnitarioMantenimiento) {

        boolean cambio = false;

        if (nombre != null && !exigirTexto(nombre, "nombre").equals(this.nombre)) {
            this.nombre = nombre.trim();
            cambio = true;
        }
        if (definicionTecnica != null) {
            this.definicionTecnica = exigirTexto(definicionTecnica, "definicion tecnica");
            cambio = true;
        }
        if (recomendacionesCuidado != null) {
            this.recomendacionesCuidado = exigirTexto(recomendacionesCuidado, "recomendaciones de cuidado");
            cambio = true;
        }
        if (tecnologiaPredominante != null) {
            this.tecnologiaPredominante = exigirTexto(tecnologiaPredominante, "tecnologia predominante");
            cambio = true;
        }
        if (voltaje != null) {
            this.voltaje = validarVoltaje(voltaje);
            cambio = true;
        }
        if (amperaje != null) {
            this.amperaje = validarAmperaje(amperaje);
            cambio = true;
        }
        if (valorUnitarioMantenimiento != null) {
            this.valorUnitarioMantenimiento = validarValor(valorUnitarioMantenimiento);
            cambio = true;
        }

        if (cambio) {
            registerEvent(new EquipmentTypeUpdatedEvent(
                    metadataFor(EquipmentTypeUpdatedEvent.TYPE), payload()));
        }
    }

    /**
     * Declara cómo se verifica este tipo de equipo, o que no se verifica si se pasa {@code null}.
     *
     * <p>Es una operación aparte de {@link #update} porque cambia lo que el tipo <i>es</i>: decidir
     * que un equipo pasa a ser verificable arrastra consigo los datos metrológicos y las
     * verificaciones que habrá que registrarle.
     */
    public void changeVerificationMode(VerificationMode modalidad) {
        if (modalidad == this.modalidadVerificacion) {
            return;
        }

        this.modalidadVerificacion = modalidad;
        registerEvent(new EquipmentTypeUpdatedEvent(
                metadataFor(EquipmentTypeUpdatedEvent.TYPE), payload()));
    }

    /** Retira el tipo sin borrarlo. Retirar dos veces no emite dos eventos. */
    public void deactivate() {
        if (!estadoActivo) {
            return;
        }

        this.estadoActivo = false;
        registerEvent(new EquipmentTypeDeactivatedEvent(
                metadataFor(EquipmentTypeDeactivatedEvent.TYPE), payload()));
    }

    @Override
    protected String aggregateType() {
        return "EquipmentType";
    }

    @Override
    protected String aggregateId() {
        return id.toString();
    }

    private EquipmentTypePayload payload() {
        return new EquipmentTypePayload(nombre, modalidadVerificacion, valorUnitarioMantenimiento);
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Un tipo de equipo necesita " + campo);
        }

        return valor.trim();
    }

    private static Integer validarVoltaje(Integer voltaje) {
        if (voltaje != null && voltaje <= 0) {
            throw new IllegalArgumentException("El voltaje es positivo, y se recibio " + voltaje);
        }

        return voltaje;
    }

    private static BigDecimal validarAmperaje(BigDecimal amperaje) {
        if (amperaje != null && amperaje.signum() <= 0) {
            throw new IllegalArgumentException("El amperaje es positivo, y se recibio " + amperaje);
        }

        return amperaje;
    }

    private static long validarValor(long valor) {
        if (valor < 0) {
            throw new IllegalArgumentException(
                    "El valor del mantenimiento no puede ser negativo, y se recibio " + valor);
        }

        return valor;
    }
}
