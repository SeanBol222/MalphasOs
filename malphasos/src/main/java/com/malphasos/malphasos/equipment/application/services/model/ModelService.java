package com.malphasos.malphasos.equipment.application.services.model;

import com.malphasos.malphasos.equipment.application.ports.input.EquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ManufacturerServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.ModelServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.ModelPersistencePort;
import com.malphasos.malphasos.equipment.application.services.model.commands.ChangeModelInvimaCommand;
import com.malphasos.malphasos.equipment.application.services.model.commands.CreateModelCommand;
import com.malphasos.malphasos.equipment.application.services.model.commands.DeactivateModelCommand;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.domain.exception.ModelNotFoundException;
import com.malphasos.malphasos.equipment.domain.model.Model;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta los casos de uso de modelos.
 *
 * <p>No se da de alta un modelo sobre una asociación retirada: significaría registrar un producto de
 * una combinación de marca y tipo que ya no se fabrica. La clave foránea no puede impedirlo, porque
 * comprueba que la fila exista y no que esté activa.
 */
@Service
@RequiredArgsConstructor
public class ModelService implements ModelServicePort {

    private final ModelPersistencePort modelPersistencePort;
    private final ManufacturerServicePort manufacturerServicePort;
    private final EquipmentServicePort equipmentServicePort;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public List<Model> findAll() {
        return modelPersistencePort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Model findById(UUID id) {
        return modelPersistencePort.findById(id).orElseThrow(() -> new ModelNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Model> findByEquipment(UUID idEquipo) {
        equipmentServicePort.findById(idEquipo);

        return modelPersistencePort.findByEquipment(idEquipo);
    }

    @Override
    @Transactional
    public Model create(CreateModelCommand command) {
        manufacturerServicePort.findById(command.idFabricante());

        Equipment equipo = equipmentServicePort.findById(command.idEquipo());
        if (!equipo.isEstadoActivo()) {
            throw new IllegalArgumentException(
                    "No se puede registrar un modelo sobre una asociacion retirada: " + equipo.getId());
        }

        return persistAndPublish(
                Model.create(command.invima(), command.idFabricante(), command.idEquipo()));
    }

    @Override
    @Transactional
    public Model changeInvima(ChangeModelInvimaCommand command) {
        Model modelo = findById(command.id());
        modelo.changeInvima(command.invima());

        return persistAndPublish(modelo);
    }

    @Override
    @Transactional
    public void deactivate(DeactivateModelCommand command) {
        Model modelo = findById(command.id());
        modelo.deactivate();

        persistAndPublish(modelo);
    }

    private Model persistAndPublish(Model modelo) {
        Model guardado = modelPersistencePort.save(modelo);
        eventDispatcherPort.dispatchAll(modelo.pullEvents());

        return guardado;
    }
}
