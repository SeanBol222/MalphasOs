package com.malphasos.malphasos.person.infrastructure.input.model.response;

import com.malphasos.malphasos.person.domain.person.PersonType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Persona tal como se devuelve al cliente.
 *
 * <p>No expone credenciales: el nombre de usuario y la contraseña solo viajan hacia el proveedor de
 * identidad al registrar, nunca de vuelta.
 */
@Builder
@Schema(name = "PersonResponse")
public record PersonResponse(
        UUID identificador,
        String cedula,
        String primerNombre,
        String segundoNombre,
        String primerApellido,
        String segundoApellido,
        PersonType tipoPersona,
        PersonType segundoTipoPersona,
        boolean estadoActivo,
        List<EmailPersonResponse> emailPersonList,
        List<PhonePersonResponse> phonePersonList) {
}
