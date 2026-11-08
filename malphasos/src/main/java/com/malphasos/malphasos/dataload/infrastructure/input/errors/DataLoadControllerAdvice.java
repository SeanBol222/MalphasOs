package com.malphasos.malphasos.dataload.infrastructure.input.errors;

import com.malphasos.malphasos.dataload.application.exception.DataLoadRejectedException;
import com.malphasos.malphasos.dataload.application.model.DataLoadReport;
import com.malphasos.malphasos.dataload.infrastructure.input.model.response.DataLoadReportResponse;
import com.malphasos.malphasos.dataload.infrastructure.input.rest.DataLoadRestAdapter;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Una carga rechazada al escribirla responde como una que no pasó la validación: 422 con un informe, el
 * mismo contrato, para que quien carga lea un solo formato de problema.
 */
@RestControllerAdvice(assignableTypes = DataLoadRestAdapter.class)
public class DataLoadControllerAdvice {

    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    @ExceptionHandler(DataLoadRejectedException.class)
    public DataLoadReportResponse handleRejected(DataLoadRejectedException ex) {
        return DataLoadReportResponse.from(new DataLoadReport(List.of(ex.problema()), List.of()));
    }
}
