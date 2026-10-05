package com.malphasos.malphasos.client.application.ports.output;

import com.malphasos.malphasos.client.domain.client.Client;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    /**
     * Los clientes de esta lista, para resolver un listado acotado por dueño.
     *
     * <p>Una lista vacía devuelve una lista vacía, que es lo correcto: quien no representa a nadie
     * no ve ningún cliente.
     */
    List<Client> findAllByIds(Collection<UUID> ids);

    /**
     * Identificadores de los clientes que esta persona representa, para el filtrado por dueño.
     *
     * <p>Mira solo si el nombramiento sigue activo, no si el cliente lo está: retirar a un
     * representante debe quitarle el acceso en el acto, mientras que un cliente retirado sigue
     * apareciendo en {@code findAll()} y sería incoherente esconderlo solo a su propio
     * representante.
     */
    Set<UUID> findIdsRepresentedBy(UUID idPersona);

    /** Para comprobar que un documento no esté ya registrado antes de dar un error del motor. */
    Optional<Client> findByDocumento(String documento);

    Client save(Client client);

    /** Si algún cliente, activo o retirado, tiene ya esta sigla. */
    boolean existsBySigla(String sigla);

    /**
     * Serializa la asignación de siglas hasta el final de la transacción en curso.
     *
     * <p>Sin esto, dos altas a la vez con la misma sigla base podrían elegir la misma: las dos verían
     * que está libre. El índice único lo pararía, pero en PostgreSQL una transacción que falla no se
     * puede reintentar desde dentro, y quien dio de alta vería un conflicto por algo que no escribió.
     * Con el bloqueo, la segunda espera a que la primera termine y ve la sigla ocupada.
     */
    void lockAcronymAllocation();
}
