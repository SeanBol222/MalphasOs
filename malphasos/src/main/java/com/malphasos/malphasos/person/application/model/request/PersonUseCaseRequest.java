package com.malphasos.malphasos.person.application.model.request;

import java.util.List;
import lombok.Builder;

/**
 * Datos necesarios para registrar una persona con acceso al sistema.
 *
 * <p>Reúne lo que va al modelo de dominio y lo que va al proveedor de identidad: el nombre de
 * usuario y la contraseña no se persisten con la persona, solo sirven para crear su usuario.
 */
@Builder
public record PersonUseCaseRequest(
        String cedula,
        String primerNombre,
        String segundoNombre,
        String primerApellido,
        String segundoApellido,
        String nombreUsuario,
        String password,
        List<EmailPersonUseCaseRequest> emailPersonList,
        List<PhonePersonUseCaseRequest> phonePersonList) {
}
