package com.malphasos.malphasos.report.domain.serviceReport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que el informe imprime y no es del reporte: la orden, el cliente, el equipo, cómo se verifica, la
 * empresa y quién hizo el servicio (2026-10-17).
 *
 * <p>Decidido por el usuario con el diseño único del informe: <b>el informe se congela al cerrar</b>.
 * Mientras el reporte es borrador esto se compone en vivo cada vez que se pide; al cerrarse se copia y
 * se guarda con él, y desde ahí corregir el equipo, el cliente o la empresa no cambia un informe ya
 * entregado. Es la misma decisión que con los pasos del protocolo, la unidad de cada lectura y la ficha
 * de cada patrón, llevada a todo lo demás que se imprime.
 *
 * <p>El logo no viaja aquí: se guarda una vez por versión distinta, y el documento lleva su huella.
 *
 * <p><b>Desde la versión 2 ({@code V51}, 2026-10-28) lleva también lo que el reporte copiaba en sus filas</b>:
 * el texto y la posición de cada paso del protocolo, la tolerancia de cada verificación y la ficha de cada
 * patrón. Decidido por el usuario con el análisis del esquema: <b>una sola fuente para cada momento</b>.
 * Mientras el reporte es borrador todo se lee en vivo del catálogo, y al cerrarse, de aquí. Las filas del
 * reporte se quedan con lo que es suyo: qué paso se marcó, qué se midió, qué patrón se eligió. {@code V51}
 * llevó a esta versión los documentos que ya existían, leyendo las copias antes de quitarlas.
 *
 * @param version la forma de este documento, por si un día cambia lo que se copia
 * @param pasos los pasos del protocolo del reporte, en su orden
 * @param patrones los patrones elegidos en cada verificación, con su ficha
 * @param empresa nula si al componerlo no había empresa configurada: el informe sale sin membrete
 * @param ingeniero el nombre completo del ingeniero de la orden; nulo si la orden no tenía
 * @param huellaFirmaIngeniero la firma que el ingeniero tenía guardada (V38), por su huella; nula si no
 *     tenía ninguna, y entonces la línea se firma a mano
 */
public record ReportDocument(
        int version,
        Orden orden,
        Cliente cliente,
        Equipo equipo,
        List<Verificacion> verificaciones,
        Empresa empresa,
        String ingeniero,
        String huellaFirmaIngeniero,
        List<Paso> pasos,
        List<Patron> patrones) {

    public static final int VERSION = 2;

    public ReportDocument {
        Objects.requireNonNull(orden, "El documento de un informe lleva su orden");
        Objects.requireNonNull(cliente, "El documento de un informe lleva su cliente");
        Objects.requireNonNull(equipo, "El documento de un informe lleva su equipo");
        verificaciones = verificaciones == null ? List.of() : List.copyOf(verificaciones);
        pasos = copia(pasos);
        patrones = copia(patrones);
    }

    /** La ficha de un patrón elegido en una verificación, por los dos identificadores. Vacía si no está. */
    public java.util.Optional<UsedReferenceStandard> patron(UUID idVerificacion, UUID idEquipoPatron) {
        return patrones.stream()
                .filter(p -> p.idVerificacion().equals(idVerificacion) && p.ficha().id().equals(idEquipoPatron))
                .map(Patron::ficha)
                .findFirst();
    }

    /** Un paso por el protocolo del que salió. Vacío si no está. */
    public java.util.Optional<Paso> paso(UUID idProtocolo) {
        return pasos.stream().filter(paso -> paso.idProtocolo().equals(idProtocolo)).findFirst();
    }

    /** Una verificación por su identificador. Vacía si no está. */
    public java.util.Optional<Verificacion> verificacion(UUID idVerificacion) {
        return verificaciones.stream().filter(v -> v.id().equals(idVerificacion)).findFirst();
    }

    /** De la orden: el tipo de servicio y la garantía marcan las casillas; la periodicidad da el próximo. */
    public record Orden(
            String numero, String tipoServicio, boolean garantia, String periodicidad, LocalDate fechaProgramada) {
    }

    /** De quién es el equipo y dónde está, con quién responde por él. */
    public record Cliente(
            String nombre,
            String documento,
            String sede,
            String direccionSede,
            String ciudadSede,
            String area,
            List<String> responsables,
            List<String> telefonos,
            List<String> correos) {

        public Cliente {
            responsables = copia(responsables);
            telefonos = copia(telefonos);
            correos = copia(correos);
        }
    }

    /** Qué equipo es, con lo que su modelo y su tipo dicen de él. */
    public record Equipo(
            String tipo,
            String marca,
            String modelo,
            String serie,
            String numeroHojaVida,
            String numeroInventario,
            String codigoInterno,
            String tecnologia,
            String uso,
            String riesgo,
            String alimentacion,
            BigDecimal voltaje,
            Integer potencia,
            BigDecimal amperaje,
            Integer frecuencia) {
    }

    /**
     * Cómo se verificaba el tipo del equipo: el nombre de la magnitud, su unidad, cuántas lecturas por
     * punto, su tolerancia y sus puntos, en orden.
     *
     * @param tolerancia la que decide si una lectura es conforme, desde la versión 2; nula si la
     *     verificación no tenía
     */
    public record Verificacion(
            UUID id, String magnitud, String unidad, Integer cantidadDatos, BigDecimal tolerancia, List<Punto> puntos) {

        public Verificacion {
            puntos = copia(puntos);
        }
    }

    /**
     * Un paso del protocolo: de qué protocolo salió, en qué posición iba y qué decía (versión 2).
     *
     * @param orden la prioridad del protocolo en el tipo del equipo; nula en uno que el tipo ya no tiene
     *     asignado, que va al final
     */
    public record Paso(UUID idProtocolo, Integer orden, String nombre, String descripcion, String indicacion) {
    }

    /** Un patrón elegido en una verificación, con la ficha que tenía (versión 2). */
    public record Patron(UUID idVerificacion, UsedReferenceStandard ficha) {
    }

    public record Punto(UUID id, BigDecimal valor) {
    }

    /** La empresa que presta el servicio, como estaba. El logo, por su huella. */
    public record Empresa(
            String nombre,
            String nit,
            String direccion,
            String ciudad,
            String movil,
            String garantia,
            String aclaracion,
            String codigoInforme,
            String huellaDelLogo) {
    }

    private static <T> List<T> copia(List<T> lista) {
        return lista == null ? List.of() : List.copyOf(lista);
    }
}
