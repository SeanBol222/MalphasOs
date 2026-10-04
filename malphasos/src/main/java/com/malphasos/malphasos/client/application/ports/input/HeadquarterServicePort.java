package com.malphasos.malphasos.client.application.ports.input;

import com.malphasos.malphasos.client.application.services.headquarter.commands.CreateHeadquarterCommand;
import com.malphasos.malphasos.client.application.services.headquarter.commands.DeactivateHeadquarterCommand;
import com.malphasos.malphasos.client.application.services.headquarter.commands.UpdateHeadquarterCommand;
import com.malphasos.malphasos.client.domain.headquarter.Headquarter;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.List;
import java.util.UUID;

/** Casos de uso sobre sedes. */
public interface HeadquarterServicePort {

    List<Headquarter> findAll();

    /** Sedes de un cliente. Falla si el cliente no existe, en vez de devolver una lista vacía. */
    /**
     * Las sedes de un cliente, si ese cliente entra en el alcance de quien consulta.
     *
     * @throws com.malphasos.malphasos.client.domain.exception.ClientNotFoundException si el cliente
     *     no existe o queda fuera del alcance
     */
    List<Headquarter> findByClient(UUID idCliente, ReadScope alcance);

    /**
     * Una sede, si pertenece a un cliente del alcance.
     *
     * @throws com.malphasos.malphasos.client.domain.exception.HeadquarterNotFoundException si no
     *     existe o si su cliente queda fuera del alcance
     */
    Headquarter findById(UUID id, ReadScope alcance);

    Headquarter create(CreateHeadquarterCommand command);

    Headquarter update(UpdateHeadquarterCommand command);

    /** Cierra la sede sin borrarla, conservando el historial. */
    void deactivate(DeactivateHeadquarterCommand command);
}
