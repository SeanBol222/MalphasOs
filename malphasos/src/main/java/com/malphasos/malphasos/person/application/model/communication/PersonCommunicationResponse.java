package com.malphasos.malphasos.person.application.model.communication;

import com.malphasos.malphasos.person.domain.person.PersonType;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Vista de una persona tal como la reciben los demás módulos.
 *
 * <p>Deliberadamente no es el modelo de dominio {@code Person}: quien consulta desde otro contexto
 * obtiene una copia de solo lectura, sin comportamiento ni posibilidad de mutar la persona por la
 * espalda.
 */
@Builder
public record PersonCommunicationResponse(
        UUID identificador,
        String cedula,
        String primerNombre,
        String segundoNombre,
        String primerApellido,
        String segundoApellido,
        PersonType tipoPersona,
        PersonType segundoTipoPersona,
        boolean estadoActivo,
        List<EmailPersonCommunicationResponse> emailPersonList,
        List<PhonePersonCommunicationResponse> phonePersonList) {
}
