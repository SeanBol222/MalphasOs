package com.malphasos.malphasos.client.application.services.manager;

import com.malphasos.malphasos.client.application.ports.input.ServiceAreaResponsiblePort;
import com.malphasos.malphasos.client.application.ports.output.ManagerPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.ServiceAreaPersistencePort;
import com.malphasos.malphasos.client.domain.manager.Manager;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationResponse;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve quién responde por un área: sus encargados, y si no tiene, los de su sede.
 *
 * <p>No comprueba el alcance de quien pregunta, y es a propósito: lo llama la hoja de vida <b>después</b>
 * de haber comprobado que quien la pide tiene derecho al equipo, y a esa altura el área ya es suya.
 * Es la misma decisión que toma {@code LifeSheetService} con el resto de saltos.
 */
@Service
@RequiredArgsConstructor
public class ServiceAreaResponsibleService implements ServiceAreaResponsiblePort {

    private final ManagerPersistencePort managerPersistencePort;
    private final ServiceAreaPersistencePort serviceAreaPersistencePort;
    private final PersonCommunicationPort personCommunicationPort;

    @Override
    @Transactional(readOnly = true)
    public List<String> responsiblesFor(UUID idAreaServicio) {
        List<String> delArea = nombresDe(managerPersistencePort.findByServiceArea(idAreaServicio));
        if (!delArea.isEmpty()) {
            return delArea;
        }
        return serviceAreaPersistencePort.findById(idAreaServicio)
                .map(area -> nombresDe(managerPersistencePort.findByHeadquarter(area.getIdSede())))
                .orElse(List.of());
    }

    /**
     * Los nombres de los encargados vigentes. El repositorio devuelve también los retirados —es una
     * consulta por asignación, no por estado—, y una persona dada de baja tampoco responde por nada.
     */
    private List<String> nombresDe(List<Manager> encargados) {
        return encargados.stream()
                .filter(Manager::isEstadoActivo)
                .map(encargado -> personCommunicationPort.findById(encargado.getIdPersona()))
                .filter(PersonCommunicationResponse::estadoActivo)
                .map(ServiceAreaResponsibleService::nombreCompleto)
                .sorted()
                .toList();
    }

    private static String nombreCompleto(PersonCommunicationResponse persona) {
        return Stream.of(
                        persona.primerNombre(),
                        persona.segundoNombre(),
                        persona.primerApellido(),
                        persona.segundoApellido())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(parte -> !parte.isEmpty())
                .collect(Collectors.joining(" "));
    }
}
