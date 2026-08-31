package com.malphasos.malphasos.shared.domain.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * La metadata es lo que permite a un consumidor ordenar, deduplicar y encaminar. Un campo vacío no
 * rompe nada al emitir, pero deja el evento inservible al otro lado, así que se rechaza aquí.
 */
class EventMetadataTest {

    @Test
    @DisplayName("of genera identificador y marca de tiempo, y usa la version inicial")
    void ofCompletaLoGenerado() {
        EventMetadata metadata = EventMetadata.of("Client", "client.created", "900123456");

        assertThat(metadata.eventId()).isNotNull();
        assertThat(metadata.occurredAt()).isNotNull().isBeforeOrEqualTo(Instant.now());
        assertThat(metadata.version()).isEqualTo(EventMetadata.INITIAL_VERSION);
    }

    @Test
    @DisplayName("dos emisiones seguidas no comparten identificador")
    void identificadoresDistintos() {
        assertThat(EventMetadata.of("Client", "client.created", "900123456").eventId())
                .isNotEqualTo(EventMetadata.of("Client", "client.created", "900123456").eventId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("el tipo de agregado no puede faltar")
    void aggregateTypeObligatorio(String vacio) {
        assertThatThrownBy(() -> EventMetadata.of(vacio, "client.created", "900123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregateType");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("el tipo de evento no puede faltar")
    void eventTypeObligatorio(String vacio) {
        assertThatThrownBy(() -> EventMetadata.of("Client", vacio, "900123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventType");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("el identificador del agregado no puede faltar")
    void aggregateIdObligatorio(String vacio) {
        assertThatThrownBy(() -> EventMetadata.of("Client", "client.created", vacio))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregateId");
    }

    @Test
    @DisplayName("la version no puede ser anterior a la inicial")
    void versionMinima() {
        assertThatThrownBy(() -> EventMetadata.of("Client", "client.created", "900123456", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    @Test
    @DisplayName("un evento sin marca de tiempo no se puede ordenar")
    void occurredAtObligatorio() {
        assertThatThrownBy(() -> new EventMetadata(
                        UUID.randomUUID(), "Client", "client.created", 1, null, "900123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("occurredAt");
    }
}
