package com.malphasos.malphasos.client.infrastructure.input.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.malphasos.malphasos.client.application.ports.input.ClientOwnershipPort;
import com.malphasos.malphasos.person.application.model.communication.PersonCommunicationResponse;
import com.malphasos.malphasos.person.application.ports.input.PersonCommunicationPort;
import com.malphasos.malphasos.person.domain.exception.PersonNotFoundException;
import com.malphasos.malphasos.person.domain.person.PersonType;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * La única pieza que convierte quién llama en un filtro de datos, de modo que cada una de sus ramas
 * decide qué ve alguien. Se prueban las cinco.
 */
@ExtendWith(MockitoExtension.class)
class ReadScopeResolverTest {

    @Mock private PersonCommunicationPort personCommunicationPort;
    @Mock private ClientOwnershipPort clientOwnershipPort;

    /** Con la seguridad activa, que es como corre en producción. */
    private ReadScopeResolver resolver;

    @BeforeEach
    void seguridadActiva() {
        resolver = new ReadScopeResolver(personCommunicationPort, clientOwnershipPort, true);
    }

    private Authentication conSub(String sub) {
        Jwt token = Jwt.withTokenValue("no-importa")
                .header("alg", "none")
                .claim("sub", sub)
                .build();

        // Con autoridades a proposito: el constructor de un solo argumento deja el token SIN
        // autenticar, y entonces el resolutor devuelve el alcance vacio antes de mirar nada. Spring
        // nunca construye asi el token de una peticion real.
        return new JwtAuthenticationToken(token, Set.of());
    }

    private PersonCommunicationResponse persona(UUID id, PersonType tipo) {
        return PersonCommunicationResponse.builder()
                .identificador(id)
                .tipoPersona(tipo)
                .estadoActivo(true)
                .build();
    }

    @Test
    @DisplayName("un representante legal queda acotado a los clientes que representa")
    void unRepresentanteVeSoloLoSuyo() {
        UUID yo = UUID.randomUUID();
        UUID miCliente = UUID.randomUUID();
        when(personCommunicationPort.findById(yo)).thenReturn(persona(yo, PersonType.CEO_CLIENT));
        when(clientOwnershipPort.clientsRepresentedBy(yo)).thenReturn(Set.of(miCliente));

        ReadScope alcance = resolver.scopeFor(conSub(yo.toString()));

        assertThat(alcance.coversEverything()).isFalse();
        assertThat(alcance.visibleClients()).containsExactly(miCliente);
    }

