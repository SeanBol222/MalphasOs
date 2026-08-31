package com.malphasos.malphasos.shared.infrastructure.output.spring;

import com.malphasos.malphasos.shared.application.ports.output.EventDispatcherPort;
import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.Payload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Despacha los eventos dentro del mismo proceso, sobre el publicador de Spring.
 *
 * <p>Es el único adaptador del puerto por ahora, de modo que no lleva {@code @Qualifier}: mientras
 * haya una sola implementación, pedirlo obligaría a nombrarla en cada punto de inyección sin que
 * hubiera nada entre lo que elegir. Cuando exista un segundo despachador habrá que distinguirlos, y
 * ese será el momento de decidir cuál es el de por omisión.
 *
 * <p>El adaptador de RabbitMQ del original no se migró junto con esto: hoy no hay ningún consumidor
 * que escuche, su binding no casa con la clave de publicación que usa, y los dos listeners que lo
 * consumirían están desactivados. Portarlo ahora sería traer un componente sin uso y con un defecto
 * conocido.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringEventDispatcher implements EventDispatcherPort {

    private final ApplicationEventPublisher publisher;

    @Override
    public void dispatch(DomainEvent<? extends Payload> event) {
        log.debug("Despachando {} del agregado {}",
                event.metadata().eventType(), event.metadata().aggregateId());

        publisher.publishEvent(event);
    }
}
