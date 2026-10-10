package com.malphasos.malphasos.equipment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.equipment.domain.protocol.AssignedProtocol;
import com.malphasos.malphasos.equipment.domain.protocol.Protocol;
import com.malphasos.malphasos.equipment.domain.protocol.ProtocolAssignment;
import com.malphasos.malphasos.equipment.domain.protocol.ProtocolAssignment.Requested;
import com.malphasos.malphasos.equipment.domain.protocol.events.ProtocolCreatedEvent;
import com.malphasos.malphasos.equipment.domain.protocol.events.ProtocolsAssignedEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Los dos agregados de los protocolos: la actividad del catálogo y su asignación a un tipo. */
class ProtocolTest {

    @Nested
    @DisplayName("Un protocolo")
    class UnProtocolo {

        @Test
        @DisplayName("nace vigente, limpio de espacios, y lo anuncia")
        void nace() {
            Protocol protocolo = Protocol.create("  Limpieza   y desinfección ", " Paño humedecido. ");

            assertThat(protocolo.getNombre()).isEqualTo("Limpieza y desinfección");
            assertThat(protocolo.getDescripcion()).isEqualTo("Paño humedecido.");
            assertThat(protocolo.isEstadoActivo()).isTrue();
            assertThat(protocolo.pullEvents()).singleElement().isInstanceOf(ProtocolCreatedEvent.class);
        }

        @Test
        @DisplayName("sin nombre o sin descripción no existe: la descripción es lo que dice qué hacer")
        void obligatorios() {
            assertThatThrownBy(() -> Protocol.create(" ", "Algo")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Protocol.create("Limpieza", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("qué hacer");
        }

        @Test
        @DisplayName("los largos son los de sus columnas")
        void largos() {
            assertThatThrownBy(() -> Protocol.create("x".repeat(121), "Algo"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Protocol.create("Limpieza", "x".repeat(501)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("la clave ignora mayúsculas, tildes y espacios repetidos")
        void clave() {
            // Es lo que impide repetir un protocolo escribiéndolo distinto: pedido expresamente.
            assertThat(Protocol.clave("Limpieza y desinfección"))
                    .isEqualTo(Protocol.clave("  LIMPIEZA  y DESINFECCION"))
                    .isEqualTo("limpieza y desinfeccion");
        }

        @Test
        @DisplayName("cambiarlo por lo mismo no es un hecho, y no emite")
        void cambioVacio() {
            Protocol protocolo = Protocol.rehydrate(UUID.randomUUID(), "Limpieza", "Paño.", true);

            protocolo.update("Limpieza", " Paño. ");

            assertThat(protocolo.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("retirarlo dos veces emite una sola vez")
        void retirar() {
            Protocol protocolo = Protocol.rehydrate(UUID.randomUUID(), "Limpieza", "Paño.", true);

            protocolo.deactivate();
            protocolo.deactivate();

            assertThat(protocolo.isEstadoActivo()).isFalse();
            assertThat(protocolo.pullEvents()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("La asignación a un tipo de equipo")
    class LaAsignacion {

        private final UUID tipo = UUID.randomUUID();
        private final UUID limpieza = UUID.randomUUID();
        private final UUID electrico = UUID.randomUUID();
        private final UUID metrologia = UUID.randomUUID();

        @Test
        @DisplayName("la prioridad sale de la posición: 1, 2, 3, sin huecos")
        void prioridadPorPosicion() {
            // La decide el usuario ordenando la lista, y nunca el programa: decidido el 2026-10-10.
            ProtocolAssignment asignacion = ProtocolAssignment.empty(tipo);

            asignacion.assign(List.of(
                    new Requested(metrologia, null), new Requested(limpieza, null), new Requested(electrico, null)));

            assertThat(asignacion.getProtocolos()).containsExactly(
                    new AssignedProtocol(metrologia, 1, null),
                    new AssignedProtocol(limpieza, 2, null),
                    new AssignedProtocol(electrico, 3, null));
            assertThat(asignacion.pullEvents()).singleElement().isInstanceOf(ProtocolsAssignedEvent.class);
        }

        @Test
        @DisplayName("reordenar renumera, y lo anuncia")
        void reordenar() {
            ProtocolAssignment asignacion = ProtocolAssignment.rehydrate(tipo, List.of(
                    new AssignedProtocol(limpieza, 1, null), new AssignedProtocol(electrico, 2, null)));

            asignacion.assign(List.of(new Requested(electrico, null), new Requested(limpieza, null)));

            assertThat(asignacion.getProtocolos())
                    .extracting(AssignedProtocol::idProtocolo, AssignedProtocol::prioridad)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(electrico, 1),
                            org.assertj.core.groups.Tuple.tuple(limpieza, 2));
            assertThat(asignacion.pullEvents()).hasSize(1);
        }

        @Test
        @DisplayName("un protocolo no puede estar dos veces en el mismo tipo")
        void sinRepetidos() {
            ProtocolAssignment asignacion = ProtocolAssignment.empty(tipo);

            assertThatThrownBy(() -> asignacion.assign(
                            List.of(new Requested(limpieza, null), new Requested(limpieza, "Otra vez"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("dos veces");
            assertThat(asignacion.getProtocolos()).isEmpty();
        }

        @Test
        @DisplayName("una indicación en blanco es ninguna, y una de más de 300 se rechaza")
        void indicacion() {
            ProtocolAssignment asignacion = ProtocolAssignment.empty(tipo);

            asignacion.assign(List.of(new Requested(limpieza, "  "), new Requested(electrico, " Con guantes ")));

            assertThat(asignacion.getProtocolos())
                    .extracting(AssignedProtocol::indicacion)
                    .containsExactly(null, "Con guantes");
            assertThatThrownBy(() -> asignacion.assign(List.of(new Requested(limpieza, "x".repeat(301)))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("pedir lo que ya hay no es un hecho, y no emite")
        void sinCambios() {
            ProtocolAssignment asignacion = ProtocolAssignment.rehydrate(tipo, List.of(
                    new AssignedProtocol(limpieza, 1, "Con guantes")));

            asignacion.assign(List.of(new Requested(limpieza, " Con guantes ")));

            assertThat(asignacion.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("cambiar solo la indicación sí es un cambio")
        void soloLaIndicacion() {
            ProtocolAssignment asignacion = ProtocolAssignment.rehydrate(tipo, List.of(
                    new AssignedProtocol(limpieza, 1, null)));

            asignacion.assign(List.of(new Requested(limpieza, "Con guantes")));

            assertThat(asignacion.pullEvents()).hasSize(1);
        }

        @Test
        @DisplayName("una lista vacía los quita todos; una que falta es un error")
        void vaciar() {
            ProtocolAssignment asignacion = ProtocolAssignment.rehydrate(tipo, List.of(
                    new AssignedProtocol(limpieza, 1, null)));

            asignacion.assign(List.of());

            assertThat(asignacion.getProtocolos()).isEmpty();
            assertThatThrownBy(() -> asignacion.assign(null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("leída de la base sale en orden de prioridad, y sin eventos")
        void rehidratar() {
            ProtocolAssignment asignacion = ProtocolAssignment.rehydrate(tipo, List.of(
                    new AssignedProtocol(electrico, 2, null), new AssignedProtocol(limpieza, 1, null)));

            assertThat(asignacion.getProtocolos())
                    .extracting(AssignedProtocol::idProtocolo)
                    .containsExactly(limpieza, electrico);
            assertThat(asignacion.pullEvents()).isEmpty();
        }
    }
}
