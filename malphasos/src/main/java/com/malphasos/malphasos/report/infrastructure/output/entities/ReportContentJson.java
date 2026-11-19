package com.malphasos.malphasos.report.infrastructure.output.entities;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * La forma de {@code reporte_servicio.t_contenido} (V59): lo que el reporte dice, en un solo documento.
 *
 * <p>Es un modelo de persistencia y no del dominio, como las entidades de al lado: el agregado no sabe que
 * se guarda en JSON. Los nombres son los de la migración, clave por clave, porque los dos escriben el mismo
 * documento —V59 los de antes, el mapper los de ahora— y los dos los lee el mismo código.
 *
 * <p>Solo lo vigente: una lectura corregida o un accesorio quitado no se guardan (decidido por el usuario
 * con V59). Y nada del catálogo: el nombre de un paso, la unidad, la tolerancia, el valor de un punto o la
 * ficha de un patrón se leen del catálogo en un borrador y de {@code t_documento} en un cerrado.
 *
 * @param version la forma del documento; hoy {@value #VERSION}
 */
public record ReportContentJson(
        int version,
        String falla,
        String diagnostico,
        String causa,
        String repuestos,
        String procedimientos,
        String observaciones,
        Ingreso ingreso,
        Recibe recibe,
        List<Paso> pasos,
        List<Accesorio> accesorios,
        List<Verificacion> verificaciones) {

    public static final int VERSION = 1;

    public ReportContentJson {
        pasos = pasos == null ? List.of() : List.copyOf(pasos);
        accesorios = accesorios == null ? List.of() : List.copyOf(accesorios);
        verificaciones = verificaciones == null ? List.of() : List.copyOf(verificaciones);
    }

    /** Las dos verificaciones al ingreso; cada una es nula mientras no se registra. */
    public record Ingreso(Estado fisico, Estado operativo) {
    }

    public record Estado(String estado, String observacion) {
    }

    /** Quien recibe sin ser persona registrada. Si lo es, va en {@code k_id_recibe} y esto es nulo. */
    public record Recibe(String nombre, String cedula, String cargo) {
    }

    /** Un paso del protocolo, por el protocolo del que salió: un reporte no repite protocolo. */
    public record Paso(UUID protocolo, String estado, String nota) {
    }

    /** Un accesorio; su orden es su posición en el arreglo. */
    public record Accesorio(String nombre, boolean conforme, String observacion) {
    }

    /** Una verificación del tipo del equipo, con los patrones que se usaron en ella y sus lecturas. */
    public record Verificacion(UUID id, List<Patron> patrones, List<Lectura> lecturas) {

        public Verificacion {
            patrones = patrones == null ? List.of() : List.copyOf(patrones);
            lecturas = lecturas == null ? List.of() : List.copyOf(lecturas);
        }
    }

    /**
     * Un patrón elegido en la verificación.
     *
     * @param desdeOrden si llegó de los patrones de la orden; hasta que existan, siempre falso
     */
    public record Patron(UUID id, boolean desdeOrden) {
    }

    /**
     * Una lectura: dónde se tomó y lo que marcaron el patrón y el equipo.
     *
     * @param punto nulo en una verificación de modalidad variable, que no declara puntos
     */
    public record Lectura(UUID punto, int secuencia, BigDecimal patron, BigDecimal equipo) {
    }
}
