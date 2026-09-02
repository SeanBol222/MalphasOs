package com.malphasos.malphasos.client.application.services.headquarter;

import com.malphasos.malphasos.client.application.ports.input.HeadquarterServicePort;
import com.malphasos.malphasos.client.application.ports.output.ClientPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.HeadquarterPersistencePort;
import com.malphasos.malphasos.client.application.services.headquarter.commands.CreateHeadquarterCommand;
import com.malphasos.malphasos.client.application.services.headquarter.commands.DeactivateHeadquarterCommand;
import com.malphasos.malphasos.client.application.services.headquarter.commands.UpdateHeadquarterCommand;
import com.malphasos.malphasos.client.domain.exception.ClientNotFoundException;
import com.malphasos.malphasos.client.domain.exception.HeadquarterNotFoundException;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.location.application.ports.input.CityServicePort;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta los casos de uso de sedes.
 *
 * <p>Comprueba que el cliente y la ciudad existan antes de abrir una sede. Lo primero es una
 * consulta dentro del mismo contexto acotado; lo segundo cruza hacia el módulo de ubicaciones, a
 * través de su puerto de entrada. En ambos casos el motivo es el mismo que en el resto del
 * proyecto: dejarlo a las claves foráneas devolvería un conflicto de integridad genérico en vez de
 * decir cuál de las dos referencias falta.
 */
@Service
@RequiredArgsConstructor
public class HeadquarterService implements HeadquarterServicePort {

    private final HeadquarterPersistencePort headquarterPersistencePort;
    private final ClientPersistencePort clientPersistencePort;
    private final CityServicePort cityServicePort;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public List<Headquarter> findAll() {
        return headquarterPersistencePort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Headquarter> findByClient(UUID idCliente) {
        requireClient(idCliente);

        return headquarterPersistencePort.findByClient(idCliente);
    }

    @Override
    @Transactional(readOnly = true)
    public Headquarter findById(UUID id) {
        return headquarterPersistencePort.findById(id)
                .orElseThrow(() -> new HeadquarterNotFoundException(id));
    }

    @Override
    @Transactional
    public Headquarter create(CreateHeadquarterCommand command) {
        requireClient(command.idCliente());
        // Lanza CityNotFoundException si no existe, que el manejador traduce a 404.
        cityServicePort.findById(command.idCiudad());

        return persistAndPublish(Headquarter.create(
                command.nombre(), command.direccion(), command.idCliente(), command.idCiudad()));
    }

    @Override
    @Transactional
    public Headquarter update(UpdateHeadquarterCommand command) {
        Headquarter sede = findById(command.id());

        if (command.idCiudad() != null) {
            cityServicePort.findById(command.idCiudad());
        }
        sede.update(command.nombre(), command.direccion(), command.idCiudad());

        return persistAndPublish(sede);
    }

    @Override
    @Transactional
    public void deactivate(DeactivateHeadquarterCommand command) {
        Headquarter sede = findById(command.id());
        sede.deactivate();

        persistAndPublish(sede);
    }

    private void requireClient(UUID idCliente) {
        if (idCliente == null || clientPersistencePort.findById(idCliente).isEmpty()) {
            throw new ClientNotFoundException(idCliente);
        }
    }

    private Headquarter persistAndPublish(Headquarter sede) {
        Headquarter guardada = headquarterPersistencePort.save(sede);
        eventDispatcherPort.dispatchAll(sede.pullEvents());

        return guardada;
    }
}
