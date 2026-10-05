package com.malphasos.malphasos.client.infrastructure.output;

import com.malphasos.malphasos.client.application.ports.output.ClientPersistencePort;
import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.client.infrastructure.output.entities.ClientEntity;
import com.malphasos.malphasos.client.infrastructure.output.mapper.ClientPersistenceMapper;
import com.malphasos.malphasos.client.infrastructure.output.repository.ClientRepository;
import java.util.List;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa el almacén de clientes sobre JPA.
 *
 * <p>Los métodos llevan {@code @Transactional} porque el mapeo recorre las colecciones perezosas del
 * cliente: sin transacción abierta eso lanzaría {@code LazyInitializationException} en cuanto
 * {@code open-in-view} esté desactivado, que es como está en este proyecto.
 */
@Component
@RequiredArgsConstructor
public class ClientPersistenceAdapter implements ClientPersistencePort {

    private final ClientRepository clientRepository;
    private final ClientPersistenceMapper clientPersistenceMapper;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public List<Client> findAll() {
        return clientPersistenceMapper.toDomainList(clientRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Client> findById(UUID id) {
        return clientRepository.findById(id).map(clientPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Client> findAllByIds(Collection<UUID> ids) {
        return clientPersistenceMapper.toDomainList(clientRepository.findAllById(ids));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findIdsRepresentedBy(UUID idPersona) {
        return clientRepository.findIdsRepresentedBy(idPersona);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Client> findByDocumento(String documento) {
        return clientRepository.findByDocumento(documento).map(clientPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySigla(String sigla) {
        return clientRepository.existsBySigla(sigla);
    }

    @Override
    @Transactional
    public void lockAcronymAllocation() {
        // Un candado de transaccion de PostgreSQL con una clave fija: se suelta solo al terminar la
        // transaccion que lo tomo, que es la del alta o el cambio de sigla.
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext('cliente.n_sigla'))")
                .getSingleResult();
    }

    @Override
    @Transactional
    public Client save(Client client) {
        // Se recupera la fila existente para volcar el agregado sobre ella. Construir una entidad
        // nueva en cada guardado haria que Hibernate insertara duplicados de los contactos.
        ClientEntity existente = clientRepository.findById(client.getId()).orElse(null);
        ClientEntity guardada = clientRepository.save(
                clientPersistenceMapper.toEntity(client, existente));

        return clientPersistenceMapper.toDomain(guardada);
    }
}
