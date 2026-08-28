package com.malphasos.malphasos.person.infrastructure.output.repository;

import com.malphasos.malphasos.person.infrastructure.output.entities.PersonEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso JPA a la tabla {@code persona}. */
public interface PersonRepository extends JpaRepository<PersonEntity, UUID> {
}
