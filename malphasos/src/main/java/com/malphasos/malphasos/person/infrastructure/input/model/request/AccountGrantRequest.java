package com.malphasos.malphasos.person.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Lo que hace falta para dar cuenta a una ficha que no la tiene (2026-11-02). El correo no viaja: es
 * el de la cuenta, o el primero vigente de la ficha.
 */
@Schema(name = "AccountGrantRequest")
public record AccountGrantRequest(
        @Schema(description = "Nombre de usuario en el proveedor de identidad", example = "juan.perez")
        @NotBlank(message = "El nombre de usuario es obligatorio")
        String nombreUsuario,

        @Schema(description = "Contraseña temporal; la persona tendrá que cambiarla al entrar")
        @NotBlank(message = "La contraseña temporal es obligatoria")
        String password) {
}
