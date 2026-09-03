package com.malphasos.malphasos.client.infrastructure.input.model.request;

import com.malphasos.malphasos.client.domain.manager.ManagerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Alta de un encargado que todavía no existe como persona.
 *
 * <p>El tipo de persona no se recibe: lo fija el servicio, porque por esta vía siempre se registra
 * un encargado.
 */
@Schema(name = "ManagerRegisterRequest")
public record ManagerRegisterRequest(
        @NotBlank(message = "La cedula es obligatoria")
        @Size(max = 10, message = "La cedula no puede pasar de 10 caracteres")
        String cedula,

        @NotBlank(message = "El primer nombre es obligatorio") String primerNombre,
        String segundoNombre,
        @NotBlank(message = "El primer apellido es obligatorio") String primerApellido,
        String segundoApellido,

        @Schema(description = "De que se encarga", example = "HEADQUARTER")
        @NotNull(message = "El tipo de encargado es obligatorio")
        ManagerType tipo,

        @Schema(description = "Sede o area de la que se encarga, segun el tipo")
        @NotNull(message = "La asignacion es obligatoria")
        UUID idAsignacion,

        @NotEmpty(message = "Se requiere al menos un correo")
        List<@Valid ContactRequest> correos,

        List<@Valid ContactRequest> telefonos) {
}
