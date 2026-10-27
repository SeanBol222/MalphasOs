package com.malphasos.malphasos.client.application.services.manager;

import com.malphasos.malphasos.client.application.ports.input.ManagerServicePort;
import com.malphasos.malphasos.client.application.ports.output.HeadquarterPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.ManagerPersistencePort;
import com.malphasos.malphasos.client.application.ports.output.ServiceAreaPersistencePort;
import com.malphasos.malphasos.client.application.services.manager.commands.AssignManagerCommand;
import com.malphasos.malphasos.client.application.services.manager.commands.DeactivateManagerCommand;
import com.malphasos.malphasos.client.application.services.manager.commands.RegisterManagerCommand;
import com.malphasos.malphasos.client.application.services.manager.commands.RelieveManagerCommand;
import com.malphasos.malphasos.client.domain.exception.HeadquarterNotFoundException;
import com.malphasos.malphasos.client.domain.exception.ManagerAssignmentNotFoundException;
import com.malphasos.malphasos.client.domain.exception.ManagerNotFoundException;
import com.malphasos.malphasos.client.domain.exception.ManagerOfAnotherClientException;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.client.domain.exception.ServiceAreaNotFoundException;
import com.malphasos.malphasos.client.domain.manager.Manager;
import com.malphasos.malphasos.client.domain.manager.ManagerAssignment;
import com.malphasos.malphasos.client.domain.manager.ManagerType;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationRequest;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.person.domain.person.PersonType;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta los casos de uso de encargados.
 *
 * <p>Un encargado <b>es</b> una persona: su identidad es la de ella. Por eso hay dos caminos de
 * alta. {@code register} crea la persona a través del contrato que publica el módulo de personas y
 * usa el identificador devuelto; {@code assign} parte de alguien que ya existe. El original solo
 * tenía el primero, de modo que un ingeniero de la empresa no podía figurar además como encargado
 * sin duplicarse como persona.
 *
 * <p>Comprueba en ambos casos que el destino exista y esté activo. Poner a alguien al frente de una
 * sede cerrada sería nombrar responsable de algo que ya no opera, y eso ninguna clave foránea puede
 * impedirlo: comprueba que la fila exista, no que esté activa.
 *
 * <p><b>Y desde el 2026-10-27 comprueba el cliente</b>: un encargado está a cargo de varias sedes y
 * áreas (`V41`), todas del mismo cliente, decidido por el usuario. El esquema no puede comprobarlo —la
 * sede y el área viven en tablas distintas— y el agregado no sabe de quién es una sede, así que la
 * regla vive aquí.
 */
@Service
@RequiredArgsConstructor
public class ManagerService implements ManagerServicePort {

