package com.malphasos.malphasos.equipment.application.ports.output;

import com.malphasos.malphasos.equipment.application.model.overview.EquipmentAreaSummary;
import com.malphasos.malphasos.equipment.application.model.overview.EquipmentOverview;
import com.malphasos.malphasos.equipment.application.model.overview.EquipmentOverviewQuery;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** La consulta del listado de equipos, sobre las tablas de este módulo. */
public interface EquipmentOverviewPersistencePort {

    /**
     * Una página, con las filas sin ubicación.
     *
     * @param areas las áreas a las que se limita; {@code null} no limita. Nunca vacío: un conjunto
     *              vacío es una página vacía, y eso lo decide quien llama sin preguntar
     */
    EquipmentOverview find(EquipmentOverviewQuery consulta, Set<UUID> areas);

    /** Los equipos en servicio contados por área, sin sede ni cliente. Mismo contrato de {@code areas}. */
    List<EquipmentAreaSummary> countByArea(Set<UUID> areas);
}
