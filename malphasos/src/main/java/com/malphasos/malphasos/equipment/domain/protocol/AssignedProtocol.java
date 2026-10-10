package com.malphasos.malphasos.equipment.domain.protocol;

import java.util.UUID;

/**
 * Un protocolo asignado a un tipo de equipo, con su prioridad y la indicación propia de ese tipo.
 *
 * <p>La prioridad no la elige quien llama: la deriva {@link ProtocolAssignment} de la posición en la
 * lista, de modo que va siempre 1, 2, 3… sin huecos ni repetidas.
 *
 * @param indicacion lo que este tipo añade al texto general del protocolo; nula si no añade nada
 */
public record AssignedProtocol(UUID idProtocolo, int prioridad, String indicacion) {
}
