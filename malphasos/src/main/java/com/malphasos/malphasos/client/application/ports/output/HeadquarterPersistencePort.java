package com.malphasos.malphasos.client.application.ports.output;

import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lo que la capa de aplicación necesita de un almacén de sedes. Sin borrado: aquí nada se borra. */
public interface HeadquarterPersistencePort {

    List<Headquarter> findAll();

    Optional<Headquarter> findById(UUID id);

    List<Headquarter> findByClient(UUID idCliente);

    Headquarter save(Headquarter headquarter);
}
