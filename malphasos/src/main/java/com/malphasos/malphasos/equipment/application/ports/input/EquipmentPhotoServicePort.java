package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.domain.clientEquipment.EquipmentPhoto;
import java.util.UUID;

/**
 * Poner y quitar la foto de un equipo del cliente (V58, 2026-11-08). Se lee con su hoja de vida, que es
 * donde se muestra, y por eso aquí no hay consulta.
 */
public interface EquipmentPhotoServicePort {

    /** Pone o cambia la foto: un PNG. El equipo tiene que existir. */
    EquipmentPhoto change(UUID idEquipoCliente, byte[] png);

    /** La quita. El equipo tiene que existir; si no tenía foto, no pasa nada. */
    void remove(UUID idEquipoCliente);
}
