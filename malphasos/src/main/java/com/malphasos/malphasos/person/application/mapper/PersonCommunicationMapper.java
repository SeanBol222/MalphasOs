package com.malphasos.malphasos.person.application.mapper;

import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationRequest;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationResponse;
import com.malphasos.malphasos.person.domain.person.EmailPerson;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.domain.person.PhonePerson;
import com.malphasos.malphasos.person.application.model.request.EmailPersonUseCaseRequest;
import com.malphasos.malphasos.person.application.model.request.PhonePersonUseCaseRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

/**
 * Traduce entre los DTO que cruzan la frontera del módulo y el modelo de dominio.
 *
 * <p>Vive en la capa de aplicación porque ambos extremos del mapeo le pertenecen: los DTO de
 * comunicación y el dominio. Los mappers de {@code infrastructure} traducen hacia tecnologías
 * concretas —el API REST, JPA—; este no toca ninguna.
 *
 * <p>Las colecciones ausentes se traducen a listas vacías y no a {@code null}: quien registra a un
 * encargado puede no enviar teléfonos, y el servicio de personas recorre ambas listas para asignar
 * identificadores. Con {@code null} ese recorrido lanzaría {@code NullPointerException} sobre una
 * petición perfectamente válida.
 */
@Mapper(
        componentModel = "spring",
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface PersonCommunicationMapper {

    /**
     * Construye la persona a registrar.
     *
     * <p>El identificador y el estado no se mapean desde la petición: los asigna el servicio, igual
     * que en el resto del módulo. Así ningún módulo puede fijar el identificador de una persona.
     */
    @Mapping(target = "identificador", ignore = true)
    @Mapping(target = "estadoActivo", ignore = true)
    Person toPerson(PersonCommunicationRequest request);

    PersonCommunicationResponse toResponse(Person person);

    @Mapping(target = "idCorreoPersona", ignore = true)
    @Mapping(target = "estadoActivo", ignore = true)
    EmailPerson toEmailPerson(EmailPersonUseCaseRequest request);

    @Mapping(target = "idTelefonoPersona", ignore = true)
    @Mapping(target = "estadoActivo", ignore = true)
    PhonePerson toPhonePerson(PhonePersonUseCaseRequest request);
}
