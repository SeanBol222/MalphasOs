package com.malphasos.malphasos.person.infrastructure.output.mapper;

import com.malphasos.malphasos.person.domain.person.EmailPerson;
import com.malphasos.malphasos.person.infrastructure.output.entities.EmailPersonEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Traduce correos entre el dominio y la persistencia.
 *
 * <p>La referencia a la persona no se mapea desde el dominio porque allí no existe: el correo
 * pertenece a la persona, no al revés. La establece {@code PersonPersistenceMapper} tras armar el
 * grafo completo.
 */
@Mapper(componentModel = "spring")
public interface EmailPersonPersistenceMapper {

    @Mapping(target = "person", ignore = true)
    EmailPersonEntity toEntity(EmailPerson email);

    EmailPerson toDomain(EmailPersonEntity entity);
}
