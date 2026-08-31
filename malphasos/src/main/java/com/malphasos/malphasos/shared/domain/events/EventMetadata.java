package com.malphasos.malphasos.shared.domain.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Sitúa un evento: quién lo produjo, cuándo y bajo qué versión del contrato.
 *
 * <p>Se construye con {@link #of(String, String, String)} y no con el constructor, para que el
 * identificador y la marca de tiempo se generen en un solo sitio. En el original cada agregado
 * repetía las cinco líneas de construcción en cada método que emitía un evento, de modo que un
 * error de dedo en {@code aggregateType} solo se habría notado al depurar un consumidor.
 *
 * <p>Tampoco lleva ya el campo {@code eventTopic}. En el original el dominio escribía ahí el nombre
 * del exchange de RabbitMQ, un detalle de transporte que no le corresponde y que además duplicaba
 * el valor que el propio despachador ya conocía. Dónde se publica un evento lo decide el adaptador
 * de salida.
 *
 * @param eventId identificador único de esta emisión, para que un consumidor pueda descartar
 *     duplicados si el transporte entrega el mismo evento dos veces
 * @param aggregateType tipo del agregado que lo emitió, p. ej. {@code "Client"}
 * @param eventType hecho concreto, p. ej. {@code "client.created"}
 * @param version versión del contrato del payload, para que un consumidor viejo pueda distinguir
 *     un evento que cambió de forma
 * @param occurredAt momento en que ocurrió el hecho, no en que se despachó
 * @param aggregateId identificador de la instancia concreta
 */
public record EventMetadata(
        UUID eventId,
        String aggregateType,
        String eventType,
        int version,
        Instant occurredAt,
        String aggregateId) {

    /** Versión inicial del contrato de un payload. */
    public static final int INITIAL_VERSION = 1;

    public EventMetadata {
        requireText(aggregateType, "aggregateType");
        requireText(eventType, "eventType");
        requireText(aggregateId, "aggregateId");

        if (eventId == null) {
            throw new IllegalArgumentException("Un evento sin eventId no se puede deduplicar");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Un evento sin occurredAt no se puede ordenar");
        }
        if (version < INITIAL_VERSION) {
            throw new IllegalArgumentException(
                    "La version de un evento empieza en " + INITIAL_VERSION + ", y se recibio " + version);
        }
    }

    /** Metadata de un evento en la versión inicial de su contrato. */
    public static EventMetadata of(String aggregateType, String eventType, String aggregateId) {
        return of(aggregateType, eventType, aggregateId, INITIAL_VERSION);
    }

    /** Metadata de un evento cuyo payload ya cambió de forma alguna vez. */
    public static EventMetadata of(String aggregateType, String eventType, String aggregateId, int version) {
        return new EventMetadata(
                UUID.randomUUID(), aggregateType, eventType, version, Instant.now(), aggregateId);
    }

    private static void requireText(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Un evento necesita " + campo);
        }
    }
}
