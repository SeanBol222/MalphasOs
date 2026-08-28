package com.malphasos.malphasos.person.infrastructure.input.model.request;

import com.malphasos.malphasos.person.domain.person.PersonType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Actualización de los datos de una persona. Los contactos se gestionan por separado, en sus propios
 * endpoints.
 */
@Builder
@Schema(name = "PersonUpdateRequest")
public record PersonUpdateRequest(
        @Schema(description = "Cedula de la persona", example = "1234567890")
        @NotBlank(message = "La cedula es obligatoria")
        @Size(max = 10)
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

        // El original documentaba aqui el ejemplo "representante_legal", que no es ninguno de los
        // valores que admite la base. Al ser un enum, el valor invalido se rechaza al deserializar.
        @Schema(description = "Funcion principal", example = "CEO_CLIENT")
        @NotNull(message = "El tipo de persona es obligatorio")
        PersonType tipoPersona,

        @Schema(description = "Funcion secundaria, solo MANAGER", example = "MANAGER")
        PersonType segundoTipoPersona) {
}
