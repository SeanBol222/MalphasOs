package com.malphasos.malphasos.client.application.model;

import java.util.List;
import java.util.UUID;

/**
 * Una sede con sus áreas, de un vistazo: lo que necesitan los listados del rediseño (2026-10-11) para
 * contar sedes, áreas y ciudades de cada cliente sin pedir la ficha de cada sede.
 *
 * <p>Es un modelo de lectura: no es el agregado {@code Headquarter} ni lo sustituye. Lleva las sedes y
 * las áreas cerradas también, con su estado, porque contarlas o no es una decisión de quien pinta.
 */
public record HeadquarterSummary(
        UUID id,
        String nombre,
        UUID idCliente,
        UUID idCiudad,
        boolean abierta,
        List<Area> areas) {

    public HeadquarterSummary {
        areas = List.copyOf(areas);
    }

    public record Area(UUID id, String nombre, boolean abierta) {
    }
}
