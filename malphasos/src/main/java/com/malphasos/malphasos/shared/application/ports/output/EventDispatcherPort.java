package com.malphasos.malphasos.shared.application.ports.output;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.List;

/**
 * Publica los eventos que un agregado registró.
 *
 * <p>Es arquitectura hexagonal aplicada a los propios eventos: la capa de aplicación depende de este
 * puerto y no de si el mecanismo real es una llamada en el mismo proceso o un mensaje que sale por
 * la red. Cambiar de uno a otro no toca ni un servicio.
 */
public interface EventDispatcherPort {

    void dispatch(DomainEvent<? extends Payload> event);

    /**
     * Publica en orden lo que {@code pullEvents()} devolvió.
     *
     * <p>Existe porque un agregado casi siempre entrega varios eventos a la vez y el original
     * obligaba a que cada servicio escribiera el bucle. Una lista vacía no hace nada.
     */
    default void dispatchAll(List<? extends DomainEvent<? extends Payload>> events) {
        events.forEach(this::dispatch);
    }
}
