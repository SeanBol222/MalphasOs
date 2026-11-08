package com.malphasos.malphasos.equipment.infrastructure.output;

import com.malphasos.malphasos.equipment.application.ports.output.EquipmentPhotoPersistencePort;
import com.malphasos.malphasos.equipment.domain.clientEquipment.EquipmentPhoto;
import com.malphasos.malphasos.equipment.infrastructure.output.entities.EquipmentPhotoEntity;
import com.malphasos.malphasos.equipment.infrastructure.output.repository.EquipmentPhotoRepository;
import com.malphasos.malphasos.shared.application.ports.output.DocumentImageStorePort;
import com.malphasos.malphasos.shared.domain.image.DocumentImage;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa el almacén de fotos de equipos sobre JPA, como el de firmas: la imagen vive en el almacén
 * común, una vez por su huella, y la fila del equipo guarda la huella. Los métodos abren su transacción
 * porque leen y escriben en dos tablas.
 */
@Component
@RequiredArgsConstructor
public class EquipmentPhotoPersistenceAdapter implements EquipmentPhotoPersistencePort {

    private final EquipmentPhotoRepository equipmentPhotoRepository;
    private final DocumentImageStorePort documentImageStorePort;

    @Override
    @Transactional(readOnly = true)
    public Optional<EquipmentPhoto> find(UUID idEquipoCliente) {
        return equipmentPhotoRepository.findById(idEquipoCliente).map(this::toDomain);
    }

    @Override
    @Transactional
    public EquipmentPhoto save(UUID idEquipoCliente, EquipmentPhoto foto) {
        DocumentImage imagen = new DocumentImage(foto.imagen(), "image/png");
        documentImageStorePort.store(imagen);

        return toDomain(equipmentPhotoRepository.save(
                new EquipmentPhotoEntity(idEquipoCliente, imagen.huella(), foto.actualizada())));
    }

    @Override
    @Transactional
    public void remove(UUID idEquipoCliente) {
        equipmentPhotoRepository.deleteById(idEquipoCliente);
    }

    private EquipmentPhoto toDomain(EquipmentPhotoEntity fila) {
        DocumentImage imagen = documentImageStorePort.find(fila.getHuella())
                .orElseThrow(() -> new IllegalStateException("La foto del equipo " + fila.getIdEquipoCliente()
                        + " apunta a una imagen que no está: " + fila.getHuella()));

        return new EquipmentPhoto(imagen.contenido(), fila.getActualizada());
    }
}
