package com.malphasos.malphasos.equipment.domain.equipmentType;

import com.malphasos.malphasos.equipment.domain.equipmentType.events.EquipmentTypeCreatedEvent;
import com.malphasos.malphasos.equipment.domain.equipmentType.events.EquipmentTypeDeactivatedEvent;
import com.malphasos.malphasos.equipment.domain.equipmentType.events.EquipmentTypePayload;
import com.malphasos.malphasos.equipment.domain.equipmentType.events.EquipmentTypeUpdatedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 * <p><b>Desde el 2026-09-26 sabe además con qué y cuántas veces se verifica</b>, que es el primer trozo
 * de la segunda tanda del módulo. Y los tres datos —modalidad, cantidad de lecturas y puntos— son una
 * sola decisión, así que se cambian juntos:
 *
 * <ul>
 *   <li>Las dos modalidades <b>constantes</b> exigen una cantidad de lecturas y al menos un punto: son
 *       lo que hace falta para llenar el reporte.
 *   <li>La modalidad <b>variable</b> no admite ninguna de las dos: cuántas lecturas tomar lo decide el
 *       ingeniero en campo, y no hay nada constante que declarar.
 *   <li>Un tipo que <b>no se verifica</b> tampoco lleva ninguna.
 * </ul>
 *
 * <p>La cantidad es <b>por punto</b>, no en total: se verifica a 50, a 100 y a 150 mmHg, y en cada valor
 * se toman las lecturas declaradas. Confundirlo daría un reporte con un tercio de los datos.
 *
 * <p>Las verificaciones técnicas y los datos metrológicos que el original guardaba aquí siguen
 * pendientes: esto es con qué se verifica, no el resultado de haberlo hecho.
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

    /** Lecturas que se toman <b>en cada punto</b>. Solo con una modalidad constante. */
    private Integer cantidadDatos;

    /**
     * Los valores constantes en los que se verifica, retirados incluidos.
     *
     * <p>Se guardan también los retirados porque aquí nada se borra: el punto con el que se hizo un
     * reporte el año pasado tiene que seguir existiendo.
     */
    private final List<VerificationPoint> puntosVerificacion = new ArrayList<>();

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
            Integer cantidadDatos,
            List<VerificationPoint> puntos,
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
        this.cantidadDatos = cantidadDatos;
        this.valorUnitarioMantenimiento = valorUnitarioMantenimiento;
        this.estadoActivo = estadoActivo;

        if (puntos != null) {
            this.puntosVerificacion.addAll(puntos);
        }
    }

    public static EquipmentType create(
            String nombre,
            String definicionTecnica,
            String recomendacionesCuidado,
            String tecnologiaPredominante,
            Integer voltaje,
            BigDecimal amperaje,
            VerificationMode modalidadVerificacion,
            Integer cantidadDatos,
            List<VerificationPoint> puntosVerificacion,
            long valorUnitarioMantenimiento) {

        List<VerificationPoint> puntos =
                validarVerificacion(modalidadVerificacion, cantidadDatos, puntosVerificacion);

        EquipmentType tipo = new EquipmentType(
                UUID.randomUUID(),
                exigirTexto(nombre, "nombre"),
                exigirTexto(definicionTecnica, "definicion tecnica"),
                exigirTexto(recomendacionesCuidado, "recomendaciones de cuidado"),
                exigirTexto(tecnologiaPredominante, "tecnologia predominante"),
                validarVoltaje(voltaje),
                validarAmperaje(amperaje),
                modalidadVerificacion,
                cantidadDatos,
                puntos,
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
            Integer cantidadDatos,
            List<VerificationPoint> puntosVerificacion,
            long valorUnitarioMantenimiento,
            boolean estadoActivo) {

        // Sin validar: leer de la base no es un hecho del dominio, y una fila vieja que ya no cumple una
        // regla nueva tiene que poder cargarse para poder corregirla.
        return new EquipmentType(id, nombre, definicionTecnica, recomendacionesCuidado,
                tecnologiaPredominante, voltaje, amperaje, modalidadVerificacion, cantidadDatos,
                puntosVerificacion, valorUnitarioMantenimiento, estadoActivo);
    }

    /** Los puntos de verificación, retirados incluidos, como copia inmutable. */
    public List<VerificationPoint> getPuntosVerificacion() {
        return Collections.unmodifiableList(puntosVerificacion);
    }

    /** Los puntos en los que se verifica hoy, ordenados por valor, que es como se recorren. */
    public List<VerificationPoint> puntosActivos() {
        return puntosVerificacion.stream()
                .filter(VerificationPoint::estadoActivo)
                .sorted((uno, otro) -> uno.valor().compareTo(otro.valor()))
                .toList();
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
    public void changeVerificationMode(
            VerificationMode modalidad, Integer cantidadDatos, List<VerificationPoint> puntos) {

        List<VerificationPoint> nuevos = validarVerificacion(modalidad, cantidadDatos, puntos);

        if (modalidad == this.modalidadVerificacion
                && java.util.Objects.equals(cantidadDatos, this.cantidadDatos)
                && mismosPuntos(nuevos)) {
            // Un cambio que no cambia nada no emite evento.
            return;
        }

        // Los de antes se retiran, no se borran: con ellos se hicieron los reportes anteriores.
        puntosVerificacion.replaceAll(
                punto -> punto.estadoActivo() ? punto.deactivated() : punto);
        puntosVerificacion.addAll(nuevos);

        this.modalidadVerificacion = modalidad;
        this.cantidadDatos = cantidadDatos;
        registerEvent(new EquipmentTypeUpdatedEvent(
                metadataFor(EquipmentTypeUpdatedEvent.TYPE), payload()));
    }

    /** Si los puntos que se piden son exactamente los que ya están activos. */
    private boolean mismosPuntos(List<VerificationPoint> pedidos) {
        List<VerificationPoint> activos = puntosActivos();

        return activos.size() == pedidos.size()
                && pedidos.stream()
                        .allMatch(pedido -> activos.stream().anyMatch(pedido::mideLoMismoQue));
    }

    /**
     * Las tres reglas que atan modalidad, cantidad y puntos, en un solo sitio.
     *
     * <p>Están aquí y no solo en el esquema porque el esquema no puede mirar la tabla de puntos desde el
     * {@code CHECK} del tipo: que una modalidad constante exija <b>al menos un punto</b> solo se puede
     * comprobar aquí. Las otras dos las impone también la base, y tenerlas en los dos sitios es
     * deliberado — el dominio da el mensaje y la base garantiza que nadie lo esquive por SQL.
     */
    private static List<VerificationPoint> validarVerificacion(
            VerificationMode modalidad, Integer cantidadDatos, List<VerificationPoint> puntos) {

        List<VerificationPoint> pedidos = puntos == null ? List.of() : List.copyOf(puntos);

        if (modalidad == null || modalidad == VerificationMode.PATRON_EQUIPO_VARIABLE) {
            if (cantidadDatos != null) {
                throw new IllegalArgumentException(
                        "Solo las modalidades constantes declaran cuantas lecturas se toman; con "
                                + (modalidad == null ? "un tipo que no se verifica" : "patron y equipo variables")
                                + " lo decide el ingeniero en campo");
            }
            if (!pedidos.isEmpty()) {
                throw new IllegalArgumentException(
                        "Solo las modalidades constantes tienen puntos de verificacion: no hay nada"
                                + " constante que declarar");
            }

            return pedidos;
        }

        if (cantidadDatos == null || cantidadDatos < 1 || cantidadDatos > MAXIMO_DATOS) {
            throw new IllegalArgumentException(
                    "La modalidad " + modalidad + " necesita entre 1 y " + MAXIMO_DATOS
                            + " lecturas por punto, y se recibio " + cantidadDatos);
        }
        if (pedidos.isEmpty()) {
            throw new IllegalArgumentException(
                    "La modalidad " + modalidad + " necesita al menos un punto de verificacion:"
                            + " es el valor en el que se mantiene lo constante");
        }

        for (int i = 0; i < pedidos.size(); i++) {
            for (int j = i + 1; j < pedidos.size(); j++) {
                if (pedidos.get(i).mideLoMismoQue(pedidos.get(j))) {
                    throw new IllegalArgumentException(
                            "El punto " + pedidos.get(i).valor() + " " + pedidos.get(i).unidad()
                                    + " esta declarado dos veces");
                }
            }
        }

        return pedidos;
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

    /** El tope que el esquema declara. Aquí para que el mensaje lo pueda citar. */
    private static final int MAXIMO_DATOS = 100;

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
