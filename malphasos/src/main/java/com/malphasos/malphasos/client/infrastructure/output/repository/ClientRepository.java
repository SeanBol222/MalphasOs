package com.malphasos.malphasos.client.infrastructure.output.repository;

import com.malphasos.malphasos.client.infrastructure.output.entities.ClientEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<ClientEntity, UUID> {

    Optional<ClientEntity> findByDocumento(String documento);
}
