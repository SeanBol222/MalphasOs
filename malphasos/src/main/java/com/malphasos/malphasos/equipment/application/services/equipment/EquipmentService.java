package com.malphasos.malphasos.equipment.application.services.equipment;

import com.malphasos.malphasos.equipment.application.ports.input.BrandServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentTypeServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.EquipmentPersistencePort;
import com.malphasos.malphasos.equipment.application.services.equipment.commands.CreateEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.equipment.commands.DeactivateEquipmentCommand;
import com.malphasos.malphasos.equipment.domain.equipment.Equipment;
import com.malphasos.malphasos.equipment.domain.exception.EquipmentNotFoundException;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta la asociación entre marcas y tipos de equipo.
 *
 * <p>Comprueba que ambos existan antes de asociarlos. La unicidad del par la garantiza el esquema:
 * comprobarla aquí solo abriría una ventana entre la consulta y la escritura, y la violación se
 * traduce a 409 en el manejador transversal.
 */
@Service
@RequiredArgsConstructor
public class EquipmentService implements EquipmentServicePort {

    private final EquipmentPersistencePort equipmentPersistencePort;
    private final EquipmentTypeServicePort equipmentTypeServicePort;
    private final BrandServicePort brandServicePort;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public List<Equipment> findAll() {
        return equipmentPersistencePort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Equipment findById(UUID id) {
        return equipmentPersistencePort.findById(id)
                .orElseThrow(() -> new EquipmentNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Equipment> findByBrand(UUID idMarca) {
        brandServicePort.findById(idMarca);

        return equipmentPersistencePort.findByBrand(idMarca);
    }

    @Override
    @Transactional
    public Equipment create(CreateEquipmentCommand command) {
        // Lanzan sus propias excepciones si no existen, que el manejador traduce a 404.
        equipmentTypeServicePort.findById(command.idTipoEquipo());
        brandServicePort.findById(command.idMarca());

        return persistAndPublish(Equipment.create(command.idTipoEquipo(), command.idMarca()));
    }

    @Override
    @Transactional
    public void deactivate(DeactivateEquipmentCommand command) {
        Equipment equipo = findById(command.id());
        equipo.deactivate();

        persistAndPublish(equipo);
    }

    private Equipment persistAndPublish(Equipment equipo) {
        Equipment guardado = equipmentPersistencePort.save(equipo);
        eventDispatcherPort.dispatchAll(equipo.pullEvents());

        return guardado;
    }
}
