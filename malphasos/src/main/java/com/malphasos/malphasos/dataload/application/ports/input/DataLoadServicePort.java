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
}
