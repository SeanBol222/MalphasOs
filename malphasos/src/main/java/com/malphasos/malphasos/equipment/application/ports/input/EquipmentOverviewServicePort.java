package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.application.model.overview.EquipmentAreaSummary;
import com.malphasos.malphasos.equipment.application.model.overview.EquipmentOverview;
import com.malphasos.malphasos.equipment.application.model.overview.EquipmentOverviewQuery;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.List;
import java.util.UUID;

/** El listado de equipos instalados, paginado y filtrado en el servidor. */
public interface EquipmentOverviewServicePort {

    /** Una página de los equipos que caen dentro del alcance y cumplen la consulta. */
    EquipmentOverview list(EquipmentOverviewQuery consulta, ReadScope alcance);

    /** Los equipos en servicio contados por área, de todo el alcance o de un cliente. */
    List<EquipmentAreaSummary> summarizeByArea(UUID idCliente, ReadScope alcance);
}
