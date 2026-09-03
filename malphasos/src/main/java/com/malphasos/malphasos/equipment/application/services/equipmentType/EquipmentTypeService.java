package com.malphasos.malphasos.equipment.application.services.equipmentType;

import com.malphasos.malphasos.equipment.application.ports.input.EquipmentTypeServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.EquipmentTypePersistencePort;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.ChangeVerificationModeCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.CreateEquipmentTypeCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.DeactivateEquipmentTypeCommand;
import com.malphasos.malphasos.equipment.application.services.equipmentType.commands.UpdateEquipmentTypeCommand;
import com.malphasos.malphasos.equipment.domain.equipmentType.EquipmentType;
import com.malphasos.malphasos.equipment.domain.exception.EquipmentTypeNotFoundException;
import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orquesta los casos de uso de tipos de equipo. */
@Service
@RequiredArgsConstructor
public class EquipmentTypeService implements EquipmentTypeServicePort {

    private final EquipmentTypePersistencePort equipmentTypePersistencePort;
    private final EventDispatcherPort eventDispatcherPort;

    @Override
    @Transactional(readOnly = true)
    public List<EquipmentType> findAll() {
        return equipmentTypePersistencePort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public EquipmentType findById(UUID id) {
        return equipmentTypePersistencePort.findById(id)
                .orElseThrow(() -> new EquipmentTypeNotFoundException(id));
    }

    @Override
    @Transactional
    public EquipmentType create(CreateEquipmentTypeCommand command) {
        return persistAndPublish(EquipmentType.create(
                command.nombre(),
                command.definicionTecnica(),
                command.recomendacionesCuidado(),
                command.tecnologiaPredominante(),
                command.voltaje(),
                command.amperaje(),
                command.modalidadVerificacion(),
                command.valorUnitarioMantenimiento()));
    }

    @Override
    @Transactional
    public EquipmentType update(UpdateEquipmentTypeCommand command) {
        EquipmentType tipo = findById(command.id());
        tipo.update(
                command.nombre(),
                command.definicionTecnica(),
                command.recomendacionesCuidado(),
                command.tecnologiaPredominante(),
                command.voltaje(),
                command.amperaje(),
                command.valorUnitarioMantenimiento());

        return persistAndPublish(tipo);
    }

    @Override
    @Transactional
    public EquipmentType changeVerificationMode(ChangeVerificationModeCommand command) {
        EquipmentType tipo = findById(command.id());
        tipo.changeVerificationMode(command.modalidad());

        return persistAndPublish(tipo);
    }

    @Override
    @Transactional
    public void deactivate(DeactivateEquipmentTypeCommand command) {
        EquipmentType tipo = findById(command.id());
        tipo.deactivate();

        persistAndPublish(tipo);
    }

    private EquipmentType persistAndPublish(EquipmentType tipo) {
        EquipmentType guardado = equipmentTypePersistencePort.save(tipo);
        eventDispatcherPort.dispatchAll(tipo.pullEvents());

        return guardado;
    }
}
