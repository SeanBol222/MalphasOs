package com.malphasos.malphasos.shared.domain.events;

/**
 * Algo relevante que ocurrió en el dominio y que otros pueden querer saber.
 *
 * <p>Separa el qué del cuándo y el quién: {@link #payload()} lleva los datos del hecho y
 * {@link #metadata()} lo sitúa —qué agregado lo produjo, cuándo, con qué versión—. Las
 * implementaciones son {@code record} de dos campos, una por hecho.
 *
 * @param <T> payload propio del agregado que emite el evento
 */
public interface DomainEvent<T extends Payload> {

    EventMetadata metadata();

    T payload();
}
