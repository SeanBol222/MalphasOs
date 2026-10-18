package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheetPdf;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.UUID;

/** La hoja de vida de un equipo en PDF, generada en el servidor (2026-10-18). */
public interface LifeSheetPdfServicePort {

    /**
     * Con el mismo alcance que la hoja de vida: quien no la puede ver, no la puede imprimir.
     *
     * @throws com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException si
     *     el equipo no existe o queda fuera del alcance de quien consulta
     * @throws com.malphasos.malphasos.equipment.application.ports.output.LifeSheetPdfUnavailableException
     *     si el conversor no responde
     */
    LifeSheetPdf pdf(UUID idEquipoCliente, ReadScope alcance);
}
