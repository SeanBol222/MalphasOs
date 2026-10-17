package com.malphasos.malphasos.company.domain.serviceCompany.events;

import com.malphasos.malphasos.shared.domain.events.Payload;

/** Lo que un evento de la empresa cuenta de ella. El logo no viaja: basta saber si lo tiene. */
public record ServiceCompanyPayload(String nombre, String nit, boolean conLogo) implements Payload {
}
