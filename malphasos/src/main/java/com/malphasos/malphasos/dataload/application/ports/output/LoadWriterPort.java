package com.malphasos.malphasos.dataload.application.ports.output;

import com.malphasos.malphasos.dataload.application.model.ReadFile;
import java.util.List;

/**
 * Escribe una carga ya validada, con los agregados y los puertos de persistencia de cada módulo. Lo que
 * ya existe se reconoce por su llave natural y no se toca.
 *
 * <p>Corre dentro de la transacción de quien lo llama: si algo se rechaza, lanza
 * {@link com.malphasos.malphasos.dataload.application.exception.DataLoadRejectedException} y no queda nada.
 */
public interface LoadWriterPort {

    void write(List<ReadFile> archivos);
}
