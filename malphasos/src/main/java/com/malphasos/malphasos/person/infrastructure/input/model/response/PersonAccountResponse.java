package com.malphasos.malphasos.person.infrastructure.input.model.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Si la persona tiene cuenta y con qué usuario entra (2026-11-02). No tenerla es un estado, no un
 * error: es el de las fichas que eran encargados hasta V53.
 */
@Schema(name = "PersonAccountResponse")
public record PersonAccountResponse(
        @Schema(description = "Si tiene usuario en el proveedor de identidad")
        boolean tieneCuenta,

        @Schema(description = "Su nombre de usuario, si tiene cuenta", nullable = true)
        String nombreUsuario) {
}
