package com.malphasos.malphasos.bootstrap.config.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.person.application.ports.input.PersonServicePort;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Comprueba que la protección de los endpoints funciona de verdad: sin token no se entra, y con un
 * token que no lleva el permiso exigido tampoco.
 *
 * <p>Es la única prueba que enciende la seguridad; las demás la dejan apagada para centrarse en su
 * propia capa. Aquí el decodificador de JWT se sustituye por un doble, de modo que no hace falta un
 * Keycloak en marcha: lo que se verifica es la cadena de filtros y la traducción de roles, no la
 * criptografía de la firma.
 *
 * <p>Los roles llegan en {@code resource_access.<client>.roles}, tal como los publica Keycloak. El
 * permiso {@code admin.full} es el que exigen todos los controladores, y en el realm original
 * estaba definido pero sin asignar a ningún grupo: cualquier usuario habría recibido 403 en todos
 * los endpoints.
 */
@SpringBootTest(properties = {"app.security.enabled=true", "app.security.client-id=malphasos-api"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityIntegrationTest {

    private static final String CLIENT_ID = "malphasos-api";

    @Autowired private MockMvc mockMvc;

    /** Sustituye al decodificador real para no depender de un emisor accesible. */
    @MockitoBean private JwtDecoder jwtDecoder;

    @MockitoBean private PersonServicePort personServicePort;

    /** Construye el claim con la forma exacta en que Keycloak publica los roles de un client. */
    private static Map<String, Object> resourceAccessWith(String... roles) {
        return Map.of(CLIENT_ID, Map.of("roles", List.of(roles)));
    }

    @Test
    @DisplayName("sin token, un endpoint protegido responde 401")
    void withoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/api/persons")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("con un token sin el permiso exigido, responde 403")
    void withoutRequiredRoleReturnsForbidden() throws Exception {
        mockMvc.perform(get("/v1/api/persons")
                        .with(jwt().jwt(j -> j.claim("resource_access", resourceAccessWith("client.read")))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("con admin.full, el endpoint responde 200")
    void withAdminFullReturnsOk() throws Exception {
        mockMvc.perform(get("/v1/api/persons")
                        .with(jwt().jwt(j -> j.claim("resource_access", resourceAccessWith("admin.full")))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "admin.full"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("los roles de otro client del realm no otorgan permiso")
    void rolesFromAnotherClientAreIgnored() throws Exception {
        Map<String, Object> deOtroClient = Map.of("otro-client", Map.of("roles", List.of("admin.full")));

        mockMvc.perform(get("/v1/api/persons")
                        .with(jwt().jwt(j -> j.claim("resource_access", deOtroClient))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("la documentacion del API es publica")
    void apiDocsArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs/person")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("el endpoint de salud es publico, para que el contenedor pueda consultarlo")
    void healthEndpointIsPublic() throws Exception {
        // Lo que importa aqui es que la seguridad no lo bloquee. El estado que reporte depende del
        // entorno: en pruebas no hay RabbitMQ, asi que responde 503 con toda razon. Que distinga
        // entre "el proceso vive" y "sus dependencias responden" es justamente el motivo de usar
        // Actuator en vez de una comprobacion de puerto abierto.
        int status = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(status)
                .describedAs("El endpoint de salud no debe exigir autenticacion")
                .isNotIn(401, 403);
    }
}
