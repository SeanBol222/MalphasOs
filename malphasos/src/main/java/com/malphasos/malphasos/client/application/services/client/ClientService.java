package com.malphasos.malphasos.client.application.services.client;

import com.malphasos.malphasos.client.application.ports.input.ClientServicePort;
import com.malphasos.malphasos.client.application.ports.output.ClientPersistencePort;
import com.malphasos.malphasos.client.application.services.client.commands.AddClientEmailCommand;
import com.malphasos.malphasos.client.application.services.client.commands.AddClientPhoneCommand;
import com.malphasos.malphasos.client.application.services.client.commands.AppointRepresentativeCommand;
import com.malphasos.malphasos.client.application.services.client.commands.CreateClientCommand;
import com.malphasos.malphasos.client.application.services.client.commands.DeactivateClientCommand;
import com.malphasos.malphasos.client.application.services.client.commands.RemoveClientEmailCommand;
import com.malphasos.malphasos.client.application.services.client.commands.RemoveClientPhoneCommand;
import com.malphasos.malphasos.client.application.services.client.commands.RemoveRepresentativeCommand;
import com.malphasos.malphasos.client.application.services.client.commands.UpdateClientCommand;
import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.client.domain.exception.ClientNotFoundException;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta los casos de uso de clientes: recupera el agregado, le pide el cambio, lo persiste y
 * publica lo que registró. Los eventos salen después de guardar, de modo que nunca se anuncia un
 * cambio que la base terminó rechazando.
 *
 * <p>Para nombrar un representante legal consulta el módulo de personas a través de
 * {@link PersonCommunicationPort}, el contrato que ese módulo publica hacia los demás. Es la primera
 * vez que un módulo de MalphasOS habla con otro. Podría dejarse en manos de la clave foránea, pero
 * entonces el llamante recibiría un conflicto de integridad genérico en lugar de "esa persona no
 * existe".
 */
@Service
@RequiredArgsConstructor
public class ClientService implements ClientServicePort {

    private final ClientPersistencePort clientPersistencePort;
    private final PersonCommunicationPort personCommunicationPort;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public List<Client> findAll(ReadScope alcance) {
        if (alcance.alcanzaATodo()) {
            return clientPersistencePort.findAll();
        }

        return clientPersistencePort.findAllByIds(alcance.clientesVisibles());
    }

    /**
     * Un cliente fuera del alcance se responde como inexistente, y se responde <b>sin consultar la
     * base</b>: el alcance se comprueba antes, de modo que ni siquiera se averigua si esa fila está.
     */
    @Override
    @Transactional(readOnly = true)
    public Client findById(UUID id, ReadScope alcance) {
        if (!alcance.alcanza(id)) {
            throw new ClientNotFoundException(id);
        }

        return clientPersistencePort.findById(id).orElseThrow(() -> new ClientNotFoundException(id));
    }

    @Override
    @Transactional
    public Client create(CreateClientCommand command) {
        return persistAndPublish(Client.create(
                command.documento(),
                command.tipoIdentificacion(),
                command.razonSocial(),
                command.idPais()));
    }

    @Override
    @Transactional
    public Client update(UpdateClientCommand command) {
        return applyTo(command.id(), cliente -> cliente.update(command.razonSocial(), command.idPais()));
    }

    @Override
    @Transactional
    public void deactivate(DeactivateClientCommand command) {
        applyTo(command.id(), Client::deactivate);
    }

    @Override
    @Transactional
    public Client addEmail(AddClientEmailCommand command) {
        return applyTo(command.idCliente(), cliente -> cliente.addEmail(command.correo()));
    }

    @Override
    @Transactional
    public Client removeEmail(RemoveClientEmailCommand command) {
        return applyTo(command.idCliente(), cliente -> cliente.removeEmail(command.idCorreo()));
    }

    @Override
    @Transactional
    public Client addPhone(AddClientPhoneCommand command) {
        return applyTo(command.idCliente(), cliente -> cliente.addPhone(command.telefono()));
    }

    @Override
    @Transactional
    public Client removePhone(RemoveClientPhoneCommand command) {
        return applyTo(command.idCliente(), cliente -> cliente.removePhone(command.idTelefono()));
    }

    @Override
    @Transactional
    public Client appointRepresentative(AppointRepresentativeCommand command) {
        // Falla con "no existe esa persona" y no con un conflicto de integridad del motor.
        personCommunicationPort.findById(command.idPersona());

        return applyTo(command.idCliente(), cliente -> cliente.appointRepresentative(command.idPersona()));
    }

    @Override
    @Transactional
    public Client removeRepresentative(RemoveRepresentativeCommand command) {
        return applyTo(
                command.idCliente(), cliente -> cliente.removeRepresentative(command.idPersona()));
    }

    /**
     * Recupera el cliente, le aplica el cambio, lo guarda y publica lo que haya registrado.
     *
     * <p>Sin restricción de alcance, y aquí vale para todas las escrituras de este módulo: el
     * filtrado por dueño acota lo que se <b>lee</b>, y ningún grupo del realm que llegue aquí
     * tiene autoridad de escritura sobre un cliente. Quien no puede escribir no llega.
     */
    private Client applyTo(UUID id, Consumer<Client> cambio) {
        Client cliente = findById(id, ReadScope.sinRestriccion());
        cambio.accept(cliente);

        return persistAndPublish(cliente);
    }

    /**
     * Se publica lo del agregado recibido y no lo del devuelto por el almacén: recoger vacía la
     * lista, y el adaptador reconstruye una instancia distinta que ya no la lleva.
     */
    private Client persistAndPublish(Client cliente) {
        Client guardado = clientPersistencePort.save(cliente);
        eventDispatcherPort.dispatchAll(cliente.pullEvents());

        return guardado;
    }
}
