package com.malphasos.malphasos.equipment.application.ports.input;

import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheet;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.UUID;

/** Lo que hace falta para consultar la hoja de vida de un equipo instalado. */
public interface LifeSheetServicePort {

    /**
     * La hoja de vida completa de un equipo, con sus cuatro secciones.
     *
     * <p><b>Solo lectura, y no por falta de tiempo.</b> RF-24 pide «modificar/eliminar hoja de vida»,
     * y editarla no significa nada: cada dato de este documento vive en su propio agregado y se
     * corrige donde vive —la sede en la sede, el modelo en el modelo— con las operaciones que ya
     * existen. Un {@code PATCH} sobre la hoja de vida tendría que repartir los cambios entre cinco
     * agregados de tres módulos, y cambiar la marca desde la hoja de vida de un equipo la cambiaría
     * para <b>todos</b> los que la comparten sin que eso se vea desde aquí.
     *
     * @throws com.malphasos.malphasos.equipment.domain.exception.ClientEquipmentNotFoundException si
     *     el equipo no existe o queda fuera del alcance de quien consulta
     */
    LifeSheet findByEquipment(UUID idEquipoCliente, ReadScope alcance);
}
