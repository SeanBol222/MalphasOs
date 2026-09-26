package com.malphasos.malphasos.bootstrap.config.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.person.application.ports.input.PersonServicePort;
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
 * Comprueba que el navegador puede hablar con el API desde el origen del frontend.
 *
 * <p>Enciende la seguridad a propósito, porque el defecto que motiva esta clase <b>solo aparece con
 * la seguridad encendida</b>: el preflight es un {@code OPTIONS} y un {@code OPTIONS} no lleva
 * cabecera de autorización, de modo que caía en {@code anyRequest().authenticated()} y se rechazaba
 * con 401 antes de que la capa de CORS respondiera. El navegador no informa de ese 401: informa de
 * que falta {@code Access-Control-Allow-Origin}, que es lo que se vio.
 *
 * <p>Va aparte de {@link SecurityIntegrationTest} porque no comprueba lo mismo: allí se verifica
 * quién entra, aquí que el preflight <b>no pasa por esa comprobación</b>.
 *
 * <p><b>Verificada rompiendo la producción a propósito</b>, y las tres mutaciones dicen cosas
 * distintas:
 *
 * <ul>
 *   <li>Quitar {@code .cors(...)} de {@link SecurityConfig}: <b>las tres siguen en verde</b>. No es
 *       un fallo de la prueba: {@code HttpSecurityConfiguration.applyCorsIfAvailable} aplica CORS
 *       sola en cuanto existe el bean. Lo que esta prueba fija es el resultado, no el mecanismo.
 *   <li>Quitar el {@code @Configuration} de {@link CorsConfig} dejando {@code .cors(...)}: las tres
 *       caen, y el preflight responde <b>200 sin cabeceras</b>. Es el caso más engañoso, porque
 *       borra el 401 que era la única pista.
 *   <li>Quitar las dos cosas: <b>401</b>, exactamente el número que apareció en el navegador.
 * </ul>
 */
@SpringBootTest(properties = {"app.security.enabled=true", "app.security.client-id=malphasos-api"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CorsConfigTest {

    /** El origen que declara la configuración de pruebas, y el del frontend en desarrollo. */
    private static final String ORIGEN_PERMITIDO = "http://localhost:5173";

    @Autowired private MockMvc mockMvc;

    /** Sustituye al decodificador real: ninguna de estas pruebas emite un token. */
    @MockitoBean private JwtDecoder jwtDecoder;

    @MockitoBean private PersonServicePort personServicePort;

    @Test
    @DisplayName("el preflight del origen permitido se responde sin token")
    void elPreflightNoExigeToken() throws Exception {
        mockMvc
                .perform(
                        options("/v1/api/clients")
                                .header("Origin", ORIGEN_PERMITIDO)
                                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGEN_PERMITIDO));
    }

    @Test
    @DisplayName("el preflight declara los verbos y las cabeceras que el API acepta")
    void elPreflightDeclaraLoQueElApiAcepta() throws Exception {
        mockMvc
                .perform(
                        options("/v1/api/clients")
                                .header("Origin", ORIGEN_PERMITIDO)
                                .header("Access-Control-Request-Method", "POST")
                                .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                // Sin PATCH no habria ni una sola edicion: es el unico verbo de escritura parcial
                // que el API publica, por convencion propia.
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PATCH,PUT,DELETE"))
                .andExpect(header().string("Access-Control-Allow-Headers", "Authorization"))
                // Y no se piden cookies: el token va en la cabecera. Misma razon que CSRF apagado.
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    @DisplayName("un origen que nadie declaró no recibe permiso")
    void unOrigenAjenoNoPasa() throws Exception {
        // Lo que distingue a esta configuracion de un comodin. Si alguien pusiera "*" para salir del
        // paso, esta prueba caeria: es la que vigila que la puerta siga teniendo lista de invitados.
        mockMvc
                .perform(
                        options("/v1/api/clients")
                                .header("Origin", "http://atacante.example")
                                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
