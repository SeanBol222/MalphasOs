package com.malphasos.malphasos.equipment.application.services.protocol;

import com.malphasos.malphasos.equipment.application.model.AssignedProtocolDetail;
import com.malphasos.malphasos.equipment.application.ports.input.ProtocolServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.EquipmentTypePersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ProtocolAssignmentPersistencePort;
import com.malphasos.malphasos.equipment.application.ports.output.ProtocolPersistencePort;
import com.malphasos.malphasos.equipment.application.services.protocol.commands.AssignProtocolsCommand;
import com.malphasos.malphasos.equipment.application.services.protocol.commands.CreateProtocolCommand;
import com.malphasos.malphasos.equipment.application.services.protocol.commands.DeactivateProtocolCommand;
import com.malphasos.malphasos.equipment.application.services.protocol.commands.UpdateProtocolCommand;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.exception.EquipmentTypeNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.ProtocolInUseException;
import com.malphasos.malphasos.equipment.domain.exception.ProtocolNameTakenException;
import com.malphasos.malphasos.equipment.domain.exception.ProtocolNotFoundException;
import com.malphasos.malphasos.equipment.domain.protocol.AssignedProtocol;
import com.malphasos.malphasos.equipment.domain.protocol.Protocol;
import com.malphasos.malphasos.equipment.domain.protocol.ProtocolAssignment;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta el catálogo de protocolos y su asignación a los tipos de equipo.
 *
 * <p>Tres reglas viven aquí y no en los agregados, porque exigen preguntar fuera de ellos: que el nombre
 * no se repita entre los vigentes, que no se retire un protocolo que algún tipo usa, y que solo se
 * asignen protocolos que existen y están vigentes a un tipo que existe y está vigente.
 */
@Service
@RequiredArgsConstructor
public class ProtocolService implements ProtocolServicePort {

    private final ProtocolPersistencePort protocolPersistencePort;
    private final ProtocolAssignmentPersistencePort protocolAssignmentPersistencePort;
    private final EquipmentTypePersistencePort equipmentTypePersistencePort;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public List<Protocol> findAll() {
        return protocolPersistencePort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Protocol findById(UUID id) {
        return protocolPersistencePort.findById(id).orElseThrow(() -> new ProtocolNotFoundException(id));
    }

    @Override
    @Transactional
    public Protocol create(CreateProtocolCommand command) {
        Protocol protocolo = Protocol.create(command.nombre(), command.descripcion());
        exigirNombreLibre(protocolo);

        return persistAndPublish(protocolo);
    }

    @Override
    @Transactional
    public Protocol update(UpdateProtocolCommand command) {
        Protocol protocolo = findById(command.id());
        protocolo.update(command.nombre(), command.descripcion());
        exigirNombreLibre(protocolo);

        return persistAndPublish(protocolo);
    }

    @Override
    @Transactional
    public void deactivate(DeactivateProtocolCommand command) {
        Protocol protocolo = findById(command.id());
        long tipos = protocolPersistencePort.countActiveAssignments(protocolo.getId());

        if (protocolo.isEstadoActivo() && tipos > 0) {
            throw new ProtocolInUseException(protocolo.getNombre(), tipos);
        }

        protocolo.deactivate();
        persistAndPublish(protocolo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignedProtocolDetail> protocolsOf(UUID idTipoEquipo) {
        exigirTipo(idTipoEquipo);

        return detallar(protocolAssignmentPersistencePort.findByType(idTipoEquipo));
    }

    @Override
    @Transactional
    public List<AssignedProtocolDetail> assign(AssignProtocolsCommand command) {
        EquipmentType tipo = exigirTipo(command.idTipoEquipo());

        if (!tipo.isEstadoActivo()) {
            throw new IllegalArgumentException(
                    "El tipo de equipo «" + tipo.getNombre() + "» está retirado: no se le asignan protocolos");
        }

        exigirProtocolosVigentes(command.protocolos());

        ProtocolAssignment asignacion = protocolAssignmentPersistencePort.findByType(tipo.getId());
        asignacion.assign(command.protocolos());

        ProtocolAssignment guardada = protocolAssignmentPersistencePort.save(asignacion);
        eventDispatcherPort.dispatchAll(asignacion.pullEvents());

        return detallar(guardada);
    }

    private EquipmentType exigirTipo(UUID idTipoEquipo) {
        return equipmentTypePersistencePort.findById(idTipoEquipo)
                .orElseThrow(() -> new EquipmentTypeNotFoundException(idTipoEquipo));
    }

    /**
     * Cada protocolo pedido existe —si no, 404 con su identificador— y está vigente. Uno retirado no se
     * puede asignar de nuevo, pero tampoco desaparece de un tipo que ya lo tenía: retirarlo exige antes
     * quitarlo de todos.
     */
    private void exigirProtocolosVigentes(List<ProtocolAssignment.Requested> pedidos) {
        if (pedidos == null) {
            return;
        }

        List<UUID> ids = pedidos.stream()
                .filter(pedido -> pedido != null && pedido.idProtocolo() != null)
                .map(ProtocolAssignment.Requested::idProtocolo)
                .toList();
        Map<UUID, Protocol> encontrados = porId(protocolPersistencePort.findAllById(ids));

        for (UUID id : ids) {
            Protocol protocolo = encontrados.get(id);

            if (protocolo == null) {
                throw new ProtocolNotFoundException(id);
            }
            if (!protocolo.isEstadoActivo()) {
                throw new IllegalArgumentException(
                        "El protocolo «" + protocolo.getNombre() + "» está retirado: no se puede asignar");
            }
        }
    }

    /**
     * Comprueba que ningún otro protocolo vigente tenga el mismo nombre sin contar mayúsculas ni tildes.
     * Se pregunta antes de guardar para responder con el nombre del que choca; el índice único de la base
     * es lo que lo garantiza con dos altas a la vez.
     */
    private void exigirNombreLibre(Protocol protocolo) {
        protocolPersistencePort.findActiveByClave(protocolo.getClave())
                .filter(otro -> !otro.getId().equals(protocolo.getId()))
                .ifPresent(otro -> {
                    throw new ProtocolNameTakenException(protocolo.getNombre(), otro.getNombre());
                });
    }

    private List<AssignedProtocolDetail> detallar(ProtocolAssignment asignacion) {
        List<AssignedProtocol> asignados = asignacion.getProtocolos();
        Map<UUID, Protocol> protocolos = porId(protocolPersistencePort.findAllById(
                asignados.stream().map(AssignedProtocol::idProtocolo).toList()));

        return asignados.stream()
                .map(asignado -> new AssignedProtocolDetail(
                        protocolos.get(asignado.idProtocolo()), asignado.prioridad(), asignado.indicacion()))
                .toList();
    }

    private static Map<UUID, Protocol> porId(List<Protocol> protocolos) {
        return protocolos.stream().collect(Collectors.toMap(Protocol::getId, Function.identity()));
    }

    private Protocol persistAndPublish(Protocol protocolo) {
        Protocol guardado = protocolPersistencePort.save(protocolo);
        eventDispatcherPort.dispatchAll(protocolo.pullEvents());

        return guardado;
    }
}
