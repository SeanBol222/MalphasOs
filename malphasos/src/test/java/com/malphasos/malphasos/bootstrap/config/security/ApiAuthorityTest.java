package com.malphasos.malphasos.bootstrap.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * La regla de expansión del administrador, aislada de Spring.
 *
 * <p>Es el único sitio donde se decide quién manda, de modo que un descuido aquí no rompe un
 * endpoint sino los ochenta y tres. Las pruebas se centran en tres cosas: que la cadena
 * {@code super.admin.full → admin.full → recursos} funcione entera, que no funcione al revés, y
 * que la pertenencia se decida por igualdad exacta y no por prefijo.
 */
class ApiAuthorityTest {

    /** Las autoridades de recurso, las que solo concede el super usuario, y las dos que mandan. */
    private static final int VOCABULARIO_COMPLETO =
            ApiAuthority.RESOURCE_AUTHORITIES.size() + ApiAuthority.SUPER_AUTHORITIES.size() + 2;

    @Test
    @DisplayName("admin.full a solas concede las diecisiete autoridades de recurso")
    void adminFullConcedeTodosLosRecursos() {
        Set<String> concedidas = ApiAuthority.expand(List.of(ApiAuthority.ADMIN_FULL));

        assertThat(concedidas)
                .containsAll(ApiAuthority.RESOURCE_AUTHORITIES)
                .contains(ApiAuthority.ADMIN_FULL)
                .hasSize(ApiAuthority.RESOURCE_AUTHORITIES.size() + 1);
    }

    @Test
    @DisplayName("super.admin.full a solas concede admin.full y, a traves de el, los recursos")
    void superAdminFullRecorreLaCadenaEntera() {
        // Es el caso que delata la rotura del primer eslabon: si expand dejara de anadir
        // admin.full, un token que solo trae super.admin.full se quedaria sin nada y ninguna
        // prueba que parta de admin.full lo notaria.
        Set<String> concedidas = ApiAuthority.expand(List.of(ApiAuthority.SUPER_ADMIN_FULL));

        assertThat(concedidas)
                .containsAll(ApiAuthority.RESOURCE_AUTHORITIES)
                .containsAll(ApiAuthority.SUPER_AUTHORITIES)
                .contains(ApiAuthority.ADMIN_FULL, ApiAuthority.SUPER_ADMIN_FULL)
                .hasSize(VOCABULARIO_COMPLETO);
    }

    @Test
    @DisplayName("admin.full NO concede lo que solo es del super usuario")
    void adminFullNoAlcanzaElEscalonDeArriba() {
        // El escalon entero se sostiene sobre esta ausencia. Hasta el 2026-09-13 los dos roles
        // concedian lo mismo y esta prueba habria sido imposible de escribir.
        Set<String> concedidas = ApiAuthority.expand(List.of(ApiAuthority.ADMIN_FULL));

        assertThat(concedidas).doesNotContainAnyElementsOf(ApiAuthority.SUPER_AUTHORITIES);
    }

    @Test
    @DisplayName("ninguna autoridad de super esta entre las de recurso")
    void losDosConjuntosNoSeTocan() {
        // Es el invariante estructural que protege al de arriba. Anadir super.person.write a
        // RESOURCE_AUTHORITIES parece lo correcto -es una autoridad mas- y devolveria al
        // administrador justo lo que se le acaba de quitar, sin que nada fallara.
        assertThat(ApiAuthority.RESOURCE_AUTHORITIES)
                .doesNotContainAnyElementsOf(ApiAuthority.SUPER_AUTHORITIES);
    }

    @Test
    @DisplayName("el prefijo super. es el que marca el escalon, y se cumple")
    void elPrefijoDiceLaVerdad() {
        // La regla se lee en los nombres sin abrir el codigo. Si alguien anadiera al conjunto una
        // autoridad sin ese prefijo, el nombre dejaria de decir donde vive.
        assertThat(ApiAuthority.SUPER_AUTHORITIES).allSatisfy(a -> assertThat(a).startsWith("super."));
        assertThat(ApiAuthority.RESOURCE_AUTHORITIES).noneMatch(a -> a.startsWith("super."));
    }