    private final ManagerPersistencePort managerPersistencePort;
    private final HeadquarterPersistencePort headquarterPersistencePort;
    private final ServiceAreaPersistencePort serviceAreaPersistencePort;
    private final PersonCommunicationPort personCommunicationPort;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public List<Manager> findAll() {
        return managerPersistencePort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Manager findByPerson(UUID idPersona) {
        return managerPersistencePort.findByPerson(idPersona)
                .orElseThrow(() -> new ManagerNotFoundException(idPersona));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Manager> findByHeadquarter(UUID idSede) {
        requireActiveHeadquarter(idSede);

        return managerPersistencePort.findByHeadquarter(idSede);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Manager> findByServiceArea(UUID idArea) {
        requireActiveServiceArea(idArea);

        return managerPersistencePort.findByServiceArea(idArea);
    }

    @Override
    @Transactional
    public Manager register(RegisterManagerCommand command) {
        requireActiveTarget(command.tipo(), command.idAsignacion());

        // La persona se crea con su tipo ya fijado: el encargado de un cliente es un MANAGER, y no
        // recibe usuario en el proveedor de identidad porque no inicia sesion en el sistema.
        UUID idPersona = personCommunicationPort.save(conTipoManager(command.persona()));

        return persistAndPublish(nuevo(idPersona, command.tipo(), command.idAsignacion()));
    }

    @Override
    @Transactional
    public Manager assign(AssignManagerCommand command) {
        requireActiveTarget(command.tipo(), command.idAsignacion());
        // Lanza PersonNotFoundException si no existe, que el manejador traduce a 404.
        personCommunicationPort.findById(command.idPersona());

        Optional<Manager> existente = managerPersistencePort.findByPerson(command.idPersona());

        if (existente.isEmpty()) {
            return persistAndPublish(
                    nuevo(command.idPersona(), command.tipo(), command.idAsignacion()));
        }

        Manager encargado = existente.get();
        requireSameClient(encargado, command.tipo(), command.idAsignacion());
        encargado.assignTo(command.tipo(), command.idAsignacion());

        return persistAndPublish(encargado);
    }

    @Override
    @Transactional
    public Manager relieve(RelieveManagerCommand command) {
        Manager encargado = findByPerson(command.idPersona());

        if (!encargado.relieveFrom(command.tipo(), command.idAsignacion())) {
            throw new ManagerAssignmentNotFoundException(
                    command.idPersona(), command.tipo(), command.idAsignacion());
        }

        return persistAndPublish(encargado);
    }

    @Override
    @Transactional
    public void deactivate(DeactivateManagerCommand command) {
        Manager encargado = findByPerson(command.idPersona());
        encargado.deactivate();

        persistAndPublish(encargado);
    }

    private Manager nuevo(UUID idPersona, ManagerType tipo, UUID idAsignacion) {
        return tipo == ManagerType.HEADQUARTER
                ? Manager.forHeadquarter(idPersona, idAsignacion)
                : Manager.forServiceArea(idPersona, idAsignacion);
    }

    /** El tipo de persona lo fija este servicio, no quien llama: aquí se registra un encargado. */
    private PersonCommunicationRequest conTipoManager(PersonCommunicationRequest peticion) {
        if (peticion == null) {
            throw new IllegalArgumentException("Un encargado necesita los datos de la persona");
        }

        return PersonCommunicationRequest.builder()
                .cedula(peticion.cedula())
                .primerNombre(peticion.primerNombre())
                .segundoNombre(peticion.segundoNombre())
                .primerApellido(peticion.primerApellido())
                .segundoApellido(peticion.segundoApellido())
                .tipoPersona(PersonType.MANAGER)
                .segundoTipoPersona(peticion.segundoTipoPersona())
                .emailPersonList(peticion.emailPersonList())
                .phonePersonList(peticion.phonePersonList())
                .build();
    }

    /**
     * Todas las asignaciones activas de un encargado son del mismo cliente.
     *
     * <p>Solo cuentan las activas: de lo que se le retiró ya no responde, y no tiene por qué atarlo.
     * El destino ya se comprobó activo, así que su cliente existe.
     */
    private void requireSameClient(Manager encargado, ManagerType tipo, UUID idAsignacion) {
        UUID nuevo = clienteDe(tipo, idAsignacion);

        encargado.getAsignaciones().stream()
                .filter(ManagerAssignment::estadoActivo)
                .map(asignacion -> clienteDe(asignacion.tipo(), asignacion.idAsignacion()))
                .filter(actual -> !actual.equals(nuevo))
                .findFirst()
                .ifPresent(actual -> {
                    throw new ManagerOfAnotherClientException(encargado.getIdPersona(), actual, nuevo);
                });
    }

    /** El cliente de una sede, o el de la sede de un área. */
    private UUID clienteDe(ManagerType tipo, UUID idAsignacion) {
        UUID idSede = tipo == ManagerType.HEADQUARTER
                ? idAsignacion
                : serviceAreaPersistencePort.findById(idAsignacion)
                        .orElseThrow(() -> new ServiceAreaNotFoundException(idAsignacion))
                        .getIdSede();

        return headquarterPersistencePort.findById(idSede)
                .map(Headquarter::getIdCliente)
                .orElseThrow(() -> new HeadquarterNotFoundException(idSede));
    }

    private void requireActiveTarget(ManagerType tipo, UUID idAsignacion) {
        if (tipo == null) {
            throw new IllegalArgumentException("Un encargado necesita saber de que se encarga");
        }

        if (tipo == ManagerType.HEADQUARTER) {
            requireActiveHeadquarter(idAsignacion);
        } else {
            requireActiveServiceArea(idAsignacion);
        }
    }

    private void requireActiveHeadquarter(UUID idSede) {
        boolean activa = idSede != null
                && headquarterPersistencePort.findById(idSede)
                        .filter(sede -> sede.isEstadoActivo())
                        .isPresent();

        if (!activa) {
            throw new HeadquarterNotFoundException(idSede);
        }
    }

    private void requireActiveServiceArea(UUID idArea) {
        boolean activa = idArea != null
                && serviceAreaPersistencePort.findById(idArea)
                        .filter(area -> area.isEstadoActivo())
                        .isPresent();

        if (!activa) {
            throw new ServiceAreaNotFoundException(idArea);
        }
    }

    private Manager persistAndPublish(Manager encargado) {
        Manager guardado = managerPersistencePort.save(encargado);
        eventDispatcherPort.dispatchAll(encargado.pullEvents());

        return guardado;
    }
}
