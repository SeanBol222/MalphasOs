package com.malphasos.malphasos.shared.domain.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contrato de acumulación y entrega de eventos, con un agregado de prueba mínimo. */
class AggregateRootTest {

    private record ThingPayload(String nombre) implements Payload {}

    private record ThingHappened(EventMetadata metadata, ThingPayload payload)
            implements DomainEvent<ThingPayload> {}

    private static final class Thing extends AggregateRoot {

        private final String id;

        private Thing(String id) {
            this.id = id;
        }

        static Thing create(String id) {
            Thing nueva = new Thing(id);
            nueva.registerEvent(new ThingHappened(nueva.metadataFor("thing.created"), new ThingPayload(id)));

            return nueva;
        }

        void ocurreAlgoMas() {
            registerEvent(new ThingHappened(metadataFor("thing.updated"), new ThingPayload(id)));
        }

        @Override
        protected String aggregateType() {
            return "Thing";
        }

        @Override
        protected String aggregateId() {
            return id;
        }
    }

    @Test
    @DisplayName("metadataFor resuelve el tipo y el identificador del agregado")
    void metadataQuedaResuelta() {
        Thing thing = Thing.create("T-1");

        EventMetadata metadata = thing.pullEvents().getFirst().metadata();

        assertThat(metadata.aggregateType()).isEqualTo("Thing");
        assertThat(metadata.aggregateId()).isEqualTo("T-1");
        assertThat(metadata.eventType()).isEqualTo("thing.created");
        assertThat(metadata.version()).isEqualTo(EventMetadata.INITIAL_VERSION);
        assertThat(metadata.eventId()).isNotNull();
        assertThat(metadata.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("los eventos se entregan en el orden en que ocurrieron")
    void seConservaElOrden() {
        Thing thing = Thing.create("T-2");
        thing.ocurreAlgoMas();

        assertThat(thing.pullEvents())
                .extracting(evento -> evento.metadata().eventType())
                .containsExactly("thing.created", "thing.updated");
    }

    @Test
    @DisplayName("recoger vacia: publicar dos veces no repite los eventos")
    void recogerVacia() {
        Thing thing = Thing.create("T-3");

        assertThat(thing.hasPendingEvents()).isTrue();
        assertThat(thing.pullEvents()).hasSize(1);

        assertThat(thing.hasPendingEvents()).isFalse();
        assertThat(thing.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("la lista entregada es inmutable: nadie altera lo que el agregado registro")
    void laListaEsInmutable() {
        Thing thing = Thing.create("T-4");

        var recogidos = thing.pullEvents();

        assertThatThrownBy(recogidos::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("cada emision lleva su propio eventId, para poder descartar duplicados")
    void cadaEventoTieneSuIdentificador() {
        Thing thing = Thing.create("T-5");
        thing.ocurreAlgoMas();

        assertThat(thing.pullEvents())
                .extracting(evento -> evento.metadata().eventId())
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("registrar un evento nulo falla en el acto, no al despacharlo")
    void noSeRegistranEventosNulos() {
        Thing thing = Thing.create("T-6");

        assertThatThrownBy(() -> thing.registerEvent(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