    @Test
    @DisplayName("un rol desconocido se conserva y no estorba a la expansion")
    void elRolDesconocidoSeConserva() {
        // expand decide que anade, nunca que quita: filtrar en silencio un rol recien creado en el
        // realm seria un fallo dificil de diagnosticar desde el lado del cliente.
        Set<String> concedidas = ApiAuthority.expand(List.of("rol.inventado", ApiAuthority.ADMIN_FULL));

        assertThat(concedidas)
                .contains("rol.inventado")
                .containsAll(ApiAuthority.RESOURCE_AUTHORITIES);
    }

    @Test
    @DisplayName("tener las diecisiete autoridades no convierte a nadie en administrador")
    void laExpansionNoFuncionaAlReves() {
        Set<String> concedidas = ApiAuthority.expand(ApiAuthority.RESOURCE_AUTHORITIES);

        assertThat(concedidas)
                .describedAs("admin.full es quien manda, no la suma de lo que concede")
                .doesNotContain(ApiAuthority.ADMIN_FULL, ApiAuthority.SUPER_ADMIN_FULL);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "admin.fullish",
                "admin.ful",
                "super.admin",
                "super.admin.fullish",
                "ADMIN.FULL",
                "Admin.Full",
                "admin.full ",
                " admin.full",
                "xadmin.full"
            })
    @DisplayName("un rol que solo se parece a admin.full no concede nada")
    void elParecidoNoBasta(String casiAdmin) {
        // La pertenencia se decide por igualdad exacta sobre el conjunto. Un refactor que la
        // sustituyera por startsWith, endsWith o una comparacion sin distinguir mayusculas abriria
        // el sistema entero a un rol que cualquiera puede crear en el realm.
        Set<String> concedidas = ApiAuthority.expand(List.of(casiAdmin));

        assertThat(concedidas).containsExactly(casiAdmin);
    }

    @Test
    @DisplayName("sin roles no se concede ninguna autoridad")
    void sinRolesNoHayAutoridades() {
        assertThat(ApiAuthority.expand(List.of())).isEmpty();
    }

    @Test
    @DisplayName("expand no modifica la coleccion que recibe")
    void expandNoTocaLaEntrada() {
        List<String> entrada = new ArrayList<>(List.of(ApiAuthority.ADMIN_FULL));

        ApiAuthority.expand(entrada);

        assertThat(entrada).containsExactly(ApiAuthority.ADMIN_FULL);
    }

    @Test
    @DisplayName("expand acepta una coleccion inmutable")
    void expandAceptaColeccionInmutable() {
        // El converter le pasa el resultado de un stream().toList(), que es inmutable. Si expand
        // llegara a escribir sobre su entrada, fallaria en produccion y no aqui.
        assertThatCode(() -> ApiAuthority.expand(List.of(ApiAuthority.SUPER_ADMIN_FULL)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el catalogo de recursos no se incluye a si mismo a quien lo concede")
    void elCatalogoNoContieneAlAdministrador() {
        assertThat(ApiAuthority.RESOURCE_AUTHORITIES)
                .doesNotContain(ApiAuthority.ADMIN_FULL, ApiAuthority.SUPER_ADMIN_FULL)
                .hasSize(17);
    }

    @Test
    @DisplayName("el catalogo de recursos es inmutable")
    void elCatalogoEsInmutable() {
        assertThatThrownBy(() -> ApiAuthority.RESOURCE_AUTHORITIES.add("colado"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("la expansion produce siempre el mismo orden")
    void elOrdenEsEstable() {
        // El javadoc promete orden estable para que dos volcados de autoridades sean comparables.
        // Un HashSet en lugar de un LinkedHashSet lo rompe sin que nada mas se entere.
        assertThat(ApiAuthority.expand(List.of(ApiAuthority.ADMIN_FULL)))
                .containsExactlyElementsOf(ApiAuthority.expand(List.of(ApiAuthority.ADMIN_FULL)));
    }
}
