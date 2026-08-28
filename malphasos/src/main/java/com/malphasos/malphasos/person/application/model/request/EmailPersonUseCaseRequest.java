package com.malphasos.malphasos.person.application.model.request;

import lombok.Builder;

/** Correo electrónico con el que se da de alta a una persona. */
@Builder
public record EmailPersonUseCaseRequest(String correoPersona) {
}
