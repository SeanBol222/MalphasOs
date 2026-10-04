package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.domain.intervention.Intervention;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.List;
import java.util.UUID;

/** Lo que los adaptadores de entrada de este módulo necesitan del historial de intervenciones. */
public interface InterventionServicePort {

    /**
     * El historial de un equipo, de la intervención más reciente a la más antigua.
     *
     * <p>El orden es parte del contrato y no una comodidad: el segundo criterio de aceptación de
     * RF-27 pide que el historial sea «consultable y ordenado cronológicamente», y el esquema lleva
     * un índice por (equipo, fecha descendente) para que ese orden no cueste una ordenación.
     *
     * @throws com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException si
     *     el equipo no existe o queda fuera del alcance de quien consulta
     */
    List<Intervention> findByEquipment(UUID idEquipoCliente, ReadScope alcance);
}
