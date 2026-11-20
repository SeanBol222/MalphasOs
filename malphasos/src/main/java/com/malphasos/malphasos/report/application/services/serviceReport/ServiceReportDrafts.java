package com.malphasos.malphasos.report.application.services.serviceReport;

import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ModelServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ProtocolServicePort;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.report.application.ports.output.ServiceReportPersistencePort;
import com.malphasos.malphasos.report.domain.serviceReport.ReportStep;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import com.malphasos.malphasos.workorder.domain.workOrder.ServiceType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Abre el borrador de un equipo en una orden (2026-10-28, {@code V50}).
 *
 * <p>Desde que no hay tabla puente, <b>abrir el borrador es poner el equipo en la orden</b>, y se hace
 * por dos puertas: al añadir el equipo a la orden —{@code work-order} lo pide por
 * {@code WorkOrderReportsPort}— y al sustituir un reporte cerrado, que abre el siguiente en el mismo paso.
 * Las dos tienen que dejar el mismo borrador, así que vive aquí y no en ninguna de las dos.
 *
 * <p><b>No depende de {@code WorkOrderServicePort}</b>, y es a propósito: la primera puerta es el propio
 * servicio de órdenes, y depender de él cerraría un ciclo entre beans. Lo que necesita de la orden —su
 * identificador y su tipo de servicio— se lo pasa quien llama.
 */
@Component
@RequiredArgsConstructor
public class ServiceReportDrafts {

    /** Cuántas veces se intenta un identificador nuevo antes de rendirse. Con 16,7 millones, sobra. */
    static final int INTENTOS_DE_NUMERO = 10;

    private final ServiceReportPersistencePort serviceReportPersistencePort;
    private final ClientEquipmentServicePort clientEquipmentServicePort;
    private final ModelServicePort modelServicePort;
    private final EquipmentServicePort equipmentServicePort;
    private final ProtocolServicePort protocolServicePort;
    private final EventDispatcherPort eventDispatcherPort;
    private final WorkOrderStandardsDistributor workOrderStandardsDistributor;

    /**
     * Abre y guarda el borrador, y publica que se abrió.
     *
     * @param patrones los que la orden lleva para el tipo del equipo (V60): el borrador los recibe marcados
     *                 {@code desdeOrden}, en las verificaciones que el catálogo les permite
     */
    public ServiceReport open(
            UUID idOrdenTrabajo, ServiceType tipoServicio, UUID idEquipoCliente, Set<UUID> patrones) {
        UUID idTipo = tipoServicio == ServiceType.PREVENTIVO || !patrones.isEmpty() ? tipoDe(idEquipoCliente) : null;
        List<ReportStep> pasos = pasosDelProtocolo(tipoServicio, idTipo);
        ServiceReport reporte = conNumeroLibre(() -> ServiceReport.open(idOrdenTrabajo, idEquipoCliente, pasos));

        if (!patrones.isEmpty()) {
            workOrderStandardsDistributor.apply(reporte, idTipo, patrones, Set.of());
        }

        ServiceReport guardado = serviceReportPersistencePort.save(reporte);
        reporte.pullEvents().forEach(eventDispatcherPort::dispatch);

        return guardado;
    }

    /**
     * Un reporte cuyo número —los seis primeros caracteres del id— no tenga ya otro (V28).
     *
     * <p>Git alarga el prefijo cuando dos commits lo comparten; aquí no se puede, porque el número va
     * impreso en un documento firmado y no puede cambiar después. Así que se elige otro identificador.
     * Los reportes descartados no emiten nada: sus eventos no se publican, porque no se guardan.
     */
    private ServiceReport conNumeroLibre(Supplier<ServiceReport> nuevo) {
        for (int intento = 0; intento < INTENTOS_DE_NUMERO; intento++) {
            ServiceReport reporte = nuevo.get();

            if (!serviceReportPersistencePort.numberTaken(reporte.numero())) {
                return reporte;
            }
        }

        throw new IllegalStateException("No se encontro un numero de reporte libre en "
                + INTENTOS_DE_NUMERO + " intentos");
    }

    /**
     * Los protocolos del preventivo del tipo del equipo, como pasos pendientes. RF-13: se cargan solos,
     * sin que nadie los elija. Desde V51 un paso solo dice de qué protocolo es: su texto y su posición se
     * leen del catálogo mientras el reporte es borrador, y del documento cuando se cierra.
     *
     * <p>Solo en una orden preventiva, decidido por el usuario el 2026-10-10: un correctivo es una
     * reparación distinta cada vez y se describe con texto libre. Se copian al abrir el borrador, que
     * desde {@code V50} es al añadir el equipo. El relleno de {@code V50} copia los mismos, con la misma
     * regla, a los equipos que estaban en una orden sin reporte.
     *
     * <p>Camina la cadena unidad → modelo → equipo del catálogo hasta el tipo, como
     * {@code ServiceReportService.tipoDelEquipo}; aquí solo hace falta su identificador.
     */
    private List<ReportStep> pasosDelProtocolo(ServiceType tipoServicio, UUID idTipo) {
        if (tipoServicio != ServiceType.PREVENTIVO) {
            return List.of();
        }

        return protocolServicePort.protocolsOf(idTipo).stream()
                .map(asignado -> ReportStep.pending(asignado.protocolo().getId()))
                .toList();
    }

    /** El tipo del equipo, caminando unidad → modelo → equipo del catálogo. */
    private UUID tipoDe(UUID idEquipoCliente) {
        ClientEquipment unidad = clientEquipmentServicePort.findById(idEquipoCliente, ReadScope.unrestricted());
        UUID idEquipoCatalogo = modelServicePort.findById(unidad.getIdModelo()).getIdEquipo();

        return equipmentServicePort.findById(idEquipoCatalogo).getIdTipoEquipo();
    }
}
