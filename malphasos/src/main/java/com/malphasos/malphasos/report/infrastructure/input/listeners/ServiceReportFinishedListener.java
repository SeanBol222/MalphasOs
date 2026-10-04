package com.malphasos.malphasos.report.infrastructure.input.listeners;

import com.malphasos.malphasos.equipment.application.ports.input.InterventionRecordingPort;
import com.malphasos.malphasos.equipment.application.services.intervention.commands.RecordInterventionCommand;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionType;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportFinishedEvent;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Anota la intervención en la hoja de vida del equipo cuando su reporte se cierra.
 *
 * <p>Es <b>el primer consumidor de un evento de dominio de este sistema</b>. Hasta ahora había doce
 * eventos publicándose y ninguno escuchado, y el despachador estaba construido y sin usar.
 *
 * <p>Con esto se cierra el segundo criterio de RF-26 —«no se requiere acción manual para actualizar el
 * historial»— de la única forma que lo cierra de verdad: no hay ninguna operación que anote una
 * intervención a mano, así que la única manera de que aparezca una línea es que un reporte se cierre.
 *
 * <h2>Por qué vive aquí y no donde la ERS lo reservó</h2>
 *
 * <p>La ERS dice que el oyente irá en {@code equipment/infrastructure/input/listeners/}, y <b>ahí no
 * puede estar</b>: consumir {@link ServiceReportFinishedEvent} obliga a conocer el módulo de
 * reportes, y la dependencia va en el otro sentido —{@code report} importa {@code equipment}—. Un
 * oyente en {@code equipment} habría creado un ciclo entre los dos módulos. Vive en el lado que ya
 * conoce al otro, y entra por {@link InterventionRecordingPort}, el puerto que {@code equipment}
 * publica. El directorio que la ERS reservó queda vacío y conviene retirarlo: un directorio reservado
 * para algo que acabó en otro sitio es indistinguible de uno olvidado.
 *
 * <h2>Síncrono y en la misma transacción, a propósito</h2>
 *
 * <p>{@code @EventListener} corre dentro de la transacción de quien publica, y los eventos de este
 * proyecto se publican después de persistir pero <b>antes de confirmar</b>. La consecuencia es que
 * cerrar el reporte y anotar la intervención son <b>atómicos</b>: o pasan los dos o no pasa ninguno.
 *
 * <p>La alternativa era {@code @TransactionalEventListener(AFTER_COMMIT)}, que anota en una
 * transacción nueva una vez confirmado el cierre. Se descartó porque su modo de fallo es peor: el
 * reporte quedaría cerrado y su historial vacío <b>sin que nada lo note</b>, y ese hueco no se puede
 * reparar sin una operación manual que RF-26 prohíbe tener.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceReportFinishedListener {

    private final InterventionRecordingPort interventionRecordingPort;
    private final WorkOrderServicePort workOrderServicePort;

    @EventListener
    public void onReportFinished(ServiceReportFinishedEvent evento) {
        // El tipo de servicio no viaja en el evento porque no es del reporte: es de la orden. Se
        // consulta aqui, que es el unico sitio donde se sabe que hace falta, y sin restriccion de
        // alcance porque esto no lo pide ningun usuario -- lo dispara un hecho del dominio.
        InterventionType tipo = InterventionType.valueOf(workOrderServicePort
                .findById(evento.payload().idOrdenTrabajo(), ReadScope.unrestricted())
                .getTipoServicio()
                .name());

        log.debug(
                "Anotando en la hoja de vida del equipo {} el cierre del reporte {}",
                evento.payload().idEquipoCliente(),
                evento.metadata().aggregateId());

        interventionRecordingPort.record(new RecordInterventionCommand(
                evento.payload().idEquipoCliente(),
                // El identificador del agregado viaja como texto en los metadatos del evento: el
                // contrato de eventos es deliberadamente agnostico del tipo de la llave.
                UUID.fromString(evento.metadata().aggregateId()),
                evento.payload().finalizado(),
                tipo,
                InterventionResult.valueOf(evento.payload().resultado().name())));
    }
}
