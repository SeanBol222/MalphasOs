package com.malphasos.malphasos.equipment.domain.equipmentType;

import com.malphasos.malphasos.equipment.domain.equipmentType.events.EquipmentTypeCreatedEvent;
import com.malphasos.malphasos.equipment.domain.equipmentType.events.EquipmentTypeDeactivatedEvent;
import com.malphasos.malphasos.equipment.domain.equipmentType.events.EquipmentTypePayload;
import com.malphasos.malphasos.equipment.domain.equipmentType.events.EquipmentTypeUpdatedEvent;
import com.malphasos.malphasos.shared.domain.events.AggregateRoot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Categoría de equipo, con sus características técnicas y el costo de su mantenimiento.
 *
 * <p><b>No existe un campo {@code verificable}.</b> Se deriva de si hay alguna verificación declarada:
 * un tipo es verificable exactamente cuando se sabe cómo verificarlo. El original tenía un booleano y
 * la columna {@code n_tipo_verificacion} en la tabla, sin nada que los atara y sin que el dominio
 * modelara siquiera la segunda — cabía un tipo marcado como verificable del que nadie sabía cómo se
 * verifica. Al derivar el booleano, ese estado deja de ser expresable.
 *
 * <p><b>Un tipo se verifica en VARIAS magnitudes, y eso es la corrección del 2026-10-03.</b> Hasta
 * entonces declaraba una modalidad, una cantidad de lecturas y una lista de puntos con su unidad, es
 * decir: daba por supuesto que un aparato mide una sola cosa. Un termohigrómetro mide temperatura y
 * humedad relativa, y con el modelo anterior había que inventarse dos tipos de equipo para un solo
 * aparato. Ahora cada cosa que se verifica es una {@link TypeVerification}, con su magnitud, su unidad,
 * su modalidad, su cantidad de lecturas y sus puntos.
 *
 * <p>La cantidad de lecturas sigue siendo <b>por punto</b> y no en total: se verifica a 50, a 100 y a
 * 150 mmHg, y en cada valor se toman las lecturas declaradas. Confundirlo daría un reporte con un
 * tercio de los datos.
 *
 * <p><b>Un tipo no declara dos veces la misma magnitud</b>, y esa es la única regla que vive aquí y no
 * en {@link TypeVerification}: ninguna verificación puede comprobarla por sí sola, porque hace falta
 * ver a sus hermanas.
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

    /**
     * Para qué se usa esta clase de equipo, opcional. Entró el 2026-10-05 con la hoja de vida impresa,
     * en el lugar que dejaron voltaje y amperaje al bajar al modelo —ver {@code TechnicalSheet}—.
     */
    private String uso;

    /** Cómo se limpia al terminar la jornada, opcional. Como las recomendaciones, es de toda la clase. */
    private String limpiezaCotidiana;

    /**
     * Qué se verifica en este tipo de equipo, retiradas incluidas.
     *
     * <p>Se guardan también las retiradas porque aquí nada se borra: la verificación con la que se hizo
     * un reporte el año pasado tiene que seguir existiendo, y una lectura de ese reporte apunta a ella.
     */
    private final List<TypeVerification> verificaciones = new ArrayList<>();

    private long valorUnitarioMantenimiento;

    private boolean estadoActivo;

    private EquipmentType(
            UUID id,
            String nombre,
            String definicionTecnica,
            String recomendacionesCuidado,
            String tecnologiaPredominante,
            String uso,
            String limpiezaCotidiana,
            List<TypeVerification> verificaciones,
            long valorUnitarioMantenimiento,
            boolean estadoActivo) {

        this.id = id;
        this.nombre = nombre;
        this.definicionTecnica = definicionTecnica;
        this.recomendacionesCuidado = recomendacionesCuidado;
        this.tecnologiaPredominante = tecnologiaPredominante;
        this.uso = uso;
        this.limpiezaCotidiana = limpiezaCotidiana;
        this.valorUnitarioMantenimiento = valorUnitarioMantenimiento;
        this.estadoActivo = estadoActivo;

        if (verificaciones != null) {
            this.verificaciones.addAll(verificaciones);
        }
    }

    public static EquipmentType create(
            String nombre,
            String definicionTecnica,
            String recomendacionesCuidado,
            String tecnologiaPredominante,
            String uso,
            String limpiezaCotidiana,
            List<TypeVerification> verificaciones,
            long valorUnitarioMantenimiento) {

        List<TypeVerification> declaradas = exigirMagnitudesDistintas(verificaciones);

        EquipmentType tipo = new EquipmentType(
                UUID.randomUUID(),
                exigirTexto(nombre, "nombre"),
                exigirTexto(definicionTecnica, "definicion tecnica"),
                exigirTexto(recomendacionesCuidado, "recomendaciones de cuidado"),
                exigirTexto(tecnologiaPredominante, "tecnologia predominante"),
                opcional(uso),
                opcional(limpiezaCotidiana),
                declaradas,
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
            String uso,
            String limpiezaCotidiana,
            List<TypeVerification> verificaciones,
            long valorUnitarioMantenimiento,
            boolean estadoActivo) {

        // Sin validar: leer de la base no es un hecho del dominio, y una fila vieja que ya no cumple una
        // regla nueva tiene que poder cargarse para poder corregirla.
        return new EquipmentType(id, nombre, definicionTecnica, recomendacionesCuidado,
                tecnologiaPredominante, uso, limpiezaCotidiana, verificaciones,
                valorUnitarioMantenimiento, estadoActivo);
    }

    /** Las verificaciones, retiradas incluidas, como copia inmutable. */
    public List<TypeVerification> getVerificaciones() {
        return Collections.unmodifiableList(verificaciones);
    }

    /** Lo que se verifica hoy. */
    public List<TypeVerification> verificacionesActivas() {
        return verificaciones.stream().filter(TypeVerification::estadoActivo).toList();
    }

    /**
     * Una verificación de este tipo por su identificador, retirada incluida.
     *
     * <p>Retirada incluida a propósito: un reporte viejo apunta a la verificación con la que se hizo, y
     * tiene que poder encontrarla para imprimirse.
     */
    public Optional<TypeVerification> verificacionPorId(UUID verificacionId) {
        return verificaciones.stream().filter(v -> v.id().equals(verificacionId)).findFirst();
    }

    /**
     * Si a este tipo de equipo se le hace verificación metrológica.
     *
     * <p>Derivado, no almacenado: es verificable exactamente cuando consta qué verificarle.
     */
    public boolean isVerificable() {
        return !verificacionesActivas().isEmpty();
    }

    /** Cuántas lecturas pide un reporte completo de este tipo, sumando todas sus verificaciones. */
    public int lecturasEsperadas() {
        return verificacionesActivas().stream().mapToInt(TypeVerification::lecturasEsperadas).sum();
    }

    /**
     * Cambia las características. Un valor nulo deja el campo como está; en los opcionales, un texto en
     * blanco lo vacía.
     */
    public void update(
            String nombre,
            String definicionTecnica,
            String recomendacionesCuidado,
            String tecnologiaPredominante,
            String uso,
            String limpiezaCotidiana,
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
        if (uso != null && !java.util.Objects.equals(opcional(uso), this.uso)) {
            this.uso = opcional(uso);
            cambio = true;
        }
        if (limpiezaCotidiana != null
                && !java.util.Objects.equals(opcional(limpiezaCotidiana), this.limpiezaCotidiana)) {
            this.limpiezaCotidiana = opcional(limpiezaCotidiana);
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
     * Declara qué se verifica en este tipo de equipo, o que no se verifica nada si llega la lista vacía.
     *
     * <p>Es una operación aparte de {@link #update} porque cambia lo que el tipo <i>es</i>: decidir que
     * un equipo pasa a ser verificable arrastra consigo los datos metrológicos y los reportes que habrá
     * que registrarle.
     *
     * <p><b>Se manda la lista entera y no una verificación suelta.</b> Es la misma decisión que ya se
     * tomó con los puntos, y por la misma razón: con operaciones por verificación habría que responder
     * qué significa recibir una que no está en la lista, y esa pregunta no tiene mejor respuesta que «se
     * retira». Las anteriores se retiran, no se borran — con ellas se firmaron reportes.
     */
    public void declareVerifications(List<TypeVerification> nuevas) {
        List<TypeVerification> declaradas = exigirMagnitudesDistintas(nuevas);

        if (declaraLoMismo(declaradas)) {
            // Un cambio que no cambia nada no emite evento.
            return;
        }

        verificaciones.replaceAll(
                verificacion -> verificacion.estadoActivo() ? verificacion.deactivated() : verificacion);
        verificaciones.addAll(declaradas);

        registerEvent(new EquipmentTypeUpdatedEvent(
                metadataFor(EquipmentTypeUpdatedEvent.TYPE), payload()));
    }

    /** Si lo que se pide declarar es exactamente lo que ya está activo. */
    private boolean declaraLoMismo(List<TypeVerification> pedidas) {
        List<TypeVerification> activas = verificacionesActivas();

        return activas.size() == pedidas.size()
                && pedidas.stream()
                        .allMatch(pedida -> activas.stream().anyMatch(pedida::declaraLoMismoQue));
    }

    /**
     * Ninguna magnitud dos veces, y es la única regla de verificación que vive en el tipo.
     *
     * <p>Dos verificaciones de temperatura en el mismo aparato son la misma verificación escrita dos
     * veces: la segunda contradice a la primera sin que nada avise de cuál vale. El esquema lo impone
     * también, con un índice único parcial, y tenerlo en los dos sitios es deliberado.
     */
    private static List<TypeVerification> exigirMagnitudesDistintas(
            List<TypeVerification> verificaciones) {

        List<TypeVerification> declaradas =
                verificaciones == null ? List.of() : List.copyOf(verificaciones);

        for (int i = 0; i < declaradas.size(); i++) {
            for (int j = i + 1; j < declaradas.size(); j++) {
                if (declaradas.get(i).mideLaMismaMagnitudQue(declaradas.get(j))) {
                    throw new IllegalArgumentException(
                            "La magnitud " + declaradas.get(i).magnitud().nombre()
                                    + " esta declarada dos veces: un tipo de equipo se verifica una sola"
                                    + " vez en cada magnitud");
                }
            }
        }

        return declaradas;
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
        return new EquipmentTypePayload(nombre, isVerificable(), valorUnitarioMantenimiento);
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Un tipo de equipo necesita " + campo);
        }

        return valor.trim();
    }

    /** Un texto opcional en blanco es lo mismo que no tenerlo. */
    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static long validarValor(long valor) {
        if (valor < 0) {
            throw new IllegalArgumentException(
                    "El valor del mantenimiento no puede ser negativo, y se recibio " + valor);
        }

        return valor;
    }
}
