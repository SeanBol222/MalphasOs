package com.malphasos.malphasos.person.infrastructure.input.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.bootstrap.config.security.ApiAuthority;
import com.malphasos.malphasos.person.application.ports.input.PersonServicePort;
import com.malphasos.malphasos.person.domain.person.Person;
import com.malphasos.malphasos.person.domain.person.PersonType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * La escalera de usuarios, probada donde se decide.
 *
 * <p><b>No existía ninguna prueba de esta clase</b> hasta el 2026-10-04, aunque sostiene la regla de
 * quién puede escribir sobre quién: la cubría de refilón {@code SecurityIntegrationTest}, que
 * comprueba los códigos HTTP de las rutas y no las ramas de esta decisión.
 */
@ExtendWith(MockitoExtension.class)
class PersonWriteGuardTest {

    @Mock private PersonServicePort personServicePort;

    @InjectMocks private PersonWriteGuard guard;

    private Authentication con(String... autoridades) {
        Authentication autenticacion = new TestingAuthenticationToken(
                "quien", "sea", List.of(autoridades).stream().map(SimpleGrantedAuthority::new).toList());
        autenticacion.setAuthenticated(true);

        return autenticacion;
    }

    private UUID alguienDeTipo(PersonType tipo) {
        UUID id = UUID.randomUUID();
        Person persona = Person.builder().identificador(id).tipoPersona(tipo).build();
        when(personServicePort.findById(id)).thenReturn(persona);

        return id;
    }

    @Test
    @DisplayName("escribir sobre gente de la casa exige super.person.write")
    void laGenteDeLaCasaExigeElEscalonDeArriba() {
        UUID ingeniero = alguienDeTipo(PersonType.ENGINEER);

        assertThat(guard.canWrite(ingeniero, con(ApiAuthority.PERSON_WRITE))).isFalse();
        assertThat(guard.canWrite(ingeniero, con(ApiAuthority.SUPER_PERSON_WRITE))).isTrue();
    }

    @Test
    @DisplayName("escribir sobre gente del cliente exige person.write")
    void laGenteDelClienteExigeElEscalonDeAbajo() {
        UUID representante = alguienDeTipo(PersonType.CEO_CLIENT);

        assertThat(guard.canWrite(representante, con(ApiAuthority.PERSON_WRITE))).isTrue();
    }

    @Test
    @DisplayName("sin autenticar no se escribe sobre nadie")
    void sinAutenticarNo() {
        UUID representante = alguienDeTipo(PersonType.CEO_CLIENT);

        assertThat(guard.canWrite(representante, null)).isFalse();
    }

    // ------------------------------------------------------------------------
    // El agujero: la escalera mira a quien se toca y no en que se le convierte
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("promover a un representante a ADMIN exige el mismo escalon que crear un ADMIN")
    void noSePuedePromoverSaltandoseLaEscalera() {
        // Crear un administrador exige super.person.write: lo dice POST /persons/admins. Editar a un
        // representante exige solo person.write, porque la escalera mira el tipo que la persona TIENE.
        // Si ademas se le puede cambiar el tipo en la misma peticion, person.write alcanza para
        // fabricar un ADMIN, y el escalon de arriba deja de significar nada.
        UUID representante = alguienDeTipo(PersonType.CEO_CLIENT);

        assertThat(guard.canUpdate(representante, PersonType.ADMIN, con(ApiAuthority.PERSON_WRITE)))
                .isFalse();
    }

    @Test
    @DisplayName("promover a SUPER_ADMIN tampoco, y es el caso que mas importa")
    void tampocoASuperAdmin() {
        UUID encargado = alguienDeTipo(PersonType.MANAGER);

        assertThat(guard.canUpdate(encargado, PersonType.SUPER_ADMIN, con(ApiAuthority.PERSON_WRITE)))
                .isFalse();
    }

    @Test
    @DisplayName("quien tiene el escalon de arriba si puede promover")
    void conElEscalonDeArribaSiSePromueve() {
        UUID representante = alguienDeTipo(PersonType.CEO_CLIENT);

        assertThat(guard.canUpdate(
                        representante,
                        PersonType.ADMIN,
                        con(ApiAuthority.PERSON_WRITE, ApiAuthority.SUPER_PERSON_WRITE)))
                .isTrue();
    }

    @Test
    @DisplayName("degradar a un ingeniero a representante exige el escalon de arriba, no el de abajo")
    void degradarTambienExigeElEscalonDeArriba() {
        // El tipo de destino es de los de abajo, pero el de origen no: tocar a un ingeniero es tocar
        // a gente de la casa, y eso no lo abarata el destino.
        UUID ingeniero = alguienDeTipo(PersonType.ENGINEER);

        assertThat(guard.canUpdate(ingeniero, PersonType.CEO_CLIENT, con(ApiAuthority.PERSON_WRITE)))
                .isFalse();
    }

    @Test
    @DisplayName("un tipo nulo significa que no se pide cambio, no que se prohiba")
    void unTipoNuloEsNoCambiar() {
        // Esta rama la destapo una mutacion que sobrevivio: tratar el nulo como prohibido no rompia
        // nada, porque hoy tipoPersona es @NotNull y la validacion del cuerpo corre ANTES que
        // @PreAuthorize, de modo que el nulo no llega nunca por la ruta de hoy.
        //
        // Se queda, y con prueba, porque es el contrato de la guarda y no un detalle del DTO: nulo
        // quiere decir «no se pide cambio de tipo». El dia que esta ruta pase a PATCH —que es lo que
        // la convencion del proyecto exige— una edicion parcial sin tipo sera lo normal, y tratarla
        // como prohibida dejaria sin editar a quien si le toca.
        UUID representante = alguienDeTipo(PersonType.CEO_CLIENT);

        assertThat(guard.canUpdate(representante, null, con(ApiAuthority.PERSON_WRITE))).isTrue();
    }

    @Test
    @DisplayName("editar sin cambiar de tipo sigue exigiendo lo de siempre")
    void editarSinCambiarDeTipo() {
        UUID representante = alguienDeTipo(PersonType.CEO_CLIENT);

        assertThat(guard.canUpdate(representante, PersonType.CEO_CLIENT, con(ApiAuthority.PERSON_WRITE)))
                .isTrue();
    }
}
