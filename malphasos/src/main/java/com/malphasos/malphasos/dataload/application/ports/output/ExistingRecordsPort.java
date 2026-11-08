package com.malphasos.malphasos.dataload.application.ports.output;

import com.malphasos.malphasos.dataload.application.model.ExistingRecords;

/** Lo que ya hay en MalphasOS, leído con los puertos de cada módulo y sin modificar ninguno. */
public interface ExistingRecordsPort {

    ExistingRecords read();
}
