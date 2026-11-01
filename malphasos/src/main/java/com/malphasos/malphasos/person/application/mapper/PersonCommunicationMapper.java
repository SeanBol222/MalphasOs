package com.malphasos.malphasos.person.application.mapper;

import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationResponse;
import com.malphasos.malphasos.person.domain.person.Person;
import org.mapstruct.Mapper;

/**
 * Traduce entre los DTO que cruzan la frontera del módulo y el modelo de dominio.
 *
 * <p>Vive en la capa de aplicación porque ambos extremos del mapeo le pertenecen: los DTO de
 * comunicación y el dominio. Los mappers de {@code infrastructure} traducen hacia tecnologías
 * concretas —el API REST, JPA—; este no toca ninguna.
 *
 * <p>Hasta el 2026-11-01 traducía también en el otro sentido, para que {@code client} registrara a
 * los encargados. Salieron con V53, y desde entonces ningún otro módulo crea personas.
 */
@Mapper(componentModel = "spring")
public interface PersonCommunicationMapper {

    PersonCommunicationResponse toResponse(Person person);
}
