package com.malphasos.malphasos.client.domain.client.events;

import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.UUID;

/**
 * Persona que pasa a supervisar a un cliente, con el cargo con que se la nombra, o que deja de hacerlo.
 *
 * <p>Lleva el identificador de la persona porque el consumidor que importa está al otro lado: el
 * módulo de personas, que quizá deba reaccionar cuando alguien gana o pierde esa condición.
 *
 * @param cargo nulo al retirarla, o si se la nombra sin cargo
 */
public record SupervisorPayload(UUID idPersona, String cargo) implements Payload {
}
