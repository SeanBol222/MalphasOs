package com.malphasos.malphasos.client.application.ports.output;

import com.malphasos.malphasos.client.domain.client.Client;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que la capa de aplicación necesita de un almacén de clientes.
 *
 * <p>Sin {@code delete}: aquí nada se borra, retirar un cliente es guardarlo con su estado en falso.
 * Y sin {@code update} aparte de {@code save}, porque el agregado ya lleva su identificador dentro.
 */
public interface ClientPersistencePort {

    List<Client> findAll();

    Optional<Client> findById(UUID id);

    /** Para comprobar que un documento no esté ya registrado antes de dar un error del motor. */
    Optional<Client> findByDocumento(String documento);

    Client save(Client client);
}
