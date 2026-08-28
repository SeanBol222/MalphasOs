package com.malphasos.malphasos.person.infrastructure.input.model.request;

import com.malphasos.malphasos.person.domain.person.PersonType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Builder;

/**
 * Alta de una persona <b>con</b> acceso al sistema: además del registro en la base, se le crea un
 * usuario en el proveedor de identidad.
 *
 * <p>No lleva tipo de persona: lo determina el endpoint al que se envía, de modo que no puede
 * discrepar del rol que se asigna en el proveedor de identidad.
 *
 * <p>Se exige al menos un correo porque el usuario se crea con él como identificador.
 */
@Builder
@Schema(name = "PersonRegisterRequest", description = "Alta de una persona con acceso al sistema")
public record PersonRegisterRequest(
        @Schema(description = "Cedula de la persona", example = "1234567890")
        @NotBlank(message = "La cedula es obligatoria")
        @Size(max = 10, message = "La cedula no puede exceder 10 caracteres")
        String cedula,

        @Schema(description = "Primer nombre", example = "Juan")
        @NotBlank(message = "El primer nombre es obligatorio")
        @Size(max = 50)
        String primerNombre,

        @Schema(description = "Segundo nombre", example = "Carlos")
        @Size(max = 50)
        String segundoNombre,

        @Schema(description = "Primer apellido", example = "Perez")
        @NotBlank(message = "El primer apellido es obligatorio")
        @Size(max = 50)
        String primerApellido,

        @Schema(description = "Segundo apellido, opcional", example = "Gomez")
        @Size(max = 50)
        String segundoApellido,

        @Schema(description = "Funcion secundaria, solo MANAGER", example = "MANAGER")
        PersonType segundoTipoPersona,

        @Schema(description = "Nombre de usuario en el proveedor de identidad", example = "juan.perez")
        @NotBlank(message = "El nombre de usuario es obligatorio")
        String nombreUsuario,

        @Schema(description = "Contrasena inicial", example = "P@ssw0rd")
        @NotBlank(message = "La contrasena es obligatoria")
        String password,

        @Schema(description = "Al menos un correo: con el se crea el usuario")
        @NotEmpty(message = "Se requiere al menos un correo")
        @Valid List<EmailPersonCreateRequest> emailPersonList,

        @Valid List<PhonePersonCreateRequest> phonePersonList) {
}
