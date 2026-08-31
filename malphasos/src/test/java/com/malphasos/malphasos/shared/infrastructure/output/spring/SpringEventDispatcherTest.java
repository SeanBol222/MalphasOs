package com.malphasos.malphasos.shared.infrastructure.output.spring;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.malphasos.malphasos.shared.domain.events.DomainEvent;
import com.malphasos.malphasos.shared.domain.events.EventMetadata;
import com.malphasos.malphasos.shared.domain.events.Payload;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SpringEventDispatcherTest {

    private record ThingPayload(String nombre) implements Payload {}

    private record ThingHappened(EventMetadata metadata, ThingPayload payload)
            implements DomainEvent<ThingPayload> {}

    @Mock private ApplicationEventPublisher publisher;

    private ThingHappened evento(String tipo) {
        return new ThingHappened(EventMetadata.of("Thing", tipo, "T-1"), new ThingPayload("x"));
    }

    @Test
    @DisplayName("cada evento llega al publicador de Spring tal cual")
    void despachaElEvento() {
        ThingHappened evento = evento("thing.created");

        new SpringEventDispatcher(publisher).dispatch(evento);

        verify(publisher).publishEvent(evento);
    }

    @Test
    @DisplayName("dispatchAll respeta el orden en que el agregado registro los eventos")
    void despachaEnOrden() {
        ThingHappened primero = evento("thing.created");
        ThingHappened segundo = evento("thing.updated");

        new SpringEventDispatcher(publisher).dispatchAll(List.of(primero, segundo));

        InOrder orden = inOrder(publisher);
        orden.verify(publisher).publishEvent(primero);
        orden.verify(publisher).publishEvent(segundo);
    }

    @Test
    @DisplayName("un agregado que no registro nada no molesta al publicador")
    void listaVaciaNoPublica() {
        new SpringEventDispatcher(publisher).dispatchAll(List.of());

        verifyNoInteractions(publisher);
    }
}
