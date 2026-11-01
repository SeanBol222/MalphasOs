package com.malphasos.malphasos.person.application.services.person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.person.application.ports.output.PersonIdentityPort;
import com.malphasos.malphasos.person.application.ports.output.PersonPersistencePort;
import com.malphasos.malphasos.person.domain.person.EmailPerson;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.domain.person.PersonType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** La conciliación del correo de la cuenta en las fichas anteriores a V40. */
@ExtendWith(MockitoExtension.class)
class PrimaryEmailReconciliationTest {

    @Mock private PersonPersistencePort persistencePort;
    @Mock private PersonIdentityPort identityPort;

    private PrimaryEmailReconciliation conciliacion() {
        return new PrimaryEmailReconciliation(persistencePort, identityPort);
    }

    private static Person persona(PersonType tipo, String... correos) {
        Person persona = Person.builder()
                .identificador(UUID.randomUUID())
                .tipoPersona(tipo)
                .estadoActivo(true)
                .build();
        for (String correo : correos) {
            persona.addEmail(EmailPerson.builder().idCorreoPersona(UUID.randomUUID())
                    .correoPersona(correo).estadoActivo(true).build());
        }
        return persona;
    }

    @Test
    @DisplayName("marca el correo de la ficha que coincide con el de la cuenta, sin distinguir mayúsculas")
    void marcaElQueCoincide() {
        Person ada = persona(PersonType.ENGINEER, "otro@x.local", "ada@x.local");
        when(persistencePort.findAll()).thenReturn(List.of(ada));
        when(identityPort.findUserEmail(ada.getIdentificador().toString())).thenReturn(Optional.of("ADA@x.local"));

        assertThat(conciliacion().reconcile()).isEqualTo(1);

        assertThat(ada.primaryEmail()).get().extracting(EmailPerson::getCorreoPersona).isEqualTo("ada@x.local");
        assertThat(ada.getEmailPersonList()).hasSize(2);
        verify(persistencePort).save(ada);
    }

    @Test
    @DisplayName("si el correo de la cuenta no está en la ficha, lo añade y lo marca")
    void anadeElQueFalta() {
        // Decidido por el usuario: la cuenta es la que recibe los avisos, y la ficha la reconoce.
        Person ada = persona(PersonType.SUPERVISOR, "otro@x.local");
        when(persistencePort.findAll()).thenReturn(List.of(ada));
        when(identityPort.findUserEmail(ada.getIdentificador().toString())).thenReturn(Optional.of("cuenta@x.local"));

        conciliacion().reconcile();

        assertThat(ada.getEmailPersonList()).hasSize(2);
        assertThat(ada.primaryEmail()).get().extracting(EmailPerson::getCorreoPersona).isEqualTo("cuenta@x.local");
        verify(persistencePort).save(ada);
    }

    @Test
    @DisplayName("no pregunta por quien ya tiene principal ni por los retirados")
    void soloLasQueLoNecesitan() {
        Person conPrincipal = persona(PersonType.ENGINEER, "ada@x.local");
        conPrincipal.getEmailPersonList().getFirst().setPrincipal(true);
        Person retirada = persona(PersonType.ENGINEER, "r@x.local").setEstadoActivo(false);
        when(persistencePort.findAll()).thenReturn(List.of(conPrincipal, retirada));

        assertThat(conciliacion().reconcile()).isZero();

        verify(identityPort, never()).findUserEmail(any());
        verify(persistencePort, never()).save(any());
    }

    @Test
    @DisplayName("sin cuenta en Keycloak no inventa nada")
    void sinCuenta() {
        Person ada = persona(PersonType.ENGINEER, "ada@x.local");
        when(persistencePort.findAll()).thenReturn(List.of(ada));
        when(identityPort.findUserEmail(ada.getIdentificador().toString())).thenReturn(Optional.empty());

        assertThat(conciliacion().reconcile()).isZero();

        assertThat(ada.primaryEmail()).isEmpty();
        verify(persistencePort, never()).save(any());
    }

    @Test
    @DisplayName("un correo de cuenta que no cabe en la ficha no se añade: se registra y se deja")
    void noCabe() {
        Person ada = persona(PersonType.ENGINEER, "ada@x.local");
        String largo = "a".repeat(45) + "@x.local";
        when(persistencePort.findAll()).thenReturn(List.of(ada));
        when(identityPort.findUserEmail(ada.getIdentificador().toString())).thenReturn(Optional.of(largo));

        assertThat(conciliacion().reconcile()).isZero();

        verify(persistencePort, never()).save(any());
    }
}
