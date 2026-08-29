package com.malphasos.malphasos.person.infrastructure.input.model.request;

import com.malphasos.malphasos.person.domain.person.PersonType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Builder;

/**
 * Alta de una persona que <b>no</b> accede al sistema: un encargado de sede que solo figura como
 * contacto, por ejemplo.
 *
 * <p>Se distingue de {@link PersonRegisterRequest} en dos cosas: aquí el tipo de persona lo indica
 * el cliente, y no viajan credenciales porque no se crea ningún usuario.
 *
 * <p>El DTO equivalente del proyecto original carecía de {@code tipoPersona} pese a alimentar una
 * columna obligatoria, de modo que el endpoint de creación fallaba siempre contra la restricción de
 * la base; y a cambio pedía usuario y contraseña, que ese caso de uso no utiliza.
 */
@Builder
@Schema(name = "PersonCreateRequest", description = "Alta de una persona sin acceso al sistema")
public record PersonCreateRequest(
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

        @Schema(description = "Funcion principal de la persona", example = "MANAGER")
        @NotNull(message = "El tipo de persona es obligatorio")
        PersonType tipoPersona,

        @Schema(description = "Funcion secundaria, solo MANAGER", example = "MANAGER")
        PersonType segundoTipoPersona,

        List<@Valid EmailPersonCreateRequest> emailPersonList,
        List<@Valid PhonePersonCreateRequest> phonePersonList) {
}
