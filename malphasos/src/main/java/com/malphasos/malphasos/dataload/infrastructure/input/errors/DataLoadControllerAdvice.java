package com.malphasos.malphasos.dataload.infrastructure.input.errors;

import com.malphasos.malphasos.dataload.application.exception.DataLoadRejectedException;
import com.malphasos.malphasos.dataload.application.model.DataLoadReport;
import com.malphasos.malphasos.dataload.infrastructure.input.model.response.DataLoadReportResponse;
import com.malphasos.malphasos.dataload.infrastructure.input.rest.DataLoadRestAdapter;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Una carga rechazada al escribirla responde como una que no pasó la validación: 422 con un informe, el
 * mismo contrato, para que quien carga lea un solo formato de problema.
 *
 * <p>Va antes que el advice global, y no por gusto: el rechazo de la base lleva dentro su causa, y Spring,
 * al recorrer los advices, acepta el primero que sepa responder a la excepción <em>o a su causa</em>. Sin
 * orden, el global ganaba por la causa y la carga respondía un 409 genérico sin informe (2026-11-07).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = DataLoadRestAdapter.class)
public class DataLoadControllerAdvice {

    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    @ExceptionHandler(DataLoadRejectedException.class)
    public DataLoadReportResponse handleRejected(DataLoadRejectedException ex) {
        return DataLoadReportResponse.from(new DataLoadReport(List.of(ex.problema()), List.of()));
    }
}
