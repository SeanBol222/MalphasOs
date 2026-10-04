package com.malphasos.malphasos.client.application.ports.input;

import java.util.Set;
import java.util.UUID;

/**
 * Lo segundo que este módulo publica hacia los demás: a qué clientes pertenece una persona.
 *
 * <p>Existe para que el filtrado por dueño se pueda resolver desde fuera de este módulo sin que
 * nadie tenga que conocer {@code representante_legal} ni el agregado {@code Client}. Lo consultan
 * los adaptadores de entrada de {@code equipment}, {@code work-order} y {@code report}, que ya
 * dependen de este módulo —la dependencia iba en ese sentido mucho antes de esto, de modo que no
 * hubo que invertir nada—.
 *
 * <p>Es el mismo patrón que {@code PersonCommunicationPort} y por la misma razón: la superficie es
 * mínima a propósito. No publica el cliente, solo su identificador, que es todo lo que un filtro
 * necesita.
 */
public interface ClientOwnershipPort {

    /**
     * Identificadores de los clientes que esta persona representa legalmente.
     *
     * <p>Devuelve un conjunto porque la llave de {@code representante_legal} es compuesta: una
     * misma persona puede representar a varios clientes, y el esquema lo permite desde el principio.
     *
     * <p>Un conjunto vacío significa que no representa a ninguno, no que el alcance sea libre. La
     * distinción la sostiene {@code ReadScope}, no este puerto.
     */
    Set<UUID> clientsRepresentedBy(UUID idPersona);
}
