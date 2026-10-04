package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.DecommissionClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.RegisterClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.RelocateClientEquipmentCommand;
import com.malphasos.malphasos.equipment.application.services.clientEquipment.commands.UpdateClientEquipmentCommand;
import com.malphasos.malphasos.equipment.domain.clientEquipment.ClientEquipment;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.List;
import java.util.UUID;

/** Casos de uso sobre las unidades que poseen los clientes. */
public interface ClientEquipmentServicePort {

    /**
     * Los equipos instalados que quien consulta tiene derecho a ver.
     *
     * <p>Un equipo no guarda su cliente: guarda su área. El alcance se traduce a áreas preguntando
     * al módulo de clientes, que es el dueño de ese camino.
     */
    List<ClientEquipment> findAll(ReadScope alcance);

    /**
     * Un equipo instalado, si cuelga de un cliente del alcance.
     *
     * @throws com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException si
     *     no existe o si queda fuera del alcance
     */
    ClientEquipment findById(UUID id, ReadScope alcance);

    /** Inventario de un área de servicio. */
    /**
     * Los equipos de un área, si el área entra en el alcance.
     *
     * <p>La comprobación la hace el propio módulo de clientes al resolver el área: aquí basta con
     * pasarle el alcance.
     */
    List<ClientEquipment> findByServiceArea(UUID idAreaServicio, ReadScope alcance);

    ClientEquipment register(RegisterClientEquipmentCommand command);

    /**
     * Traslada una unidad a otra área de servicio, que debe estar abierta y ser del mismo cliente.
     *
     * <p>Entre sedes del mismo cliente sí se puede; cruzar de cliente no.
     *
     * @throws com.malphasos.malphasos.equipment.domain.exception.CrossClientRelocationException si
     *     el área de destino es de otro cliente
     */
    ClientEquipment relocate(RelocateClientEquipmentCommand command);

    ClientEquipment update(UpdateClientEquipmentCommand command);

    /** Da de baja la unidad sin borrarla. */
    void decommission(DecommissionClientEquipmentCommand command);
}
