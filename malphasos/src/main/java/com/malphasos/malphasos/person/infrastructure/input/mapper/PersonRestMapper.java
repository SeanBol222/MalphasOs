package com.malphasos.malphasos.person.infrastructure.input.mapper;

import com.malphasos.malphasos.person.application.model.request.PersonUseCaseRequest;
import com.malphasos.malphasos.person.domain.person.EmailPerson;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.domain.person.PhonePerson;
import com.malphasos.malphasos.person.infrastructure.input.model.request.EmailPersonCreateRequest;
import com.malphasos.malphasos.person.infrastructure.input.model.request.PersonCreateRequest;
import com.malphasos.malphasos.person.infrastructure.input.model.request.PersonRegisterRequest;
import com.malphasos.malphasos.person.infrastructure.input.model.request.PersonUpdateRequest;
import com.malphasos.malphasos.person.infrastructure.input.model.request.PhonePersonCreateRequest;
import com.malphasos.malphasos.person.infrastructure.input.model.response.PersonResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Traduce entre los DTOs del API y los modelos que entienden el dominio y la capa de aplicación.
 *
 * <p>Los identificadores y el estado no se mapean desde las peticiones: los asigna el servicio. Que
 * el mapeo sea explícito evita que un cliente pueda fijar el identificador de una persona enviándolo
 * en el cuerpo.
 */
@Mapper(componentModel = "spring")
public interface PersonRestMapper {

    @Mapping(target = "identificador", ignore = true)
    @Mapping(target = "estadoActivo", ignore = true)
    Person toPerson(PersonCreateRequest request);

    @Mapping(target = "identificador", ignore = true)
    @Mapping(target = "estadoActivo", ignore = true)
    @Mapping(target = "emailPersonList", ignore = true)
    @Mapping(target = "phonePersonList", ignore = true)
    Person toPerson(PersonUpdateRequest request);

    PersonUseCaseRequest toUseCaseRequest(PersonRegisterRequest request);

    @Mapping(target = "idCorreoPersona", ignore = true)
    @Mapping(target = "estadoActivo", ignore = true)
    EmailPerson toEmailPerson(EmailPersonCreateRequest request);

    @Mapping(target = "idTelefonoPersona", ignore = true)
    @Mapping(target = "estadoActivo", ignore = true)
    PhonePerson toPhonePerson(PhonePersonCreateRequest request);

    PersonResponse toResponse(Person person);

    List<PersonResponse> toResponseList(List<Person> persons);
}
