package com.malphasos.malphasos.client.infrastructure.output.repository;

import com.malphasos.malphasos.client.infrastructure.output.entities.ClientEntity;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRepository extends JpaRepository<ClientEntity, UUID> {

    Optional<ClientEntity> findByDocumento(String documento);

    boolean existsBySigla(String sigla);

    /**
     * Clientes que esta persona representa, por el lado de la tabla puente.
     *
     * <p>Se escribe a mano y no por nombre de método porque la consulta sale de
     * {@code LegalRepresentativeEntity} y devuelve el identificador del cliente, no la entidad:
     * traer el cliente entero para quedarse con su llave son tres colecciones perezosas de más en
     * una consulta que se hace en cada lectura del API.
     *
     * <p>{@code r.cliente.id} no genera un {@code JOIN}: la relación usa identidad derivada, así
     * que la llave ajena ya está en la fila del puente.
     */
    @Query("""
            select r.cliente.id
            from LegalRepresentativeEntity r
            where r.persona = :idPersona
              and r.estadoActivo = true
            """)
    Set<UUID> findIdsRepresentedBy(@Param("idPersona") UUID idPersona);
}
