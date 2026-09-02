package com.malphasos.malphasos.client.application.ports.input;

import com.malphasos.malphasos.client.application.services.headquarter.commands.CreateHeadquarterCommand;
import com.malphasos.malphasos.client.application.services.headquarter.commands.DeactivateHeadquarterCommand;
import com.malphasos.malphasos.client.application.services.headquarter.commands.UpdateHeadquarterCommand;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import java.util.List;
import java.util.UUID;

/** Casos de uso sobre sedes. */
public interface HeadquarterServicePort {

    List<Headquarter> findAll();

    /** Sedes de un cliente. Falla si el cliente no existe, en vez de devolver una lista vacía. */
    List<Headquarter> findByClient(UUID idCliente);

    Headquarter findById(UUID id);

    Headquarter create(CreateHeadquarterCommand command);

    Headquarter update(UpdateHeadquarterCommand command);

    /** Cierra la sede sin borrarla, conservando el historial. */
    void deactivate(DeactivateHeadquarterCommand command);
}
