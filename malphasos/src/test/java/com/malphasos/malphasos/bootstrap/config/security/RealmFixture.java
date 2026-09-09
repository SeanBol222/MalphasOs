package com.malphasos.malphasos.bootstrap.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lee el realm de desarrollo que se importa en Keycloak para que las pruebas puedan afirmar cosas
 * sobre él sin copiarlo a mano.
 *
 * <p>El realm es la otra mitad del modelo de permisos: la aplicación decide qué exige cada
 * operación, pero quién trae cada autoridad lo decide Keycloak. Una prueba que repitiera aquí la
 * lista de roles de un grupo dejaría de decir nada en cuanto el realm cambiara, así que se lee del
 * archivo de verdad.
 *
 * <p>El archivo vive fuera del módulo, en {@code docker/}, que es territorio de solo lectura. Se
 * localiza subiendo desde el directorio de trabajo, porque ese directorio no es el mismo cuando la
 * batería se lanza desde la raíz del repositorio que cuando se lanza desde el módulo.
 */
final class RealmFixture {

    /** Client de Keycloak que representa a esta API. */
    static final String CLIENT_API = "malphasos-api";

    private static final String RUTA_RELATIVA = "docker/keycloak/import/malphasos-realm-realm.json";

    private static final JsonNode REALM = leerRealm();

    private RealmFixture() {}

    private static JsonNode leerRealm() {
        Path directorio = Path.of("").toAbsolutePath();

        while (directorio != null) {
            Path candidato = directorio.resolve(RUTA_RELATIVA);
            if (Files.isRegularFile(candidato)) {
                return JsonMapper.builder().build().readTree(candidato.toFile());
            }
            directorio = directorio.getParent();
        }

        throw new IllegalStateException(
                "No se encontro " + RUTA_RELATIVA + " subiendo desde " + Path.of("").toAbsolutePath());
    }

    /** Nombres de los roles que el realm define sobre el client de esta API. */
    static Set<String> rolesDelClientApi() {
        JsonNode roles = REALM.path("roles").path("client").path(CLIENT_API);
        assertThat(roles.isArray())
                .describedAs("El realm debe definir roles para el client " + CLIENT_API)
                .isTrue();

        Set<String> nombres = new LinkedHashSet<>();
        roles.forEach(rol -> nombres.add(rol.path("name").asString()));

        return nombres;
    }

    /** Roles del client de esta API que el realm asigna a un grupo. */
    static Set<String> rolesDelGrupo(String grupo) {
        for (JsonNode nodo : REALM.path("groups")) {
            if (grupo.equals(nodo.path("name").asString())) {
                Set<String> nombres = new LinkedHashSet<>();
                nodo.path("clientRoles").path(CLIENT_API).forEach(rol -> nombres.add(rol.asString()));

                return nombres;
            }
        }

        throw new IllegalStateException("El realm no define el grupo " + grupo);
    }

    /** Nombres de los grupos que el realm declara. */
    static Set<String> grupos() {
        Set<String> nombres = new LinkedHashSet<>();
        REALM.path("groups").forEach(grupo -> nombres.add(grupo.path("name").asString()));

        return nombres;
    }
}
