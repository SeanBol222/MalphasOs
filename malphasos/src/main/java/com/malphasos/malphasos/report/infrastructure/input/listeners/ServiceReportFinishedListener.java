package com.malphasos.malphasos.report.infrastructure.input.listeners;

import com.malphasos.malphasos.equipment.application.ports.input.InterventionRecordingPort;
import com.malphasos.malphasos.equipment.application.services.intervention.commands.RecordInterventionCommand;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionResult;
import com.malphasos.malphasos.equipment.domain.intervention.InterventionType;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.report.application.ports.input.ServiceReportServicePort;
import com.malphasos.malphasos.report.domain.serviceReport.ReportState;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.report.domain.serviceReport.events.ServiceReportFinishedEvent;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.workorder.application.ports.input.WorkOrderServicePort;
import com.malphasos.malphasos.workorder.domain.workOrder.WorkOrder;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Anota la intervención en la hoja de vida del equipo cuando su reporte se cierra.
 *
 * <p>Es <b>el primer consumidor de un evento de dominio de este sistema</b>. Hay <b>51</b> eventos
 * declarados en cinco módulos y, hasta este oyente, ninguno escuchado: el despachador estaba
 * construido y sin usar. (Este comentario decía «doce eventos», escrito de memoria al crearlo el
 * 2026-10-04; contados ese mismo día con {@code grep}, son 51 —14 de {@code client}, 18 de
 * {@code equipment}, 7 de {@code location}, 5 de {@code report} y 7 de {@code work-order}—. El
 * mensaje del commit que lo introdujo arrastra la cifra falsa.)
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
    private final ServiceReportServicePort serviceReportServicePort;
    private final PersonCommunicationPort personCommunicationPort;

    @EventListener
    public void onReportFinished(ServiceReportFinishedEvent evento) {
        // El tipo de servicio no viaja en el evento porque no es del reporte: es de la orden. Se
        // consulta aqui, que es el unico sitio donde se sabe que hace falta, y sin restriccion de
        // alcance porque esto no lo pide ningun usuario -- lo dispara un hecho del dominio.
        WorkOrder orden = workOrderServicePort.findById(
                evento.payload().idOrdenTrabajo(), ReadScope.unrestricted());
        InterventionType tipo = InterventionType.valueOf(orden.getTipoServicio().name());

        log.debug(
                "Anotando en la hoja de vida del equipo {} el cierre del reporte {}",
                evento.payload().idEquipoCliente(),
                evento.metadata().aggregateId());

        UUID idReporte = UUID.fromString(evento.metadata().aggregateId());

        // Que se hizo y quien lo hizo, desde V17: los procedimientos del reporte y el nombre del
        // ingeniero de la orden, congelados en la linea. No viajan en el evento porque el evento dice
        // que el reporte se cerro, no que contenia; se leen aqui, una vez, y no se vuelven a consultar.
        String procedimientos = serviceReportServicePort
                .findById(idReporte, ReadScope.unrestricted())
                .getProcedimientos();

        interventionRecordingPort.record(new RecordInterventionCommand(
                evento.payload().idEquipoCliente(),
                // El identificador del agregado viaja como texto en los metadatos del evento: el
                // contrato de eventos es deliberadamente agnostico del tipo de la llave.
                idReporte,
                evento.payload().finalizado(),
                tipo,
                InterventionResult.valueOf(evento.payload().resultado().name()),
                procedimientos,
                nombreDelIngeniero(orden),
                corregidosPor(idReporte, evento.payload().idOrdenTrabajo(), evento.payload().idEquipoCliente())));
    }

    /**
     * Los otros reportes cerrados de la misma orden y el mismo equipo: los que este cierre corrige.
     *
     * <p>Un reporte cerrado no se edita; se retira y se abre otro sobre la misma orden y el mismo
     * equipo. Como solo puede haber un reporte <b>activo</b> por ese par, cualquier otro reporte
     * cerrado del par es uno retirado al que este sustituye. Se calcula aquí porque es este módulo el
     * que conoce los reportes; {@code equipment} solo recibe la lista y no tiene que leer tablas
     * ajenas.
     *
     * <p>Se consulta la orden entera y se filtra en memoria: una orden tiene un reporte por equipo más
     * sus correcciones, y una consulta nueva para eso sería una pieza más que mantener.
     */
    /**
     * El nombre completo del ingeniero de la orden, o nada si la orden no tiene. Una orden se puede
     * empezar sin ingeniero, y un servicio sin responsable conocido se imprime con la raya.
     */
    private String nombreDelIngeniero(WorkOrder orden) {
        return orden.getIdIngeniero() == null
                ? null
                : personCommunicationPort.findById(orden.getIdIngeniero()).nombreCompleto();
    }

    private Set<UUID> corregidosPor(UUID idReporte, UUID idOrden, UUID idEquipo) {
        return serviceReportServicePort.findByWorkOrder(idOrden, ReadScope.unrestricted()).stream()
                .filter(otro -> idEquipo.equals(otro.getIdEquipoCliente()))
                .filter(otro -> otro.getEstado() == ReportState.FINALIZADO)
                .map(ServiceReport::getId)
                .filter(id -> !id.equals(idReporte))
                .collect(Collectors.toSet());
    }
}
