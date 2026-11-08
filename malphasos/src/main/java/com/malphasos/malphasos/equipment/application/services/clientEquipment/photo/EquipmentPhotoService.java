package com.malphasos.malphasos.equipment.application.services.clientEquipment.photo;

import com.malphasos.malphasos.equipment.application.ports.input.ClientEquipmentServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.EquipmentPhotoServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.EquipmentPhotoPersistencePort;
import com.malphasos.malphasos.equipment.domain.clientEquipment.EquipmentPhoto;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La foto de cada equipo del cliente (V58). Escribir no se acota por dueño, como el resto de escrituras
 * del equipo: la autoridad {@code equipment.write} la tienen la casa —ingenieros y administradores— y no
 * la cuenta de un cliente.
 */
@Service
@RequiredArgsConstructor
public class EquipmentPhotoService implements EquipmentPhotoServicePort {

    private final EquipmentPhotoPersistencePort equipmentPhotoPersistencePort;
    private final ClientEquipmentServicePort clientEquipmentServicePort;

    @Override
    @Transactional
    public EquipmentPhoto change(UUID idEquipoCliente, byte[] png) {
        // Que exista, con su error propio: si no, la foránea saltaría como un conflicto sin explicar.
        clientEquipmentServicePort.findById(idEquipoCliente, ReadScope.unrestricted());

        return equipmentPhotoPersistencePort.save(idEquipoCliente, new EquipmentPhoto(png, LocalDateTime.now()));
    }

    @Override
    @Transactional
    public void remove(UUID idEquipoCliente) {
        clientEquipmentServicePort.findById(idEquipoCliente, ReadScope.unrestricted());
        equipmentPhotoPersistencePort.remove(idEquipoCliente);
    }
}
