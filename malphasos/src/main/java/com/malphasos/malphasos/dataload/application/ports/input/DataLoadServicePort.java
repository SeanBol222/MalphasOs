package com.malphasos.malphasos.dataload.application.ports.input;

import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import com.malphasos.malphasos.dataload.application.model.DataLoadReport;
import java.util.List;

/**
 * La carga de datos de otro sistema, en el formato de {@code contracts/carga} (2026-11-07).
 *
 * <p>No modifica ningún otro módulo, decidido por el usuario: el trabajo pesado es del archivo, y
 * MalphasOS comprueba lo que el esquema no puede ver. Ver la guía del formato.
 */
public interface DataLoadServicePort {

    /** Comprueba los archivos de una carga sin escribir nada. */
    DataLoadReport validate(List<DataLoadFile> archivos);

    /**
     * Valida y, si no hay errores, escribe la carga entera en una transacción: todo o nada. Devuelve el
     * informe de la validación; si es válido, la carga quedó escrita.
     *
     * @throws com.malphasos.malphasos.dataload.application.exception.DataLoadRejectedException si al
     *     escribir la rechaza una regla que la validación no ve; no queda nada escrito
     */
    DataLoadReport apply(List<DataLoadFile> archivos);
}
