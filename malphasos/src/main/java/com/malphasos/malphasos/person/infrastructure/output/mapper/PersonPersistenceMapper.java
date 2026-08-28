package com.malphasos.malphasos.person.infrastructure.output.mapper;

import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.infrastructure.output.entities.PersonEntity;
import java.util.List;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/**
 * Traduce personas entre el dominio y la persistencia, delegando los contactos en sus propios
 * mappers.
 */
@Mapper(
        componentModel = "spring",
        uses = {EmailPersonPersistenceMapper.class, PhonePersonPersistenceMapper.class})
public interface PersonPersistenceMapper {

    PersonEntity toEntity(Person person);

    Person toDomain(PersonEntity entity);

    List<Person> toDomainList(List<PersonEntity> entities);

    /**
     * Cierra la relación bidireccional que JPA necesita para poblar la llave foránea.
     *
     * <p>En el dominio la relación es unidireccional: la persona conoce sus contactos y ellos no la
     * conocen a ella. En la base, en cambio, la llave foránea vive en la fila del contacto, así que
     * sin este paso se persistirían con {@code k_identificador} nulo.
     */
    @AfterMapping
    default void enlazarContactos(@MappingTarget PersonEntity entity) {

        if (entity.getEmailPersonList() != null) {
            entity.getEmailPersonList().forEach(correo -> correo.setPerson(entity));
        }
        if (entity.getPhonePersonList() != null) {
            entity.getPhonePersonList().forEach(telefono -> telefono.setPerson(entity));
        }
    }
}
