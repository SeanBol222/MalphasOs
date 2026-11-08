package com.malphasos.malphasos.dataload.application.ports.output;

import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import com.malphasos.malphasos.dataload.application.model.FormatCheck;

/** El formato publicado: comprueba un archivo contra el esquema de {@code contracts/carga}. */
public interface LoadFormatPort {

    FormatCheck check(DataLoadFile archivo);
}
