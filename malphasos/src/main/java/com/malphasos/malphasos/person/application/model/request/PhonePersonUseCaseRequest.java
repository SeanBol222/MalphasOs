package com.malphasos.malphasos.person.application.model.request;

import lombok.Builder;

/** Teléfono con el que se da de alta a una persona. */
@Builder
public record PhonePersonUseCaseRequest(String telefonoPersona) {
}
