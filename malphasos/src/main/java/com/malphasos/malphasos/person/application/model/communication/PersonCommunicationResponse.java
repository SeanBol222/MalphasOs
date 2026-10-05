package com.malphasos.malphasos.person.application.model.communication;

import com.malphasos.malphasos.person.domain.person.PersonType;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

    /**
     * «Ana Maria Perez Gomez»: los nombres y apellidos que tenga, en orden y sin huecos.
     *
     * <p>Vive aqui y no en quien lo usa porque lo necesitan dos modulos —el responsable de un area en
     * {@code client}, el ingeniero de un servicio en {@code report}— y dos copias de la misma regla
     * acaban diciendo cosas distintas.
     */
    public String nombreCompleto() {
        return Stream.of(primerNombre, segundoNombre, primerApellido, segundoApellido)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(parte -> !parte.isEmpty())
                .collect(Collectors.joining(" "));
    }
}
