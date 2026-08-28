package com.malphasos.malphasos.person.infrastructure.output;

import com.malphasos.malphasos.person.application.ports.output.PersonPersistencePort;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.infrastructure.output.mapper.PersonPersistenceMapper;
import com.malphasos.malphasos.person.infrastructure.output.repository.PersonRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa el puerto de persistencia sobre JPA.
 *
 * <p>Traduce en ambos sentidos entre el modelo de dominio y las entidades, de modo que ni el
 * servicio ni el dominio conocen JPA. No hay borrado físico: desactivar una persona es guardarla
 * con su estado en falso.
 *
 * <p>Los métodos son transaccionales por sí mismos y no dependen de que alguien haya abierto una
 * transacción antes: las colecciones de contactos se cargan de forma perezosa, y traducirlas al
 * dominio fuera de una sesión abierta lanzaría {@code LazyInitializationException}. El proyecto
 * original no lo declaraba en ninguna capa y solo funcionaba porque {@code open-in-view} viene
 * activado por omisión en Spring Boot, manteniendo la sesión abierta durante toda la petición HTTP;
 * aquí esa opción está desactivada por ser un antipatrón conocido.
 */
@Component
@RequiredArgsConstructor
public class PersonPersistenceAdapter implements PersonPersistencePort {

    private final PersonRepository personRepository;
    private final PersonPersistenceMapper personPersistenceMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<Person> findById(UUID id) {
        return personRepository.findById(id).map(personPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Person> findAll() {
        return personPersistenceMapper.toDomainList(personRepository.findAll());
    }

    @Override
    @Transactional
    public Person save(Person person) {
        return personPersistenceMapper.toDomain(
                personRepository.save(personPersistenceMapper.toEntity(person)));
    }
}
