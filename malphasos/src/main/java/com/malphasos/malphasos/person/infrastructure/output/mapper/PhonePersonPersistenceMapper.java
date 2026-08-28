package com.malphasos.malphasos.person.infrastructure.output.mapper;

import com.malphasos.malphasos.person.domain.person.PhonePerson;
import com.malphasos.malphasos.person.infrastructure.output.entities.PhonePersonEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Traduce teléfonos entre el dominio y la persistencia.
 *
 * <p>La referencia a la persona la establece {@code PersonPersistenceMapper}, por el mismo motivo
 * descrito en {@link EmailPersonPersistenceMapper}.
 */
@Mapper(componentModel = "spring")
public interface PhonePersonPersistenceMapper {

    @Mapping(target = "person", ignore = true)
    PhonePersonEntity toEntity(PhonePerson phone);

    PhonePerson toDomain(PhonePersonEntity entity);
}
