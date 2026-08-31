package com.malphasos.malphasos.client.domain.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.malphasos.malphasos.client.domain.client.events.ClientCreatedEvent;
import com.malphasos.malphasos.client.domain.client.events.ClientDeactivatedEvent;
import com.malphasos.malphasos.client.domain.client.events.LegalRepresentativeAppointedEvent;
import com.malphasos.malphasos.client.domain.client.events.LegalRepresentativePayload;
import com.malphasos.malphasos.client.domain.client.events.LegalRepresentativeRemovedEvent;
import java.util.List;
import java.util.Set;
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
        assertThat(cliente.getRepresentantes()).isNotNull().isEmpty();
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
    @DisplayName("nombrar representante legal registra el hecho con la persona en el payload")
    void nombrarRepresentante() {
        Client cliente = unCliente();
        cliente.pullEvents();
        UUID persona = UUID.randomUUID();

        cliente.appointRepresentative(persona);

        assertThat(cliente.getRepresentantes()).containsExactly(persona);
        assertThat(cliente.pullEvents()).singleElement()
                .isInstanceOfSatisfying(LegalRepresentativeAppointedEvent.class, evento ->
                        assertThat(evento.payload()).isEqualTo(new LegalRepresentativePayload(persona)));
    }

    @Test
    @DisplayName("un cliente admite varios representantes legales")
    void variosRepresentantes() {
        Client cliente = unCliente();
        cliente.appointRepresentative(UUID.randomUUID());
        cliente.appointRepresentative(UUID.randomUUID());

        assertThat(cliente.getRepresentantes()).hasSize(2);
    }

    @Test
    @DisplayName("nombrar dos veces a la misma persona no repite el hecho")
    void nombrarDosVeces() {
        Client cliente = unCliente();
        UUID persona = UUID.randomUUID();
        cliente.appointRepresentative(persona);
        cliente.pullEvents();

        cliente.appointRepresentative(persona);

        assertThat(cliente.getRepresentantes()).hasSize(1);
        assertThat(cliente.pullEvents()).isEmpty();
    }

    @Test
    @DisplayName("retirar un representante registra el hecho; retirar a quien no lo es no hace nada")
    void retirarRepresentante() {
        Client cliente = unCliente();
        UUID persona = UUID.randomUUID();
        cliente.appointRepresentative(persona);
        cliente.pullEvents();

        cliente.removeRepresentative(persona);

        assertThat(cliente.getRepresentantes()).isEmpty();
        assertThat(cliente.pullEvents()).singleElement()
                .isInstanceOf(LegalRepresentativeRemovedEvent.class);

        cliente.removeRepresentative(persona);
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
        assertThatThrownBy(() -> cliente.getRepresentantes().add(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("rehidratar no registra nada, y la igualdad es por identidad")
    void rehidratar() {
        UUID id = UUID.randomUUID();
        Client uno = Client.rehydrate(id, "900123456", IdentificationType.CC, "Uno", null, true,
                List.of(), List.of(), Set.of());
        Client otro = Client.rehydrate(id, "900123456", IdentificationType.CC, "Otro", null, false,
                List.of(), List.of(), Set.of());

        assertThat(uno.hasPendingEvents()).isFalse();
        assertThat(uno).isEqualTo(otro).hasSameHashCodeAs(otro);
    }
}
