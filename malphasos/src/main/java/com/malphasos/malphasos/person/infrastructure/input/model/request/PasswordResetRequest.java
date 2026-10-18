package com.malphasos.malphasos.person.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * La contraseña temporal que pone un administrador. La política —largo, distinta del usuario y del
 * correo— la impone Keycloak, que es quien la guarda: repetirla aquí sería tener dos que pueden
 * discrepar.
 */
@Schema(name = "PasswordResetRequest")
public record PasswordResetRequest(
        @Schema(description = "Contraseña temporal; la persona tendrá que cambiarla al entrar")
        @NotBlank(message = "La contraseña temporal es obligatoria")
        String password) {
}
