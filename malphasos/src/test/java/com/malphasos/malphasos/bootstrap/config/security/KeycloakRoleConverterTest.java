package com.malphasos.malphasos.bootstrap.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * El converter como unidad, sin Spring ni servidor de autorización.
 *
 * <p>El token es una entrada externa: quien lo emite puede cambiar de versión, y un claim con la
 * forma que no se esperaba no debe tumbar la petición con un 500 sino dejarla sin autoridades, que
 * acaba en 403. Aquí se le dan al converter todas las formas rotas que se le ocurren a uno y se
 * exige que ninguna lance.
 *
 * <p>Es además el único sitio donde la expansión de {@link ApiAuthority} se ejercita por la vía
 * real: el post-procesador {@code jwt().authorities(...)} de MockMvc fija las autoridades a mano y
 * nunca pasa por aquí.
 */
class KeycloakRoleConverterTest {

    private static final String CLIENT_ID = "malphasos-api";

    private final KeycloakRoleConverter converter = new KeycloakRoleConverter(CLIENT_ID);

    /** Un token mínimo al que se le añade lo que cada caso quiera probar. */
    private static Jwt tokenCon(Consumer<Jwt.Builder> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("sub", "usuario");

        claims.accept(builder);

        return builder.build();
    }

    private static List<String> nombresDe(Collection<GrantedAuthority> autoridades) {
        return autoridades.stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("un token sin resource_access no concede autoridades y no lanza")
    void sinResourceAccess() {
        assertThat(converter.convert(tokenCon(builder -> {}))).isEmpty();
    }

    @Test
    @DisplayName("un resource_access que no es un mapa no concede autoridades y no lanza")
    void resourceAccessQueNoEsMapa() {
        assertThat(converter.convert(tokenCon(b -> b.claim("resource_access", "no soy un mapa"))))
                .isEmpty();
    }

    @Test
    @DisplayName("un resource_access sin el client de esta API no concede autoridades")
    void sinElClientDeEstaApi() {
        Jwt token = tokenCon(b ->
                b.claim("resource_access", Map.of("otro-client", Map.of("roles", List.of("admin.full")))));

        assertThat(converter.convert(token)).isEmpty();
    }

    @Test
    @DisplayName("el client presente pero sin roles no concede autoridades y no lanza")
    void clientSinRoles() {
        Jwt token = tokenCon(b -> b.claim("resource_access", Map.of(CLIENT_ID, Map.of("otraCosa", "x"))));

        assertThat(converter.convert(token)).isEmpty();
    }

    @Test
    @DisplayName("un roles que no es una coleccion no concede autoridades y no lanza")
    void rolesQueNoEsColeccion() {
        Jwt token = tokenCon(b -> b.claim("resource_access", Map.of(CLIENT_ID, Map.of("roles", "admin.full"))));

        assertThat(converter.convert(token)).isEmpty();
    }

    @Test
    @DisplayName("de un roles con elementos que no son texto se toman solo los que si lo son")
    void rolesConElementosQueNoSonTexto() {
        List<Object> mezcla = java.util.Arrays.asList(42, "client.read", null, Map.of("a", "b"), "person.read");
        Jwt token = tokenCon(b -> b.claim("resource_access", Map.of(CLIENT_ID, Map.of("roles", mezcla))));

        assertThat(nombresDe(converter.convert(token)))
                .containsExactlyInAnyOrder("client.read", "person.read");
    }

    @Test
    @DisplayName("un roles vacio no concede autoridades")
    void rolesVacio() {
        Jwt token = tokenCon(b -> b.claim("resource_access", Map.of(CLIENT_ID, Map.of("roles", List.of()))));

        assertThat(converter.convert(token)).isEmpty();
    }

    @Test
    @DisplayName("los roles de recurso se convierten tal cual, sin anadir ni quitar")
    void rolesDeRecursoSeConviertenTalCual() {
        Jwt token = tokenCon(b -> b.claim(
                "resource_access", Map.of(CLIENT_ID, Map.of("roles", List.of("client.read", "equipment.write")))));

        assertThat(nombresDe(converter.convert(token)))
                .containsExactlyInAnyOrder("client.read", "equipment.write");
    }

    @Test
    @DisplayName("admin.full en el token sale del converter ya expandido")
    void adminFullSaleExpandido() {
        Jwt token = tokenCon(
                b -> b.claim("resource_access", Map.of(CLIENT_ID, Map.of("roles", List.of("admin.full")))));

        assertThat(nombresDe(converter.convert(token)))
                .containsAll(ApiAuthority.RESOURCE_AUTHORITIES)
                .contains(ApiAuthority.ADMIN_FULL);
    }

    @Test
    @DisplayName("super.admin.full en el token sale del converter con la cadena entera")
    void superAdminFullSaleExpandido() {
        Jwt token = tokenCon(
                b -> b.claim("resource_access", Map.of(CLIENT_ID, Map.of("roles", List.of("super.admin.full")))));

        assertThat(nombresDe(converter.convert(token)))
                .containsAll(ApiAuthority.RESOURCE_AUTHORITIES)
                .contains(ApiAuthority.ADMIN_FULL, ApiAuthority.SUPER_ADMIN_FULL);
    }

    @Test
    @DisplayName("un roles con el mismo rol repetido no duplica autoridades")
    void rolesRepetidosNoSeDuplican() {
        // El realm no deberia emitirlos repetidos, pero el converter no lo controla y una autoridad
        // duplicada en el contexto de seguridad es un sintoma que confunde al depurar.
        Jwt token = tokenCon(b -> b.claim(
                "resource_access", Map.of(CLIENT_ID, Map.of("roles", List.of("client.read", "client.read")))));

        assertThat(nombresDe(converter.convert(token))).containsExactly("client.read");
    }
}
