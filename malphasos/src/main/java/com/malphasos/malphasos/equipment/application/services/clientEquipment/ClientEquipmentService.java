package com.malphasos.malphasos.equipment.application.services.clientEquipment;

import com.malphasos.malphasos.client.application.ports.input.ServiceAreaServicePort;
import com.malphasos.malphasos.client.domain.serviceArea.ServiceArea;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ModelServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.ClientEquipmentPersistencePort;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.DecommissionClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.RegisterClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.RelocateClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.UpdateClientEquipmentCommand;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException;
import com.malphasos.malphasos.equipment.domain.exception.CrossClientRelocationException;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.Objects;
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
 *
 * <p>Y una tercera que ningún {@code CHECK} podría expresar sin cruzar tres tablas: una unidad solo
 * se traslada a áreas de su propio cliente. En el alta no hay nada equivalente que comprobar,
 * porque es el área elegida la que define de qué cliente pasa a ser la unidad.
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
        // TODO(filtrado-por-dueno): este listado todavia no acota, y es una de las lecturas que si
        // filtran datos ajenos. Lo cierra la tanda de equipment; aqui solo se comprueba existencia.
        serviceAreaServicePort.findById(idAreaServicio, ReadScope.sinRestriccion());

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
        requireSameClient(unidad, command.idAreaServicio());
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

    /**
     * Una unidad solo se mueve entre áreas del cliente que la posee.
     *
     * <p>Puede cambiar de área dentro de una sede y también de sede, mientras el cliente sea el
     * mismo. Cruzar a otro cliente dejaría el historial de mantenimiento de la unidad colgando de
     * quien nunca la tuvo, y el traslado es justo el hecho que decide quién responde por ella.
     *
     * <p>La regla vive aquí y no en el esquema porque expresarla en SQL exigiría un {@code CHECK}
     * que cruza tres tablas —unidad, área y sede— para comparar dos clientes que ninguna de ellas
     * guarda junta. Es el mismo caso que "no abrir un área en una sede cerrada", que también vive
     * en un servicio.
     *
     * <p>El cliente de cada área lo responde {@code client} en una sola llamada: este módulo no
     * camina la jerarquía área → sede → cliente, que es interna de aquel contexto.
     */
    private void requireSameClient(ClientEquipment unidad, UUID idAreaDestino) {
        UUID clienteDestino = serviceAreaServicePort.findOwningClient(idAreaDestino);
        UUID clienteActual = serviceAreaServicePort.findOwningClient(unidad.getIdAreaServicio());

        if (!Objects.equals(clienteActual, clienteDestino)) {
            throw new CrossClientRelocationException(
                    unidad.getId(), idAreaDestino, clienteActual, clienteDestino);
        }
    }

    /**
     * Un equipo no se instala donde ya no se opera.
     *
     * <p>Sin restricción de alcance: instalar un equipo exige {@code equipment.write}, que solo
     * tienen los ingenieros y los administradores.
     */
    private void requireActiveServiceArea(UUID idAreaServicio) {
        ServiceArea area = serviceAreaServicePort.findById(idAreaServicio, ReadScope.sinRestriccion());

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
