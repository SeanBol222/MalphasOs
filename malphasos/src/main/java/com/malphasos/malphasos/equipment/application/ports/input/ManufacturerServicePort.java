package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.application.services.manufacturer.commands.CreateManufacturerCommand;
import com.malphasos.malphasos.equipment.application.services.manufacturer.commands.DeactivateManufacturerCommand;
import com.malphasos.malphasos.equipment.application.services.manufacturer.commands.UpdateManufacturerCommand;
import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import java.util.List;
import java.util.UUID;

/** Casos de uso sobre fabricantes. */
public interface ManufacturerServicePort {

    List<Manufacturer> findAll();

    Manufacturer findById(UUID id);

    Manufacturer create(CreateManufacturerCommand command);

    Manufacturer update(UpdateManufacturerCommand command);

    void deactivate(DeactivateManufacturerCommand command);
}
