package com.malphasos.malphasos.equipment.domain.equipmentType;

import com.malphasos.malphasos.equipment.domain.magnitude.Magnitude;
import com.malphasos.malphasos.equipment.domain.magnitude.MeasurementUnit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Una de las cosas que se verifican en un tipo de equipo: qué magnitud, con qué unidad, con qué
 * modalidad, cuántas lecturas por punto y en qué valores.
 *
 * <p><b>Por qué existe este nivel.</b> Hasta el 2026-10-03 el tipo de equipo declaraba <i>una</i>
 * modalidad, <i>una</i> cantidad de lecturas y una lista de puntos, cada uno con su unidad. Un
 * termohigrómetro lo desmiente: mide temperatura <b>y</b> humedad relativa, en unidades distintas, en
 * puntos distintos —0 °C no es 40 %HR— y a veces con modalidades distintas. Con el modelo anterior
 * había que registrarlo como dos tipos de equipo, que es mentira: es un aparato, con una hoja de vida
 * y un reporte.
 *
 * <p><b>No es un agregado.</b> No tiene vida fuera de su {@link EquipmentType}: se entra y se sale por
 * él, igual que un {@link VerificationPoint} se entra y se sale por su verificación. Lo que sí tiene es
 * identidad propia, porque una lectura de un reporte apunta a ella.
 *
 * <p>Las tres reglas que atan modalidad, cantidad y puntos <b>bajaron aquí</b> desde el tipo, que es su
 * sitio: ahora pueden valer distinto para la temperatura y para la humedad del mismo aparato.
 *
 * <p><b>Lleva la magnitud y la unidad enteras, no sus identificadores</b>, y es una excepción
 * razonada a la convención de referenciar por identificador. Esa convención guarda fronteras entre
 * <i>agregados</i>, y ni {@link Magnitude} ni {@link MeasurementUnit} lo son: son datos de referencia
 * inmutables del mismo módulo, que entran sembrados y nadie edita. Con solo los identificadores, ni el
 * reporte ni la pantalla podrían decir «temperatura en °C» sin volver a consultar el catálogo — y el
 * servicio ya lo consulta para validarlos, así que embeberlos no cuesta una consulta más.
 *
 * @param magnitud qué se mide
 * @param unidad   en qué unidad. El esquema garantiza que es una unidad <i>de esa</i> magnitud
 * @param puntos   los valores constantes, <b>retirados incluidos</b>: con ellos se firmaron reportes
 * @param tolerancia el error admitido, en la unidad de la verificación (V26, 2026-10-14). Nula si no se
 *                   declaró: los tipos anteriores no la tienen, y la conformidad dice entonces «sin
 *                   tolerancia» en lugar de inventarse una. La regla de conformidad no vive aquí sino
 *                   en la lectura del reporte, que copia la tolerancia al tomarse (V27)
 */
