package com.malphasos.malphasos.shared.domain.events;

import java.util.ArrayList;
import java.util.List;

/**
 * Raíz de un agregado: la entidad por la que se entra a un grupo de objetos que cambian juntos.
 *
 * <p>Acumula los eventos que sus operaciones van registrando y los entrega de una vez cuando alguien
 * los recoge. El agregado no despacha nada: decide qué ocurrió, y quién lo publica es el servicio de
 * aplicación tras confirmar que el cambio se persistió. Así un evento nunca anuncia algo que la base
 * de datos terminó rechazando.
 *
 * <p>Las subclases declaran su tipo y su identificador, y construyen la metadata con
 * {@link #metadataFor(String)}. En el original cada método que emitía un evento repetía las cinco
 * líneas de construcción de {@link EventMetadata}, con el tipo del agregado escrito a mano cada vez.
 */
public abstract class AggregateRoot {

    private final List<DomainEvent<? extends Payload>> events = new ArrayList<>();

    /** Tipo del agregado tal como viaja en la metadata, p. ej. {@code "Client"}. */
    protected abstract String aggregateType();

    /** Identificador de esta instancia, en texto, tal como viaja en la metadata. */
    protected abstract String aggregateId();

    /**
     * Metadata para un hecho de este agregado, con su tipo e identificador ya resueltos.
     *
     * @param eventType hecho concreto, p. ej. {@code "client.created"}
     */
    protected EventMetadata metadataFor(String eventType) {
        return EventMetadata.of(aggregateType(), eventType, aggregateId());
    }

    protected void registerEvent(DomainEvent<? extends Payload> event) {
        if (event == null) {
            throw new IllegalArgumentException("No se puede registrar un evento nulo");
        }

        events.add(event);
    }

    /**
     * Entrega los eventos acumulados y deja el agregado limpio.
     *
     * <p>Recoger vacía: llamar dos veces no publica el mismo evento dos veces. La lista devuelta es
     * inmutable, de modo que quien la recibe no puede alterar lo que el agregado registró.
     */
    public List<DomainEvent<? extends Payload>> pullEvents() {
        List<DomainEvent<? extends Payload>> recogidos = List.copyOf(events);
        events.clear();

        return recogidos;
    }

    /** Si quedan eventos por recoger. Útil para no llamar al despachador en balde. */
    public boolean hasPendingEvents() {
        return !events.isEmpty();
    }
}
