package com.malphasos.malphasos.equipment.application.services.clientEquipment;

import com.malphasos.malphasos.client.application.ports.input.ServiceAreaServicePort;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ModelServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.ClientEquipmentPersistencePort;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.DecommissionClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.RegisterClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.RelocateClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.UpdateClientEquipmentCommand;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta el inventario de equipos de los clientes.
 *
 * <p>Es el punto donde el catálogo se encuentra con la organización del cliente, y el primer sitio
 * donde este módulo <b>consulta a {@code client}</b>: hasta ahora era `client` quien consultaba a
 * los demás. No hay ciclo, porque `client` no conoce a `equipment`.
 *
 * <p>Dos reglas que ninguna clave foránea puede imponer, porque comprueban que la fila exista y no
 * que esté activa: no se incorpora una unidad de un modelo retirado, ni se instala o traslada a un
 * área de servicio cerrada.
 */
@Service
@RequiredArgsConstructor
public class ClientEquipmentService implements ClientEquipmentServicePort {

    private final ClientEquipmentPersistencePort clientEquipmentPersistencePort;
    private final ModelServicePort modelServicePort;
    private final ServiceAreaServicePort serviceAreaServicePort;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public List<ClientEquipment> findAll() {
        return clientEquipmentPersistencePort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public ClientEquipment findById(UUID id) {
        return clientEquipmentPersistencePort.findById(id)
                .orElseThrow(() -> new ClientEquipmentNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientEquipment> findByServiceArea(UUID idAreaServicio) {
        serviceAreaServicePort.findById(idAreaServicio);

        return clientEquipmentPersistencePort.findByServiceArea(idAreaServicio);
    }

    @Override
    @Transactional
    public ClientEquipment register(RegisterClientEquipmentCommand command) {
        Model modelo = modelServicePort.findById(command.idModelo());
        if (!modelo.isEstadoActivo()) {
            throw new IllegalArgumentException(
                    "No se puede incorporar una unidad de un modelo retirado: " + modelo.getId());
        }

        requireActiveServiceArea(command.idAreaServicio());

        return persistAndPublish(ClientEquipment.register(
                command.serie(),
                command.idModelo(),
                command.idAreaServicio(),
                command.numeroInventario(),
                command.fechaCompra(),
                command.valorCompra()));
    }

    @Override
    @Transactional
    public ClientEquipment relocate(RelocateClientEquipmentCommand command) {
        requireActiveServiceArea(command.idAreaServicio());

        ClientEquipment unidad = findById(command.id());
        unidad.relocateTo(command.idAreaServicio());

        return persistAndPublish(unidad);
    }

    @Override
    @Transactional
    public ClientEquipment update(UpdateClientEquipmentCommand command) {
        ClientEquipment unidad = findById(command.id());
        unidad.update(command.numeroInventario(), command.fechaCompra(), command.valorCompra());

        return persistAndPublish(unidad);
    }

    @Override
    @Transactional
    public void decommission(DecommissionClientEquipmentCommand command) {
        ClientEquipment unidad = findById(command.id());
        unidad.decommission();

        persistAndPublish(unidad);
    }

    /** Un equipo no se instala donde ya no se opera. */
    private void requireActiveServiceArea(UUID idAreaServicio) {
        ServiceArea area = serviceAreaServicePort.findById(idAreaServicio);

        if (!area.isEstadoActivo()) {
            throw new IllegalArgumentException(
                    "No se puede situar un equipo en un area de servicio cerrada: " + idAreaServicio);
        }
    }

    private ClientEquipment persistAndPublish(ClientEquipment unidad) {
        ClientEquipment guardada = clientEquipmentPersistencePort.save(unidad);
        eventDispatcherPort.dispatchAll(unidad.pullEvents());

        return guardada;
    }
}
