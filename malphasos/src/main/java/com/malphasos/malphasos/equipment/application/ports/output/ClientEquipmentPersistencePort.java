package com.malphasos.malphasos.equipment.application.ports.output;

import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Almacén de las unidades que poseen los clientes. */
public interface ClientEquipmentPersistencePort {

    List<ClientEquipment> findAll();

    Optional<ClientEquipment> findById(UUID id);

    /** Inventario de un área de servicio: lo que hay instalado en ella. */
    List<ClientEquipment> findByServiceArea(UUID idAreaServicio);

    ClientEquipment save(ClientEquipment clientEquipment);
}
