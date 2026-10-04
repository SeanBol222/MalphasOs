package com.malphasos.malphasos.client.application.ports.input;

import com.malphasos.malphasos.client.application.services.client.commands.AddClientEmailCommand;
import com.malphasos.malphasos.client.application.services.client.commands.AddClientPhoneCommand;
import com.malphasos.malphasos.client.application.services.client.commands.AppointRepresentativeCommand;
import com.malphasos.malphasos.client.application.services.client.commands.CreateClientCommand;
import com.malphasos.malphasos.client.application.services.client.commands.DeactivateClientCommand;
import com.malphasos.malphasos.client.application.services.client.commands.RemoveClientEmailCommand;
import com.malphasos.malphasos.client.application.services.client.commands.RemoveClientPhoneCommand;
import com.malphasos.malphasos.client.application.services.client.commands.RemoveRepresentativeCommand;
import com.malphasos.malphasos.client.application.services.client.commands.UpdateClientCommand;
import com.malphasos.malphasos.client.domain.client.Client;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.List;
import java.util.UUID;

/** Casos de uso sobre clientes. */
public interface ClientServicePort {

    /**
     * Los clientes que quien consulta tiene derecho a ver.
     *
     * <p>El alcance es un argumento y no un dato ambiental: así esta firma declara que este listado
     * filtra, y una implementación nueva no puede olvidarlo.
     */
    List<Client> findAll(ReadScope alcance);

    /** @throws com.malphasos.malphasos.client.domain.exception.ClientNotFoundException si no existe */
    /**
     * Un cliente, si entra en el alcance de quien consulta.
     *
     * @throws com.malphasos.malphasos.client.domain.exception.ClientNotFoundException si no existe
     *     <b>o si queda fuera del alcance</b>. Son el mismo error a propósito: distinguirlos
     *     confirmaría que ese identificador existe.
     */
    Client findById(UUID id, ReadScope alcance);

    Client create(CreateClientCommand command);

    Client update(UpdateClientCommand command);

    /** Retira el cliente sin borrarlo, conservando el historial. */
    void deactivate(DeactivateClientCommand command);

    Client addEmail(AddClientEmailCommand command);

    Client removeEmail(RemoveClientEmailCommand command);

    Client addPhone(AddClientPhoneCommand command);

    Client removePhone(RemoveClientPhoneCommand command);

    /** Nombra representante legal a una persona, que debe existir en el módulo de personas. */
    Client appointRepresentative(AppointRepresentativeCommand command);

    Client removeRepresentative(RemoveRepresentativeCommand command);
}
