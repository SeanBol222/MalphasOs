package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.DecommissionClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.RegisterClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.RelocateClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.UpdateClientEquipmentCommand;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import java.util.List;
import java.util.UUID;

/** Casos de uso sobre las unidades que poseen los clientes. */
public interface ClientEquipmentServicePort {

    List<ClientEquipment> findAll();

    ClientEquipment findById(UUID id);

    /** Inventario de un área de servicio. */
    List<ClientEquipment> findByServiceArea(UUID idAreaServicio);

    ClientEquipment register(RegisterClientEquipmentCommand command);

    ClientEquipment relocate(RelocateClientEquipmentCommand command);

    ClientEquipment update(UpdateClientEquipmentCommand command);

    /** Da de baja la unidad sin borrarla. */
    void decommission(DecommissionClientEquipmentCommand command);
}
