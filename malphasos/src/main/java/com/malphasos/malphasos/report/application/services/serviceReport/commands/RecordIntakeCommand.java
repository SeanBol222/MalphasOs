package com.malphasos.malphasos.report.application.services.serviceReport.commands;

import com.malphasos.malphasos.report.domain.serviceReport.IntakeState;
import java.util.List;
import java.util.UUID;

/**
 * Cómo llegó el equipo: las dos verificaciones al ingreso y sus accesorios, que sustituyen a los que
 * hubiera (V31). El orden de los accesorios es el de la lista.
 */
public record RecordIntakeCommand(
        UUID id,
        IntakeState fisico,
        String observacionFisico,
        IntakeState operativo,
        String observacionOperativo,
        List<Accessory> accesorios) {

    /** Un accesorio tal como llega: el orden lo pone su posición en la lista. */
    public record Accessory(String nombre, boolean conforme, String observacion) {
    }
}