    @Test
    @DisplayName("un representante que no representa a nadie no ve ningun cliente")
    void unRepresentanteSinClientesNoVeNada() {
        UUID yo = UUID.randomUUID();
        when(personCommunicationPort.findById(yo)).thenReturn(persona(yo, PersonType.CEO_CLIENT));
        when(clientOwnershipPort.clientsRepresentedBy(yo)).thenReturn(Set.of());

        ReadScope alcance = resolver.scopeFor(conSub(yo.toString()));

        // Lo que importa es que no se confunda con «lo ve todo», que es el defecto que se corrige.
        assertThat(alcance.coversEverything()).isFalse();
        assertThat(alcance.covers(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("un ingeniero lo ve todo y no se le pregunta por sus clientes")
    void laGenteDeLaCasaLoVeTodo() {
        UUID yo = UUID.randomUUID();
        when(personCommunicationPort.findById(yo)).thenReturn(persona(yo, PersonType.ENGINEER));

        ReadScope alcance = resolver.scopeFor(conSub(yo.toString()));

        assertThat(alcance.coversEverything()).isTrue();
        // No basta con que el alcance salga libre: preguntar por los clientes de un ingeniero seria
        // una consulta inutil en cada lectura del API.
        verify(clientOwnershipPort, never()).clientsRepresentedBy(any());
    }

    @Test
    @DisplayName("una cuenta sin persona en la base lo ve todo: es el operador creado a mano")
    void unaCuentaSinPersonaLoVeTodo() {
        UUID yo = UUID.randomUUID();
        when(personCommunicationPort.findById(yo)).thenThrow(new PersonNotFoundException(yo.toString()));

        ReadScope alcance = resolver.scopeFor(conSub(yo.toString()));

        assertThat(alcance.coversEverything()).isTrue();
    }

    @Test
    @DisplayName("con la seguridad activa, sin autenticacion no se ve nada")
    void conSeguridadActivaSinAutenticacionNoSeVeNada() {
        // Esta rama no se alcanza: las 33 lecturas exigen autenticacion y ninguna es ruta publica.
        // Por eso mismo conviene que sea la cerrada, para que dejar de serlo no abra nada.
        assertThat(resolver.scopeFor(null).coversEverything()).isFalse();
        assertThat(resolver.scopeFor(null).covers(UUID.randomUUID())).isFalse();

        Authentication sinAutenticar = new TestingAuthenticationToken("quien", "sea");
        sinAutenticar.setAuthenticated(false);

        assertThat(resolver.scopeFor(sinAutenticar).coversEverything()).isFalse();
    }

    @Test
    @DisplayName("con la seguridad apagada no se filtra nada, porque no hay autorizacion que aplicar")
    void conSeguridadApagadaNoSeFiltra() {
        // app.security.enabled=false apaga la autorizacion entera, y el filtrado por dueno es
        // autorizacion. Con la rama cerrada tambien aqui, apagar la seguridad dejaba el API
        // devolviendo listas vacias en silencio: parecia que no habia datos, no que faltara permiso.
        ReadScopeResolver sinSeguridad =
                new ReadScopeResolver(personCommunicationPort, clientOwnershipPort, false);

        assertThat(sinSeguridad.scopeFor(null).coversEverything()).isTrue();
    }

    @Test
    @DisplayName("un sub que no es un identificador no acota a nadie, y no se consulta la base")
    void unSubQueNoEsUuidNoAcota() {
        // Es el caso de los post-procesadores de prueba, cuyo sub es «user», y el de cualquier token
        // cuyo principalClaimName no sea el sub. Dar alcance libre es la misma decision que con la
        // cuenta sin persona, y conviene que no acabe en una consulta con un identificador inventado.
        ReadScope alcance = resolver.scopeFor(conSub("user"));

        assertThat(alcance.coversEverything()).isTrue();
        verify(personCommunicationPort, never()).findById(any());
    }

    @Test
    @DisplayName("el identificador sale del claim sub, no del nombre del principal")
    void elIdentificadorSaleDelClaimSub() {
        UUID yo = UUID.randomUUID();
        Jwt token = Jwt.withTokenValue("no-importa")
                .header("alg", "none")
                .claim("sub", yo.toString())
                .claim("preferred_username", "ada")
                .build();
        // Un token cuyo nombre de principal es otro claim: si el resolutor leyera getName() se
        // quedaria con «ada», no encontraria a nadie y abriria la vista en silencio.
        Authentication autenticacion = new JwtAuthenticationToken(token, Set.of(), "ada");
        when(personCommunicationPort.findById(yo)).thenReturn(persona(yo, PersonType.CEO_CLIENT));
        when(clientOwnershipPort.clientsRepresentedBy(yo)).thenReturn(Set.of());

        resolver.scopeFor(autenticacion);

        verify(personCommunicationPort).findById(yo);
    }

    @Test
    @DisplayName("un representante que tambien es encargado sigue siendo representante")
    void elSegundoTipoNoCambiaElAlcance() {
        UUID yo = UUID.randomUUID();
        when(personCommunicationPort.findById(yo))
                .thenReturn(PersonCommunicationResponse.builder()
                        .identificador(yo)
                        .tipoPersona(PersonType.CEO_CLIENT)
                        .segundoTipoPersona(PersonType.MANAGER)
                        .estadoActivo(true)
                        .build());
        when(clientOwnershipPort.clientsRepresentedBy(yo)).thenReturn(Set.of());

        assertThat(resolver.scopeFor(conSub(yo.toString())).coversEverything()).isFalse();
    }
}
