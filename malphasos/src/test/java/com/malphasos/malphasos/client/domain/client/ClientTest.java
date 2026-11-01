package com.malphasos.malphasos.client.domain.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.client.domain.client.events.ClientCreatedEvent;
import com.malphasos.malphasos.client.domain.client.events.ClientDeactivatedEvent;
import com.malphasos.malphasos.client.domain.client.events.SupervisorAppointedEvent;
import com.malphasos.malphasos.client.domain.client.events.SupervisorPayload;
import com.malphasos.malphasos.client.domain.client.events.SupervisorRemovedEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ClientTest {

    private Client unCliente() {
        return Client.create("900123456", IdentificationType.NIT_JURIDICO, "Hospital Central", null);
    }

    @Test
    @DisplayName("crear un cliente lo deja activo y registra el hecho")
    void crear() {
        Client cliente = unCliente();

        assertThat(cliente.getId()).isNotNull();
        assertThat(cliente.getDocumento()).isEqualTo("900123456");
        assertThat(cliente.getTipoIdentificacion()).isEqualTo(IdentificationType.NIT_JURIDICO);
        assertThat(cliente.isEstadoActivo()).isTrue();

        assertThat(cliente.pullEvents()).singleElement()
                .isInstanceOfSatisfying(ClientCreatedEvent.class, evento -> {
                    assertThat(evento.metadata().aggregateType()).isEqualTo("Client");
                    assertThat(evento.metadata().eventType()).isEqualTo("client.created");
                });
    }

    @Test
    @DisplayName("un cliente nace con sus listas vacias, no nulas")
    void listasInicializadas() {
        Client cliente = unCliente();

        // En el original las listas no se inicializaban, de modo que agregar el primer correo
        // lanzaba NullPointerException.
        assertThat(cliente.getCorreos()).isNotNull().isEmpty();
        assertThat(cliente.getTelefonos()).isNotNull().isEmpty();
        assertThat(cliente.getSupervisores()).isNotNull().isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("un cliente sin razon social se rechaza")
    void razonSocialObligatoria(String razonSocial) {
        assertThatThrownBy(() ->
                        Client.create("900123456", IdentificationType.CC, razonSocial, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("razon social");
    }

    @Test
    @DisplayName("un documento de mas de once caracteres se rechaza, como la columna")
    void documentoDemasiadoLargo() {
        assertThatThrownBy(() ->
                        Client.create("123456789012", IdentificationType.CC, "Otro", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("11");
    }

    @Test
    @DisplayName("un cliente sin tipo de identificacion se rechaza")
    void tipoObligatorio() {
        assertThatThrownBy(() -> Client.create("900123456", null, "Hospital", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo de identificacion");
    }

    @Test
    @DisplayName("agregar un correo no emite evento: es contabilidad interna del cliente")
    void agregarCorreoNoEmite() {
        Client cliente = unCliente();
        cliente.pullEvents();

        cliente.addEmail("contacto@hospital.com");

        assertThat(cliente.getCorreos()).singleElement()
                .extracting(EmailClient::getCorreo).isEqualTo("contacto@hospital.com");
        assertThat(cliente.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("un correo sin arroba se rechaza")
    void correoInvalido() {
        assertThatThrownBy(() -> unCliente().addEmail("no-es-un-correo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("retirar un correo lo deja inactivo sin quitarlo de la lista")
    void retirarCorreo() {
        Client cliente = unCliente();
        UUID idCorreo = cliente.addEmail("contacto@hospital.com").getId();

        cliente.removeEmail(idCorreo);

        assertThat(cliente.getCorreos()).singleElement()
                .extracting(EmailClient::isEstadoActivo).isEqualTo(false);
    }

    @Test
    @DisplayName("retirar un correo que no existe falla, en vez de fingir que funciono")
    void retirarCorreoInexistente() {
        // El original lo buscaba y, si no lo encontraba, no hacia nada y devolvia sin avisar.
        assertThatThrownBy(() -> unCliente().removeEmail(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tiene un correo");
    }

    @Test
    @DisplayName("retirar un telefono que no existe tambien falla")
    void retirarTelefonoInexistente() {
        assertThatThrownBy(() -> unCliente().removePhone(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tiene un telefono");
    }

    @Test
    @DisplayName("nombrar supervisor registra el hecho con la persona y su cargo en el payload")
    void nombrarSupervisor() {
        Client cliente = unCliente();
        cliente.pullEvents();
        UUID persona = UUID.randomUUID();

        cliente.appointSupervisor(persona, "  Ingeniero biomédico ");

        assertThat(cliente.getSupervisores())
                .containsExactly(new ClientSupervisor(persona, "Ingeniero biomédico"));
        assertThat(cliente.pullEvents()).singleElement()
                .isInstanceOfSatisfying(SupervisorAppointedEvent.class, evento -> assertThat(evento.payload())
                        .isEqualTo(new SupervisorPayload(persona, "Ingeniero biomédico")));
    }

    @Test
    @DisplayName("un cargo en blanco es no tener cargo, y uno de más de 80 caracteres no cabe")
    void cargoEnBlancoODemasiadoLargo() {
        Client cliente = unCliente();
        UUID persona = UUID.randomUUID();

        cliente.appointSupervisor(persona, "   ");

        assertThat(cliente.getSupervisores()).containsExactly(new ClientSupervisor(persona, null));
        assertThatThrownBy(() -> cliente.appointSupervisor(UUID.randomUUID(), "x".repeat(81)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("80");
    }

    @Test
    @DisplayName("un cliente admite varios supervisores, en el orden en que se nombraron")
    void variosSupervisores() {
        Client cliente = unCliente();
        UUID primero = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        cliente.appointSupervisor(primero, null);
        cliente.appointSupervisor(segundo, null);

        assertThat(cliente.getSupervisores())
                .extracting(ClientSupervisor::idPersona)
                .containsExactly(primero, segundo);
    }

    @Test
    @DisplayName("nombrar dos veces a la misma persona con el mismo cargo no repite el hecho")
    void nombrarDosVeces() {
        Client cliente = unCliente();
        UUID persona = UUID.randomUUID();
        cliente.appointSupervisor(persona, "Médico");
        cliente.pullEvents();

        cliente.appointSupervisor(persona, "Médico");

        assertThat(cliente.getSupervisores()).hasSize(1);
        assertThat(cliente.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("nombrar de nuevo con otro cargo lo cambia, y lo anuncia")
    void nombrarConOtroCargo() {
        Client cliente = unCliente();
        UUID persona = UUID.randomUUID();
        cliente.appointSupervisor(persona, "Médico");
        cliente.pullEvents();

        cliente.appointSupervisor(persona, "Médico propietario");

        assertThat(cliente.getSupervisores()).containsExactly(new ClientSupervisor(persona, "Médico propietario"));
        assertThat(cliente.pullEvents()).singleElement().isInstanceOf(SupervisorAppointedEvent.class);
    }

    @Test
    @DisplayName("cambiar el cargo exige que ya supervise al cliente")
    void cambiarCargo() {
        Client cliente = unCliente();
        UUID persona = UUID.randomUUID();

        assertThatThrownBy(() -> cliente.changeSupervisorTitle(persona, "Médico"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no supervisa");
        assertThat(cliente.getSupervisores()).isEmpty();

        cliente.appointSupervisor(persona, null);
        cliente.changeSupervisorTitle(persona, "Médico");

        assertThat(cliente.getSupervisores()).containsExactly(new ClientSupervisor(persona, "Médico"));
    }

    @Test
    @DisplayName("retirar un supervisor registra el hecho; retirar a quien no lo es no hace nada")
    void retirarSupervisor() {
        Client cliente = unCliente();
        UUID persona = UUID.randomUUID();
        cliente.appointSupervisor(persona, "Médico");
        cliente.pullEvents();

        cliente.removeSupervisor(persona);

        assertThat(cliente.getSupervisores()).isEmpty();
        assertThat(cliente.pullEvents()).singleElement()
                .isInstanceOfSatisfying(SupervisorRemovedEvent.class, evento ->
                        assertThat(evento.payload()).isEqualTo(new SupervisorPayload(persona, null)));

        cliente.removeSupervisor(persona);
        assertThat(cliente.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("actualizar sin cambios no anuncia un cambio que no ocurrio")
    void actualizarSinCambios() {
        Client cliente = unCliente();
        cliente.pullEvents();

        cliente.update("Hospital Central", null);

        assertThat(cliente.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("retirar deja el cliente inactivo, y retirar de nuevo no repite el hecho")
    void retirar() {
        Client cliente = unCliente();
        cliente.pullEvents();

        cliente.deactivate();

        assertThat(cliente.isEstadoActivo()).isFalse();
        assertThat(cliente.pullEvents()).singleElement().isInstanceOf(ClientDeactivatedEvent.class);

        cliente.deactivate();
        assertThat(cliente.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("las colecciones entregadas son inmutables")
    void coleccionesInmutables() {
        Client cliente = unCliente();
        cliente.addEmail("contacto@hospital.com");

        assertThatThrownBy(() -> cliente.getCorreos().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> cliente.getSupervisores().add(new ClientSupervisor(UUID.randomUUID(), null)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("rehidratar no registra nada, y la igualdad es por identidad")
    void rehidratar() {
        UUID id = UUID.randomUUID();
        Client uno = Client.rehydrate(id, "900123456", IdentificationType.CC, "Uno", "CLI", null, true,
                List.of(), List.of(), List.of());
        Client otro = Client.rehydrate(id, "900123456", IdentificationType.CC, "Otro", "CLI", null, false,
                List.of(), List.of(), List.of());

        assertThat(uno.hasPendingEvents()).isFalse();
        assertThat(uno).isEqualTo(otro).hasSameHashCodeAs(otro);
    }
}
