package com.malphasos.malphasos.person.application.model.identity;

import lombok.Builder;

/**
 * Datos que se envían al proveedor de identidad para dar de alta un usuario.
 *
 * <p>Es una representación distinta de {@link com.malphasos.malphasos.person.domain.person.Person}
 * a propósito: el proveedor de identidad solo necesita lo imprescindible para autenticar, y la
 * contraseña no pertenece al modelo de dominio.
 *
 * <p>En el proyecto original esta clase se llamaba {@code PersonIdentityResponse}, pese a viajar
 * hacia el proveedor y no venir de él. Se renombra porque el nombre describía lo contrario de su
 * función.
 */
@Builder
public record PersonIdentityRequest(
        String userName,
        String email,
        String firstName,
        String lastName,
        String password) {
}
