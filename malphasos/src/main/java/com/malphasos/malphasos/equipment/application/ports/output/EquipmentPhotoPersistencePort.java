package com.malphasos.malphasos.equipment.application.ports.output;

import com.malphasos.malphasos.equipment.domain.clientEquipment.EquipmentPhoto;
import java.util.Optional;
import java.util.UUID;

/** El almacén de las fotos de los equipos del cliente: una por equipo (V58). */
public interface EquipmentPhotoPersistencePort {

    Optional<EquipmentPhoto> find(UUID idEquipoCliente);

    EquipmentPhoto save(UUID idEquipoCliente, EquipmentPhoto foto);

    /** Quita la foto del equipo; la imagen se queda en el almacén. No pasa nada si no tenía. */
    void remove(UUID idEquipoCliente);
}
