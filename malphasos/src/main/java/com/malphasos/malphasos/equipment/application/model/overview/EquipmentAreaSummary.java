package com.malphasos.malphasos.equipment.application.model.overview;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Los equipos en servicio de un área, contados: lo que las fichas de cliente y de sede del rediseño
 * (2026-10-11) suman por sede y por cliente. Solo salen las áreas que tienen algún equipo en servicio.
 *
 * @param fueraDeServicio los que quedaron fuera de servicio en su último servicio cerrado
 * @param ultimoServicio  el más reciente de los últimos servicios de sus equipos; nulo si ninguno tuvo
 */
public record EquipmentAreaSummary(
        UUID idArea,
        UUID idSede,
        UUID idCliente,
        long equipos,
        long fueraDeServicio,
        LocalDateTime ultimoServicio) {

    public EquipmentAreaSummary enSede(UUID sede, UUID cliente) {
        return new EquipmentAreaSummary(idArea, sede, cliente, equipos, fueraDeServicio, ultimoServicio);
    }
}
