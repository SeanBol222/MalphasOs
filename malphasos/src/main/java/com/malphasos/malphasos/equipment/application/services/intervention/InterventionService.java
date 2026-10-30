package com.malphasos.malphasos.equipment.application.services.intervention;

import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.InterventionServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.ServiceHistoryPort;
import com.malphasos.malphasos.equipment.domain.intervention.Intervention;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El historial de intervenciones de un equipo, que solo se lee.
 *
 * <p><b>No hay operación de escritura, y es el requisito.</b> El segundo criterio de RF-26 dice que «no se
 * requiere acción manual para actualizar el historial»: la única forma de que aparezca una línea es que un
 * reporte se cierre. Hasta {@code V52} un oyente del cierre la anotaba en la tabla {@code intervencion};
 * desde entonces se deriva del propio reporte, por {@link ServiceHistoryPort}, y no queda nada que anotar.
 */
@Service
@RequiredArgsConstructor
public class InterventionService implements InterventionServicePort {

    private final ServiceHistoryPort serviceHistoryPort;
    private final ClientEquipmentServicePort clientEquipmentServicePort;

    @Override
    @Transactional(readOnly = true)
    public List<Intervention> findByEquipment(UUID idEquipoCliente, ReadScope alcance) {
        // Toda la comprobación de pertenencia es pasarle el alcance a quien es dueño del equipo: si
        // es de otro cliente, responde «no existe» y el historial no se llega a consultar.
        clientEquipmentServicePort.findById(idEquipoCliente, alcance);

        return serviceHistoryPort.historyOf(idEquipoCliente);
    }
}
