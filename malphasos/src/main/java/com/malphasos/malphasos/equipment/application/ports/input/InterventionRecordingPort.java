package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.application.services.intervention.commands.RecordInterventionCommand;

/**
 * Lo que este módulo publica hacia los demás para anotar una intervención en la hoja de vida.
 *
 * <p><b>Existe por una restricción del grafo de dependencias, y conviene dejarla escrita porque la
 * ERS dice lo contrario.</b> El documento reserva {@code equipment/infrastructure/input/listeners/}
 * para «el oyente que reaccione al cierre de un reporte», y ese oyente <b>no puede vivir ahí</b>:
 * consumir {@code ServiceReportFinishedEvent} obligaría a {@code equipment} a importar
 * {@code report}, y la dependencia va en el otro sentido desde que el módulo de reportes existe
 * —{@code report} importa {@code equipment}, no al contrario—. Ponerlo donde el documento lo
 * reservó habría creado un ciclo entre dos módulos.
 *
 * <p>Así que el oyente vive en {@code report}, que ya conoce a este módulo, y entra por aquí. Es el
 * mismo patrón que {@code PersonCommunicationPort} y {@code ClientOwnershipPort}: una superficie
 * mínima, con un DTO de la capa de aplicación, para que el otro lado no tenga que conocer este
 * dominio.
 */
public interface InterventionRecordingPort {

    /**
     * Anota la intervención, si ese reporte no está anotado ya.
     *
     * <p><b>Es idempotente, y eso no es un adorno</b>: un oyente de eventos puede recibir el mismo
     * hecho dos veces —un reintento, un reenvío— y el historial no puede contar dos veces el mismo
     * mantenimiento. La garantía última es del esquema, que tiene un único sobre el reporte y no lo
     * declara parcial a propósito; aquí se comprueba antes para no provocar una violación de
     * integridad en el camino normal.
     */
    void record(RecordInterventionCommand command);
}
