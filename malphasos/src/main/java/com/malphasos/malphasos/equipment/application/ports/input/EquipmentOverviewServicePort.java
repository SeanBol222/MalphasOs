package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.application.model.overview.EquipmentOverview;
import com.malphasos.malphasos.equipment.application.model.overview.EquipmentOverviewQuery;
import com.malphasos.malphasos.shared.application.model.ReadScope;

/** El listado de equipos instalados, paginado y filtrado en el servidor. */
public interface EquipmentOverviewServicePort {

    /** Una página de los equipos que caen dentro del alcance y cumplen la consulta. */
    EquipmentOverview list(EquipmentOverviewQuery consulta, ReadScope alcance);
}
