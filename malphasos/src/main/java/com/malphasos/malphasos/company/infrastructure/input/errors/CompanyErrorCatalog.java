package com.malphasos.malphasos.company.infrastructure.input.errors;

import lombok.Getter;

/**
 * Códigos de error propios de este contexto.
 *
 * <p>Un código de "no existe" nunca se comparte con uno de "datos inválidos": salen con estados HTTP
 * distintos, y un cliente que solo mire el código no podría distinguirlos.
 */
@Getter
public enum CompanyErrorCatalog {
    COMPANY_NOT_CONFIGURED("ERR_COMPANY_001", "The service company has not been configured"),
    INVALID_COMPANY_DATA("ERR_COMPANY_002", "Invalid service company data");

    private final String code;
    private final String message;

    CompanyErrorCatalog(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
