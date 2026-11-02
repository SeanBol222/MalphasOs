package com.malphasos.malphasos.person.infrastructure.output.repository;

import com.malphasos.malphasos.person.infrastructure.output.entities.PersonEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso JPA a la tabla {@code persona}. */
public interface PersonRepository extends JpaRepository<PersonEntity, UUID> {

    /**
     * Cambia la llave primaria de una persona. Hibernate no admite cambiar un {@code @Id}, así que va en
     * SQL; las foráneas que la nombran lo siguen por su {@code ON UPDATE CASCADE} (V56). Vacía y limpia
     * el contexto antes y después: la entidad cargada con la llave vieja ya no describe ninguna fila.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE persona SET k_identificador = :nuevo WHERE k_identificador = :actual",
            nativeQuery = true)
    int changeIdentifier(@Param("actual") UUID actual, @Param("nuevo") UUID nuevo);
}