public record TypeVerification(
        UUID id,
        Magnitude magnitud,
        MeasurementUnit unidad,
        VerificationMode modalidad,
        Integer cantidadDatos,
        List<VerificationPoint> puntos,
        boolean estadoActivo,
        BigDecimal tolerancia) {

    /** El tope que el esquema declara. Aquí para que el mensaje lo pueda citar. */
    public static final int MAXIMO_DATOS = 100;

    /** Los decimales que la columna guarda, los mismos que los puntos y las lecturas. */
    public static final int DECIMALES_TOLERANCIA = 4;

    /** Sin tolerancia: la forma de antes de V26, que siguen usando quienes no la declaran. */
    public TypeVerification(
            UUID id,
            Magnitude magnitud,
            MeasurementUnit unidad,
            VerificationMode modalidad,
            Integer cantidadDatos,
            List<VerificationPoint> puntos,
            boolean estadoActivo) {
        this(id, magnitud, unidad, modalidad, cantidadDatos, puntos, estadoActivo, null);
    }

    /**
     * Solo lo que no puede faltar nunca, ni leyendo de la base.
     *
     * <p>Las reglas cruzadas —modalidad contra cantidad contra puntos— <b>no van aquí</b>: viven en
     * {@link #validar} y las aplica {@link #of}. Es la misma razón por la que el agregado tiene
     * {@code rehydrate}: una fila vieja que ya no cumple una regla nueva tiene que poder cargarse para
     * poder corregirla, y si la regla estuviera en el constructor no habría forma de leerla.
     */
    public TypeVerification {
        if (id == null) {
            throw new IllegalArgumentException("Una verificacion necesita su identificador");
        }
        if (magnitud == null) {
            throw new IllegalArgumentException("Una verificacion necesita saber que magnitud mide");
        }
        if (unidad == null) {
            throw new IllegalArgumentException("Una verificacion necesita su unidad");
        }
        if (!unidad.esDeLaMagnitud(magnitud.id())) {
            // Ultima defensa, no la primera: el servicio lo comprueba antes para dar un mensaje con
            // codigo propio, y desde V48 el esquema guarda solo la unidad. Aqui esta para que el
            // objeto no pueda existir en ese estado ni siquiera dentro de una prueba.
            throw new IllegalArgumentException(
                    "La unidad " + unidad.simbolo() + " no es de la magnitud " + magnitud.nombre());
        }
        if (modalidad == null) {
            throw new IllegalArgumentException(
                    "Una verificacion necesita su modalidad: una que no dice como se hace no es una"
                            + " verificacion");
        }

        puntos = puntos == null ? List.of() : List.copyOf(puntos);
    }

    /** Una verificación nueva, activa, con sus reglas comprobadas. */
    public static TypeVerification of(
            Magnitude magnitud,
            MeasurementUnit unidad,
            VerificationMode modalidad,
            Integer cantidadDatos,
            List<VerificationPoint> puntos) {

        return of(magnitud, unidad, modalidad, cantidadDatos, puntos, null);
    }

    /** Lo mismo, con la tolerancia de V26. */
    public static TypeVerification of(
            Magnitude magnitud,
            MeasurementUnit unidad,
            VerificationMode modalidad,
            Integer cantidadDatos,
            List<VerificationPoint> puntos,
            BigDecimal tolerancia) {

        List<VerificationPoint> pedidos = validar(modalidad, cantidadDatos, puntos);

        return new TypeVerification(UUID.randomUUID(), magnitud, unidad, modalidad, cantidadDatos, pedidos,
                true, validarTolerancia(tolerancia));
    }

    /** Una verificación que vuelve de la base, con su estado tal como está guardado y sin validar. */
    public static TypeVerification rehydrate(
            UUID id,
            Magnitude magnitud,
            MeasurementUnit unidad,
            VerificationMode modalidad,
            Integer cantidadDatos,
            List<VerificationPoint> puntos,
            boolean estadoActivo) {

        return rehydrate(id, magnitud, unidad, modalidad, cantidadDatos, puntos, estadoActivo, null);
    }

    /** Lo mismo, con la tolerancia de V26. */
    public static TypeVerification rehydrate(
            UUID id,
            Magnitude magnitud,
            MeasurementUnit unidad,
            VerificationMode modalidad,
            Integer cantidadDatos,
            List<VerificationPoint> puntos,
            boolean estadoActivo,
            BigDecimal tolerancia) {

        return new TypeVerification(
                id, magnitud, unidad, modalidad, cantidadDatos, puntos, estadoActivo, tolerancia);
    }

    /**
     * La misma verificación, retirada, <b>y sus puntos con ella</b>.
     *
     * <p>Retirar la verificación y dejar sus puntos activos dejaría puntos vivos colgando de algo que ya
     * no se verifica, y la pantalla los seguiría ofreciendo.
     */
    public TypeVerification deactivated() {
        return new TypeVerification(
                id,
                magnitud,
                unidad,
                modalidad,
                cantidadDatos,
                puntos.stream().map(punto -> punto.estadoActivo() ? punto.deactivated() : punto).toList(),
                false,
                tolerancia);
    }

    /** Los puntos en los que se verifica hoy, ordenados por valor, que es como se recorren. */
    public List<VerificationPoint> puntosActivos() {
        return puntos.stream()
                .filter(VerificationPoint::estadoActivo)
                .sorted((uno, otro) -> uno.valor().compareTo(otro.valor()))
                .toList();
    }

    /** Cuántas lecturas pide esta verificación en total: una por punto y por dato. */
    public int lecturasEsperadas() {
        return cantidadDatos == null ? 0 : puntosActivos().size() * cantidadDatos;
    }

    /** Si las dos miden la misma magnitud. Un tipo no declara dos veces la misma. */
    public boolean mideLaMismaMagnitudQue(TypeVerification otra) {
        return magnitud.id().equals(otra.magnitud.id());
    }

    /** Si la otra declara exactamente lo mismo, puntos incluidos. Lo usa el «no emitir si no cambia». */
    public boolean declaraLoMismoQue(TypeVerification otra) {
        if (!magnitud.id().equals(otra.magnitud.id())
                || !unidad.id().equals(otra.unidad.id())
                || modalidad != otra.modalidad
                || !Objects.equals(cantidadDatos, otra.cantidadDatos)
                || !mismaTolerancia(tolerancia, otra.tolerancia)) {

            return false;
        }

        List<VerificationPoint> mios = puntosActivos();
        List<VerificationPoint> suyos = otra.puntosActivos();

        return mios.size() == suyos.size()
                && suyos.stream().allMatch(suyo -> mios.stream().anyMatch(suyo::mideLoMismoQue));
    }

    /** 0,5 y 0,50 son la misma tolerancia: se comparan por valor, no por escala. */
    private static boolean mismaTolerancia(BigDecimal una, BigDecimal otra) {
        return una == null ? otra == null : otra != null && una.compareTo(otra) == 0;
    }

    /** Positiva y con los decimales que la columna guarda, o nula. */
    private static BigDecimal validarTolerancia(BigDecimal tolerancia) {
        if (tolerancia == null) {
            return null;
        }
        if (tolerancia.signum() <= 0) {
            throw new IllegalArgumentException(
                    "La tolerancia tiene que ser mayor que cero: una tolerancia cero exige una igualdad que"
                            + " ningun instrumento da");
        }
        if (tolerancia.stripTrailingZeros().scale() > DECIMALES_TOLERANCIA) {
            throw new IllegalArgumentException(
                    "La tolerancia admite como mucho " + DECIMALES_TOLERANCIA + " decimales");
        }

        return tolerancia;
    }

    /**
     * Las tres reglas que atan modalidad, cantidad y puntos, en un solo sitio.
     *
     * <p>Están aquí y no solo en el esquema porque el esquema no puede mirar la tabla de puntos desde el
     * {@code CHECK} de la verificación: que una modalidad constante exija <b>al menos un punto</b> solo
     * se puede comprobar aquí. Las otras dos las impone también la base, y tenerlas en los dos sitios es
     * deliberado — el dominio da el mensaje y la base garantiza que nadie lo esquive por SQL.
     */
    private static List<VerificationPoint> validar(
            VerificationMode modalidad, Integer cantidadDatos, List<VerificationPoint> puntos) {

        List<VerificationPoint> pedidos = puntos == null ? List.of() : List.copyOf(puntos);

        if (modalidad == VerificationMode.PATRON_EQUIPO_VARIABLE) {
            if (cantidadDatos != null) {
                throw new IllegalArgumentException(
                        "Con patron y equipo variables no se declara cuantas lecturas se toman: lo decide"
                                + " el ingeniero en campo");
            }
            if (!pedidos.isEmpty()) {
                throw new IllegalArgumentException(
                        "Con patron y equipo variables no hay puntos de verificacion: no hay nada"
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
                            "El punto " + pedidos.get(i).valor() + " esta declarado dos veces en la misma"
                                    + " verificacion");
                }
            }
        }

        return pedidos;
    }
}
