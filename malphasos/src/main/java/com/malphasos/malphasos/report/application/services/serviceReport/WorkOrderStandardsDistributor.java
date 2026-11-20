package com.malphasos.malphasos.report.application.services.serviceReport;

import com.malphasos.malphasos.equipment.application.ports.input.VerificationStandardsServicePort;
import com.malphasos.malphasos.equipment.domain.verificationStandards.VerificationStandards;
import com.malphasos.malphasos.report.domain.serviceReport.ServiceReport;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Pone y quita en un reporte los patrones que le llegan de su orden (V60, 2026-11-19).
 *
 * <p>Un patrón de la orden va a <b>cada verificación vigente del tipo que el catálogo le permite</b>: el
 * simulador de presión, a la de presión del monitor y no a su frecuencia. Llega marcado
 * {@code desdeOrden}. Quitarlo de la orden lo saca solo de donde llegó por ella: lo que el ingeniero puso a
 * mano se queda aunque sea el mismo patrón.
 *
 * <p>Lo usan las dos puertas que tocan un borrador desde la orden: abrirlo, con lo que la orden ya lleva, y
 * cambiar los patrones de la orden, con lo que cambió.
 */
@Component
@RequiredArgsConstructor
public class WorkOrderStandardsDistributor {

    private final VerificationStandardsServicePort verificationStandardsServicePort;

    /** Pone los puestos y quita los quitados. No guarda: lo hace quien llama, con los eventos del reporte. */
    public void apply(ServiceReport reporte, UUID idTipoEquipo, Set<UUID> puestos, Set<UUID> quitados) {
        // Lo puesto a mano no sale: lo decide withdrawFromWorkOrder, que solo quita lo que llegó por la orden.
        if (!quitados.isEmpty()) {
            reporte.patronesActivos().stream()
                    .filter(usado -> quitados.contains(usado.idEquipoPatron()))
                    .toList()
                    .forEach(usado -> reporte.withdrawFromWorkOrder(usado.idVerificacion(), usado.idEquipoPatron()));
        }

        if (puestos.isEmpty()) {
            return;
        }

        for (VerificationStandards verificacion : verificationStandardsServicePort.standardsOf(idTipoEquipo)) {
            puestos.stream()
                    .filter(verificacion::admite)
                    .sorted()
                    .forEach(patron -> reporte.receiveFromWorkOrder(verificacion.getIdVerificacion(), patron));
        }
    }
}
