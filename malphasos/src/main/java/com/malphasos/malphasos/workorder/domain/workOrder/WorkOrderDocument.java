package com.malphasos.malphasos.workorder.domain.workOrder;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que la orden era al ejecutarse (2026-10-28, {@code V49}).
 *
 * <p>Decidido por el usuario con el análisis del esquema: <b>mientras la orden vive se lee en vivo, y al
 * ejecutarla se congela</b>. Desde entonces corregir la sede, renombrar un área o trasladar un equipo no
 * cambia lo que dice una orden ya hecha. Es lo que {@code V37} hizo con el informe de cada reporte.
 *
 * <p><b>El relleno de {@code V49} escribe esta misma forma en SQL</b> para las órdenes ejecutadas antes,
 * y una prueba compara los dos: cambiar un campo aquí exige cambiarlo allí, o subir la versión.
 *
 * @param version la forma de este documento, por si un día cambia lo que se copia
 * @param fechaEjecucion el día en que se ejecutó, en la zona de la empresa; nula en las congeladas tarde,
 *     porque ninguna tabla lo guardaba
 * @param ingeniero el nombre completo del ingeniero a cargo
 * @param equipos por área, número de hoja de vida, serie e identificador
 */
public record WorkOrderDocument(
        int version,
        String numero,
        String tipoServicio,
        boolean garantia,
        String periodicidad,
        LocalDate fechaMantenimiento,
        LocalDate fechaEjecucion,
        Cliente cliente,
        Sede sede,
        String ingeniero,
        List<Equipo> equipos) {

    public static final int VERSION = 1;

    public WorkOrderDocument {
        Objects.requireNonNull(cliente, "El documento de una orden lleva su cliente");
        Objects.requireNonNull(sede, "El documento de una orden lleva su sede");
        equipos = equipos == null ? List.of() : List.copyOf(equipos);
    }

    public record Cliente(String nombre, String documento) {
    }

    public record Sede(String nombre, String direccion, String ciudad) {
    }

    /**
     * Un equipo de la orden y el área en la que se le prestó el servicio.
     *
     * @param numeroReporte el número de su reporte vigente; nulo si no tenía
     */
    public record Equipo(
            UUID id,
            String area,
            String tipo,
            String marca,
            String modelo,
            String serie,
            String numeroHojaVida,
            String numeroReporte) {
    }
}
