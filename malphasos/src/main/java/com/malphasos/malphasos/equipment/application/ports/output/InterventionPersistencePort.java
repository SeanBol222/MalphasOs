package com.malphasos.malphasos.equipment.application.ports.output;

import com.malphasos.malphasos.equipment.domain.intervention.Intervention;
import java.util.List;
import java.util.UUID;

/**
 * Lo que la capa de aplicación necesita de un almacén de intervenciones.
 *
 * <p>Sin {@code delete} y <b>sin {@code update}</b>, y lo segundo es más fuerte que la convención
 * general del proyecto: una intervención no se modifica nunca. Describe algo que pasó.
 */
public interface InterventionPersistencePort {

    /** El historial de un equipo, de lo más reciente a lo más antiguo. */
    List<Intervention> findByEquipment(UUID idEquipoCliente);

    /** Si ese reporte ya tiene su línea en el historial. Sostiene la idempotencia del registro. */
    boolean existsByReport(UUID idReporteServicio);

    Intervention save(Intervention intervention);
}
