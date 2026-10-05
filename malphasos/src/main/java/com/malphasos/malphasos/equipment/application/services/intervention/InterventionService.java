package com.malphasos.malphasos.equipment.application.services.intervention;

import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.InterventionRecordingPort;
import com.malphasos.malphasos.equipment.application.ports.input.InterventionServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.InterventionPersistencePort;
import com.malphasos.malphasos.equipment.application.services.intervention.commands.RecordInterventionCommand;
import com.malphasos.malphasos.equipment.domain.intervention.Intervention;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El historial de intervenciones: se escribe solo, y se lee por equipo.
 *
 * <p>Implementa los dos puertos de entrada porque son las dos caras de lo mismo y mantenerlos
 * separados obligaría a dos servicios sobre el mismo almacén: uno es lo que este módulo publica hacia
 * fuera —{@link InterventionRecordingPort}, por donde entra el oyente del cierre de un reporte— y el
 * otro lo que consume su propio adaptador REST.
 *
 * <p><b>No hay operación de escritura manual, y es el requisito.</b> El segundo criterio de RF-26 dice
 * que «no se requiere acción manual para actualizar el historial», de modo que no existe un
 * {@code POST /interventions}: la única forma de que aparezca una línea es que un reporte se cierre.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterventionService implements InterventionServicePort, InterventionRecordingPort {

    private final InterventionPersistencePort interventionPersistencePort;
    private final ClientEquipmentServicePort clientEquipmentServicePort;

    @Override
    @Transactional(readOnly = true)
    public List<Intervention> findByEquipment(UUID idEquipoCliente, ReadScope alcance) {
        // Toda la comprobación de pertenencia es pasarle el alcance a quien es dueño del equipo: si
        // es de otro cliente, responde «no existe» y el historial no se llega a consultar.
        clientEquipmentServicePort.findById(idEquipoCliente, alcance);

        return interventionPersistencePort.findByEquipment(idEquipoCliente);
    }

    @Override
    @Transactional
    public void record(RecordInterventionCommand command) {
        if (interventionPersistencePort.existsByReport(command.idReporteServicio())) {
            // Un oyente puede recibir el mismo hecho dos veces. Salir en silencio es lo correcto
            // aquí: el historial ya dice lo que tiene que decir, y no hay nada que corregir.
            log.debug(
                    "El reporte {} ya tiene su linea en el historial; no se anota otra.",
                    command.idReporteServicio());

            return;
        }

        Intervention nueva = interventionPersistencePort.save(Intervention.record(
                command.idEquipoCliente(),
                command.idReporteServicio(),
                command.fechaServicio(),
                command.tipoServicio(),
                command.resultado()));

        reemplazarLasCorregidas(command, nueva);
    }

    /**
     * Las líneas de los reportes que este cierre corrige dejan de contar, apuntando a la nueva.
     *
     * <p>Decisión del usuario del 2026-10-04: corregir un reporte es retirarlo y abrir otro, y sin
     * esto el mismo mantenimiento quedaba dos veces en la hoja de vida. La línea vieja no se borra —el
     * rastro de que hubo una corrección se conserva— y solo se reemplazan las que siguen vigentes: en
     * una cadena de correcciones cada línea apunta a la que la sustituyó primero, y la historia de la
     * cadena no se reescribe.
     *
     * <p>Ocurre al <b>cerrarse</b> el sustituto y no al abrirlo: si el sustituto no llegara a cerrarse,
     * reemplazar al abrir dejaría la hoja de vida sin un mantenimiento que sí ocurrió.
     */
    private void reemplazarLasCorregidas(RecordInterventionCommand command, Intervention nueva) {
        if (command.reportesSustituidos().isEmpty()) {
            return;
        }

        interventionPersistencePort.findByReports(command.reportesSustituidos()).stream()
                .filter(Intervention::estadoActivo)
                .map(vieja -> vieja.reemplazadaPor(nueva.id()))
                .forEach(interventionPersistencePort::save);
    }
}
