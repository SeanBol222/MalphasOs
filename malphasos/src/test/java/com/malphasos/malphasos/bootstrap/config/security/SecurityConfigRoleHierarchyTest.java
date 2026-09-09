package com.malphasos.malphasos.bootstrap.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * La jerarquía que declara {@link SecurityConfig}, comprobada sobre el bean y no sobre la cadena
 * de filtros.
 *
 * <p>La jerarquía y la expansión del converter dicen lo mismo por caminos distintos: el converter
 * solo interviene cuando la autenticación nace de un JWT que atraviesa la cadena, y la jerarquía
 * cubre cualquier otra procedencia. Que ambas se deriven de la misma constante no garantiza que
 * ambas la traduzcan bien, y esta clase verifica la segunda traducción.
 */
class SecurityConfigRoleHierarchyTest {

    private final RoleHierarchy jerarquia = new SecurityConfig("malphasos-api").roleHierarchy();

    private List<String> alcanzablesDesde(String... autoridades) {
        Collection<? extends GrantedAuthority> entrada =
                java.util.Arrays.stream(autoridades).map(SimpleGrantedAuthority::new).toList();

        return jerarquia.getReachableGrantedAuthorities(entrada).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    @Test
    @DisplayName("desde admin.full se alcanzan las diecisiete autoridades de recurso")
    void adminFullAlcanzaLosRecursos() {
        assertThat(alcanzablesDesde(ApiAuthority.ADMIN_FULL))
                .containsAll(ApiAuthority.RESOURCE_AUTHORITIES)
                .contains(ApiAuthority.ADMIN_FULL);
    }

    @Test
    @DisplayName("desde super.admin.full se alcanza admin.full y, por el, todos los recursos")
    void superAdminFullAlcanzaLaCadenaEntera() {
        assertThat(alcanzablesDesde(ApiAuthority.SUPER_ADMIN_FULL))
                .containsAll(ApiAuthority.RESOURCE_AUTHORITIES)
                .contains(ApiAuthority.ADMIN_FULL, ApiAuthority.SUPER_ADMIN_FULL);
    }

    @Test
    @DisplayName("desde las autoridades de recurso no se alcanza admin.full")
    void losRecursosNoAlcanzanAlAdministrador() {
        assertThat(alcanzablesDesde(ApiAuthority.RESOURCE_AUTHORITIES.toArray(String[]::new)))
                .doesNotContain(ApiAuthority.ADMIN_FULL, ApiAuthority.SUPER_ADMIN_FULL);
    }

    @Test
    @DisplayName("desde una autoridad de recurso no se alcanza ninguna otra")
    void unRecursoNoAbreOtroRecurso() {
        assertThat(alcanzablesDesde(ApiAuthority.CLIENT_READ)).containsExactly(ApiAuthority.CLIENT_READ);
    }

    @Test
    @DisplayName("una autoridad ajena a la jerarquia se conserva")
    void laAutoridadAjenaSeConserva() {
        assertThat(alcanzablesDesde("rol.inventado")).contains("rol.inventado");
    }

    @Test
    @DisplayName("la jerarquia no usa el prefijo ROLE_ de Spring Security")
    void sinPrefijoRole() {
        // Los permisos llegan de Keycloak con su propio nombre y se comprueban con hasAuthority.
        // Un ROLE_ colado obligaria a escribir hasRole en las anotaciones y partiria el modelo en
        // dos convenciones.
        assertThat(alcanzablesDesde(ApiAuthority.ADMIN_FULL)).noneMatch(a -> a.startsWith("ROLE_"));
    }
}
