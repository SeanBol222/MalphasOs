package com.malphasos.malphasos.client.domain.client.events;

import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.UUID;

/**
 * Persona que pasa a representar a un cliente, o que deja de hacerlo.
 *
 * <p>Lleva el identificador de la persona porque el consumidor que importa está al otro lado: el
 * módulo de personas, que quizá deba reaccionar cuando alguien gana o pierde esa condición.
 */
public record LegalRepresentativePayload(UUID idPersona) implements Payload {
}
