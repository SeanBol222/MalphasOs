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
 * @param version la forma de este documento, por si un día cambia lo que se copia
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
        String huellaFirmaIngeniero) {

    public static final int VERSION = 1;

    public ReportDocument {
        Objects.requireNonNull(orden, "El documento de un informe lleva su orden");
        Objects.requireNonNull(cliente, "El documento de un informe lleva su cliente");
        Objects.requireNonNull(equipo, "El documento de un informe lleva su equipo");
        verificaciones = verificaciones == null ? List.of() : List.copyOf(verificaciones);
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
            Integer voltaje,
            Integer potencia,
            BigDecimal amperaje,
            Integer frecuencia) {
    }

    /**
     * Cómo se verificaba el tipo del equipo: el nombre de la magnitud, su unidad, cuántas lecturas por
     * punto y sus puntos, en orden.
     */
    public record Verificacion(UUID id, String magnitud, String unidad, Integer cantidadDatos, List<Punto> puntos) {

        public Verificacion {
            puntos = copia(puntos);
        }
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
