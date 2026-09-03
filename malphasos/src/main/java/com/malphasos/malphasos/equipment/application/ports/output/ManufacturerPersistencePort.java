package com.malphasos.malphasos.equipment.application.ports.output;

import com.malphasos.malphasos.equipment.domain.manufacturer.Manufacturer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Almacén de fabricantes. Sin borrado: retirar es guardar con el estado en falso. */
public interface ManufacturerPersistencePort {

    List<Manufacturer> findAll();

    Optional<Manufacturer> findById(UUID id);

    Manufacturer save(Manufacturer manufacturer);
}
