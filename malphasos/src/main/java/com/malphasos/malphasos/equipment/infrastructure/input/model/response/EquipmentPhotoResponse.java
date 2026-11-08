package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import com.malphasos.malphasos.equipment.domain.clientEquipment.EquipmentPhoto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(name = "EquipmentPhotoResponse")
public record EquipmentPhotoResponse(
        @Schema(description = "La foto, PNG como data: URI") String imagen,
        @Schema(description = "Cuando se subio") LocalDateTime actualizada) {

    public static EquipmentPhotoResponse from(EquipmentPhoto foto) {
        return new EquipmentPhotoResponse(foto.dataUri(), foto.actualizada());
    }
}
