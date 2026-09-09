package com.malphasos.malphasos.bootstrap.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import com.malphasos.malphasos.person.application.ports.input.PersonServicePort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Comprueba que la protección de los endpoints funciona de verdad: sin token no se entra, con un
 * token que no lleva el permiso exigido tampoco, y quien lleva el permiso de un recurso no entra en
 * los de otro.
 *
 * <p>Es la única prueba que enciende la seguridad; las demás la dejan apagada para centrarse en su
 * propia capa. Aquí el decodificador de JWT se sustituye por un doble, de modo que no hace falta un
 * Keycloak en marcha: lo que se verifica es la cadena de filtros y la traducción de roles, no la
 * criptografía de la firma.
 *
 * <p>Los roles llegan en {@code resource_access.<client>.roles}, tal como los publica Keycloak.
 * Salvo donde se diga lo contrario, los tokens se construyen con ese claim y <b>sin</b>
 * {@code .authorities(...)}: así la petición atraviesa {@link KeycloakRoleConverter} y la expansión
 * del administrador se ejercita de verdad. Fijar las autoridades a mano se salta el converter y solo
 * prueba la jerarquía declarada en {@link SecurityConfig}.
 *
 * <p>Cada operación exige únicamente la autoridad de su recurso. Antes todas exigían
 * {@code admin.full}, que el realm solo daba al grupo de administradores: ingenieros y clientes
 * autenticaban bien y recibían 403 en todo.
 */
@SpringBootTest(properties = {"app.security.enabled=true", "app.security.client-id=malphasos-api"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityIntegrationTest {

    private static final String CLIENT_ID = "malphasos-api";

    /** Identificador cualquiera: ninguna de estas pruebas depende de que el recurso exista. */
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    @Autowired private MockMvc mockMvc;

    /** Sustituye al decodificador real para no depender de un emisor accesible. */
    @MockitoBean private JwtDecoder jwtDecoder;

    @MockitoBean private PersonServicePort personServicePort;

    /** Construye el claim con la forma exacta en que Keycloak publica los roles de un client. */
    private static Map<String, Object> resourceAccessWith(String... roles) {
        return Map.of(CLIENT_ID, Map.of("roles", List.of(roles)));
    }

    private static Jwt tokenCon(Map<String, Object> resourceAccess) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("sub", "usuario")
                .claim("resource_access", resourceAccess)
                .build();
    }

    /**
     * El decodificador devuelve un token con los roles que la propia petición pidió, codificados en
     * el valor del bearer separados por tildes.
     *
     * <p>Es un rodeo, pero es el único que hace pasar la petición por la cadena de filtros de
     * verdad: {@code BearerTokenAuthenticationFilter} decodifica, y solo entonces se aplica el
     * {@link KeycloakRoleConverter} que configura {@link SecurityConfig}. Con el post-procesador
     * {@code jwt()} de MockMvc no ocurre: ese construye la autenticación ya hecha y la mete en el
     * contexto, de modo que el converter de la aplicación no llega a ejecutarse nunca y el claim
     * {@code resource_access} que se le ponga al token no lo lee nadie.
     */
    @BeforeEach
    void elDecodificadorDevuelveLosRolesQuePideLaPeticion() {
        when(jwtDecoder.decode(anyString())).thenAnswer(invocacion -> {
            String valor = invocacion.getArgument(0, String.class);
            String[] roles = valor.isEmpty() ? new String[0] : valor.split("~");

            return tokenCon(resourceAccessWith(roles));
        });
    }

    /**
     * Un token con esos roles y nada más, que atraviesa la cadena de filtros y, con ella, la
     * expansión del administrador.
     */
    private static RequestPostProcessor conRoles(String... roles) {
        return request -> {
            // La tilde separa porque el resolvedor de bearer de Spring solo admite el alfabeto
            // [a-zA-Z0-9-._~+/] en el valor del token: una coma le hace responder 401 antes de
            // llegar a decodificar nada.
            request.addHeader("Authorization", "Bearer " + String.join("~", roles));

            return request;
        };
    }

    /** Los roles que el realm asigna a un grupo, leídos del archivo que se importa en Keycloak. */
    private static RequestPostProcessor comoElGrupo(String grupo) {
        return conRoles(RealmFixture.rolesDelGrupo(grupo).toArray(String[]::new));
    }

    /**
     * La autorización dejó pasar la petición.
     *
     * <p>No se exige un código concreto a propósito. Lo que se prueba aquí es la autorización, no lo
     * que el servicio conteste: la mayoría de estas rutas apuntan a identificadores que no existen y
     * responden 404, y eso ya demuestra que la petición llegó al dominio. Lo que sí se descarta es
     * un 5xx, para que un error interno no se cuele haciéndose pasar por acceso concedido.
     */
    private void autorizado(MockHttpServletRequestBuilder peticion) throws Exception {
        int estado = mockMvc.perform(peticion).andReturn().getResponse().getStatus();

        assertThat(estado)
                .describedAs("La autoridad exigida por el endpoint deberia bastar")
                .isNotIn(401, 403)
                .isLessThan(500);
    }

    private void prohibido(MockHttpServletRequestBuilder peticion) throws Exception {
        mockMvc.perform(peticion).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sin token, un endpoint protegido responde 401")
    void withoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/api/persons")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("con un token sin el permiso exigido, responde 403")
    void withoutRequiredRoleReturnsForbidden() throws Exception {
        mockMvc.perform(get("/v1/api/persons").with(conRoles("client.read"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("con admin.full, el endpoint responde 200")
    void withAdminFullReturnsOk() throws Exception {
        mockMvc.perform(get("/v1/api/persons").with(conRoles(ApiAuthority.ADMIN_FULL)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("una autenticacion que no nace de la cadena de filtros la cubre la jerarquia")
    void laJerarquiaCubreLoQueElConverterNoAlcanza() throws Exception {
        // Aqui las autoridades se fijan a mano, saltandose el converter: es la situacion que motiva
        // el bean RoleHierarchy de SecurityConfig. Sin el, un administrador autenticado por
        // cualquier otra via llegaria a @PreAuthorize con admin.full crudo y recibiria 403.
        mockMvc.perform(get("/v1/api/persons")
                        .with(jwt().authorities(new SimpleGrantedAuthority(ApiAuthority.ADMIN_FULL))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("los roles de otro client del realm no otorgan permiso")
    void rolesFromAnotherClientAreIgnored() throws Exception {
        Map<String, Object> deOtroClient = Map.of("otro-client", Map.of("roles", List.of("admin.full")));
        when(jwtDecoder.decode(anyString())).thenReturn(tokenCon(deOtroClient));

        mockMvc.perform(get("/v1/api/persons").header("Authorization", "Bearer da-igual"))
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

        assertThat(status)
                .describedAs("El endpoint de salud no debe exigir autenticacion")
                .isNotIn(401, 403);
    }

    /**
     * Red de seguridad de la propia clase.
     *
     * <p>{@link #autorizado} da por buena cualquier respuesta que no sea 401 ni 403, de modo que una
     * ruta mal escrita respondería 404 o 405 y pasaría por acceso concedido sin conceder nada. Esta
     * prueba recorre todas las rutas que la clase usa y exige que, con un rol que no otorga nada,
     * respondan 403: solo puede hacerlo un handler que existe y está protegido.
     */
    @Nested
    @DisplayName("las rutas que esta clase usa existen y estan protegidas")
    class LasRutasExisten {

        @ParameterizedTest(name = "{0} {1}")
        @CsvSource({
            "GET, /v1/api/persons",
            "GET, /v1/api/countries",
            "GET, /v1/api/clients",
            "GET, /v1/api/managers",
            "GET, /v1/api/brands",
            "GET, /v1/api/client-equipments",
            "GET, /v1/api/service-areas/" + ID,
            "DELETE, /v1/api/countries/" + ID,
            "DELETE, /v1/api/cities/" + ID,
            "DELETE, /v1/api/persons/" + ID,
            "DELETE, /v1/api/persons/" + ID + "/emails/" + ID,
            "DELETE, /v1/api/persons/" + ID + "/phones/" + ID,
            "DELETE, /v1/api/clients/" + ID,
            "DELETE, /v1/api/clients/" + ID + "/emails/" + ID,
            "DELETE, /v1/api/clients/" + ID + "/phones/" + ID,
            "DELETE, /v1/api/clients/" + ID + "/representatives/" + ID,
            "DELETE, /v1/api/headquarters/" + ID,
            "DELETE, /v1/api/service-areas/" + ID,
            "DELETE, /v1/api/managers/" + ID,
            "DELETE, /v1/api/brands/" + ID,
            "DELETE, /v1/api/manufacturers/" + ID,
            "DELETE, /v1/api/models/" + ID,
            "DELETE, /v1/api/equipment-types/" + ID,
            "DELETE, /v1/api/equipments/" + ID,
            "DELETE, /v1/api/client-equipments/" + ID,
            "PATCH, /v1/api/client-equipments/" + ID + "/service-area/" + ID
        })
        @DisplayName("responde 403 a un rol que no otorga nada")
        void laRutaExisteYPide(String verbo, String ruta) throws Exception {
            MockHttpServletRequestBuilder peticion =
                    switch (verbo) {
                        case "GET" -> get(ruta);
                        case "DELETE" -> delete(ruta);
                        case "PATCH" -> patch(ruta);
                        default -> throw new IllegalArgumentException("Verbo no contemplado: " + verbo);
                    };

            prohibido(peticion.with(conRoles("rol.inventado")));
        }
    }

    /**
     * La expansión del administrador vista desde HTTP, con el token pasando por el converter.
     *
     * <p>Un solo rol en el token y acceso a todos los recursos: eso es lo que hace que las ochenta y
     * tres anotaciones puedan nombrar solo su recurso sin dejar fuera al administrador.
     */
    @Nested
    @DisplayName("la expansion del administrador")
    class ExpansionDelAdministrador {

        @ParameterizedTest
        @ValueSource(strings = {"admin.full", "super.admin.full"})
        @DisplayName("abre un endpoint de lectura de cada recurso")
        void abreLasLecturasDeTodosLosRecursos(String rolDeAdministrador) throws Exception {
            autorizado(get("/v1/api/persons").with(conRoles(rolDeAdministrador)));
            autorizado(get("/v1/api/countries").with(conRoles(rolDeAdministrador)));
            autorizado(get("/v1/api/clients").with(conRoles(rolDeAdministrador)));
            autorizado(get("/v1/api/service-areas/" + ID).with(conRoles(rolDeAdministrador)));
            autorizado(get("/v1/api/managers").with(conRoles(rolDeAdministrador)));
            autorizado(get("/v1/api/brands").with(conRoles(rolDeAdministrador)));
        }

        @ParameterizedTest
        @ValueSource(strings = {"admin.full", "super.admin.full"})
        @DisplayName("abre tambien las autoridades que no tiene ningun grupo del realm")
        void abreLasAutoridadesQueNadieMasTiene(String rolDeAdministrador) throws Exception {
            // client.delete y engineer.assign no los lleva ningun grupo salvo administradores: si la
            // expansion se los saltara, nadie en el sistema podria dar de baja a un cliente.
            autorizado(delete("/v1/api/clients/" + ID).with(conRoles(rolDeAdministrador)));
            autorizado(delete("/v1/api/managers/" + ID).with(conRoles(rolDeAdministrador)));
            autorizado(patch("/v1/api/client-equipments/" + ID + "/service-area/" + ID)
                    .with(conRoles(rolDeAdministrador)));
        }

        @Test
        @DisplayName("super.admin.full a solas recorre la cadena entera hasta los recursos")
        void superAdminSolo() throws Exception {
            // La cadena es super.admin.full -> admin.full -> los diecisiete. Si se rompiera el primer
            // eslabon, solo lo delataria un token que traiga super.admin.full y nada mas.
            autorizado(get("/v1/api/clients").with(conRoles("super.admin.full")));
        }

        @Test
        @DisplayName("un rol desconocido junto a admin.full no impide la expansion")
        void elRolDesconocidoNoEstorba() throws Exception {
            autorizado(get("/v1/api/clients").with(conRoles("rol.inventado", "admin.full")));
        }

        @ParameterizedTest
        @ValueSource(strings = {"admin.fullish", "super.admin", "ADMIN.FULL", "admin.ful"})
        @DisplayName("un rol que solo se parece a admin.full no abre nada")
        void elParecidoNoAbreNada(String casiAdmin) throws Exception {
            prohibido(get("/v1/api/clients").with(conRoles(casiAdmin)));
        }

        @Test
        @DisplayName("tener las diecisiete autoridades no concede admin.full")
        void laExpansionNoVaAlReves() throws Exception {
            // No hay endpoint que exija admin.full, asi que lo unico observable por HTTP es que
            // tenerlas todas no abre nada mas de lo que cada una abre. La direccion contraria de la
            // implicacion se comprueba en ApiAuthorityTest y en SecurityConfigRoleHierarchyTest.
            String[] todas = ApiAuthority.RESOURCE_AUTHORITIES.toArray(String[]::new);

            autorizado(get("/v1/api/clients").with(conRoles(todas)));
            prohibido(get("/v1/api/clients").with(conRoles("rol.inventado")));
        }
    }

    /**
     * El rol equivocado, que es donde estaba el agujero que este modelo cierra.
     *
     * <p>Las ochenta y tres anotaciones se pusieron con un script por número de línea. Un desfase de
     * una línea deja una escritura protegida por la autoridad de lectura y nada lo delata: el
     * endpoint sigue respondiendo, solo que a quien no debería.
     */
    @Nested
    @DisplayName("la autoridad de lectura no abre las escrituras")
    class LecturaNoAbreEscritura {

        @ParameterizedTest(name = "{0} con {1}")
        @CsvSource({
            "/v1/api/countries/" + ID + ", location.read",
            "/v1/api/cities/" + ID + ", location.read",
            "/v1/api/persons/" + ID + ", person.read",
            "/v1/api/persons/" + ID + "/emails/" + ID + ", person.read",
            "/v1/api/persons/" + ID + "/phones/" + ID + ", person.read",
            "/v1/api/clients/" + ID + ", client.read",
            "/v1/api/clients/" + ID + "/emails/" + ID + ", client.read",
            "/v1/api/headquarters/" + ID + ", client.read",
            "/v1/api/service-areas/" + ID + ", service-area.read",
            "/v1/api/managers/" + ID + ", engineer.read",
            "/v1/api/brands/" + ID + ", equipment.read",
            "/v1/api/manufacturers/" + ID + ", equipment.read",
            "/v1/api/models/" + ID + ", equipment.read",
            "/v1/api/equipment-types/" + ID + ", equipment.read",
            "/v1/api/equipments/" + ID + ", equipment.read",
            "/v1/api/client-equipments/" + ID + ", equipment.read"
        })
        @DisplayName("un DELETE con solo la autoridad de lectura de su propio recurso responde 403")
        void elDeleteConSoloLecturaResponde403(String ruta, String autoridadDeLectura) throws Exception {
            prohibido(delete(ruta).with(conRoles(autoridadDeLectura)));
        }
    }

    /**
     * Cruces entre módulos: cada autoridad abre lo suyo y nada de lo ajeno.
     */
    @Nested
    @DisplayName("una autoridad no abre el modulo de al lado")
    class NoHayFugasEntreModulos {

        @Test
        @DisplayName("client.read no abre las personas")
        void clientReadNoAbrePersonas() throws Exception {
            prohibido(get("/v1/api/persons").with(conRoles("client.read")));
        }

        @Test
        @DisplayName("equipment.read no abre los clientes")
        void equipmentReadNoAbreClientes() throws Exception {
            prohibido(get("/v1/api/clients").with(conRoles("equipment.read")));
        }

        @Test
        @DisplayName("person.write no abre los paises, ni para leer ni para escribir")
        void personWriteNoAbrePaises() throws Exception {
            prohibido(get("/v1/api/countries").with(conRoles("person.write")));
            prohibido(delete("/v1/api/countries/" + ID).with(conRoles("person.write")));
        }

        @Test
        @DisplayName("client.read no abre las areas de servicio, que llevan autoridad propia")
        void clientReadNoAbreAreasDeServicio() throws Exception {
            // Las areas cuelgan de la sede, que cuelga del cliente, pero su autoridad es aparte: un
            // cliente puede ver sus areas sin poder ver la ficha de otros clientes.
            prohibido(get("/v1/api/service-areas/" + ID).with(conRoles("client.read")));
        }

        @Test
        @DisplayName("engineer.assign no abre la consulta de encargados")
        void engineerAssignNoAbreLaLectura() throws Exception {
            // Poner a alguien al frente y consultar quien esta al frente son permisos distintos.
            prohibido(get("/v1/api/managers").with(conRoles("engineer.assign")));
        }
    }

    /**
     * Los tres repartos que no son el obvio. Son decisiones deliberadas y, precisamente por eso, lo
     * primero que un refactor uniformiza sin darse cuenta.
     */
    @Nested
    @DisplayName("los repartos que no son el obvio")
    class RepartosDeliberados {

        @Test
        @DisplayName("con client.write se quita un correo del cliente pero no se da de baja al cliente")
        void clientWriteNoDaDeBajaAlCliente() throws Exception {
            autorizado(delete("/v1/api/clients/" + ID + "/emails/" + ID).with(conRoles("client.write")));
            autorizado(delete("/v1/api/clients/" + ID + "/phones/" + ID).with(conRoles("client.write")));
            autorizado(delete("/v1/api/clients/" + ID + "/representatives/" + ID).with(conRoles("client.write")));

            prohibido(delete("/v1/api/clients/" + ID).with(conRoles("client.write")));
        }

        @Test
        @DisplayName("con client.delete se da de baja al cliente pero no se le tocan los sub-recursos")
        void clientDeleteNoTocaLosSubRecursos() throws Exception {
            autorizado(delete("/v1/api/clients/" + ID).with(conRoles("client.delete")));

            prohibido(delete("/v1/api/clients/" + ID + "/emails/" + ID).with(conRoles("client.delete")));
        }

        @Test
        @DisplayName("con equipment.write se corrige y se da de baja, pero no se vincula ni se traslada")
        void equipmentWriteNoVinculaNiTraslada() throws Exception {
            autorizado(delete("/v1/api/client-equipments/" + ID).with(conRoles("equipment.write")));

            prohibido(patch("/v1/api/client-equipments/" + ID + "/service-area/" + ID)
                    .with(conRoles("equipment.write")));
            prohibido(post("/v1/api/service-areas/" + ID + "/equipments")
                    .with(conRoles("equipment.write"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(unEquipoDeCliente()));
        }

        @Test
        @DisplayName("con equipment.assign se vincula y se traslada, pero no se da de baja")
        void equipmentAssignNoDaDeBaja() throws Exception {
            autorizado(patch("/v1/api/client-equipments/" + ID + "/service-area/" + ID)
                    .with(conRoles("equipment.assign")));
            autorizado(post("/v1/api/service-areas/" + ID + "/equipments")
                    .with(conRoles("equipment.assign"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(unEquipoDeCliente()));

            prohibido(delete("/v1/api/client-equipments/" + ID).with(conRoles("equipment.assign")));
        }

        @Test
        @DisplayName("los encargados se gobiernan con engineer.read y engineer.assign, no con engineer.write")
        void alEncargadoSeLeAsignaNoSeLeEscribe() throws Exception {
            // engineer.write no existe en el realm. Si alguien lo escribiera en una anotacion, el
            // endpoint quedaria cerrado para todo el mundo, administrador incluido.
            autorizado(get("/v1/api/managers").with(conRoles("engineer.read")));
            autorizado(delete("/v1/api/managers/" + ID).with(conRoles("engineer.assign")));

            prohibido(delete("/v1/api/managers/" + ID).with(conRoles("engineer.write")));
        }
    }

    /**
     * Los perfiles de los grupos del realm, con los roles leídos del archivo que se importa en
     * Keycloak. Si el realm cambia, estas pruebas cambian de significado en el acto, que es
     * exactamente lo que se quiere: obligan a revisar qué acaba de abrirse o cerrarse.
     */
    @Nested
    @DisplayName("los perfiles de los grupos del realm")
    class PerfilesDeLosGrupos {

        @Test
        @DisplayName("un ingeniero lee todo lo que necesita para trabajar")
        void elIngenieroLeeLoQueNecesita() throws Exception {
            autorizado(get("/v1/api/persons").with(comoElGrupo("engineers")));
            autorizado(get("/v1/api/countries").with(comoElGrupo("engineers")));
            autorizado(get("/v1/api/clients").with(comoElGrupo("engineers")));
            autorizado(get("/v1/api/service-areas/" + ID).with(comoElGrupo("engineers")));
            autorizado(get("/v1/api/managers").with(comoElGrupo("engineers")));
            autorizado(get("/v1/api/brands").with(comoElGrupo("engineers")));
        }

        @Test
        @DisplayName("un ingeniero mantiene el parque de equipos: corrige, da de baja, vincula y traslada")
        void elIngenieroMantieneLosEquipos() throws Exception {
            autorizado(delete("/v1/api/brands/" + ID).with(comoElGrupo("engineers")));
            autorizado(delete("/v1/api/client-equipments/" + ID).with(comoElGrupo("engineers")));
            autorizado(patch("/v1/api/client-equipments/" + ID + "/service-area/" + ID)
                    .with(comoElGrupo("engineers")));
        }

        @Test
        @DisplayName("un ingeniero da de alta el equipo de un area de servicio")
        void elIngenieroVinculaEquipos() throws Exception {
            // Vincular un equipo a un area es la unica alta que el ingeniero hace, y es la que da
            // sentido a que lleve equipment.assign. Las demas pruebas del perfil miran DELETE y
            // PATCH; sin esta, el POST del oficio del ingeniero no lo mira nadie.
            autorizado(post("/v1/api/service-areas/" + ID + "/equipments")
                    .with(comoElGrupo("engineers"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(unEquipoDeCliente()));
        }

        @Test
        @DisplayName("un ingeniero asigna equipos pero no encargados: equipment.assign no es engineer.assign")
        void elIngenieroNoAsignaEncargados() throws Exception {
            // Las dos autoridades se llaman igual salvo por el recurso, y el ingeniero lleva una y
            // no la otra. Es el par que una revision apresurada del realm uniformiza "para que el
            // ingeniero pueda asignar", concediendo de paso el gobierno de los encargados.
            autorizado(patch("/v1/api/client-equipments/" + ID + "/service-area/" + ID)
                    .with(comoElGrupo("engineers")));

            prohibido(delete("/v1/api/managers/" + ID).with(comoElGrupo("engineers")));
        }

        @Test
        @DisplayName("un ingeniero consulta personas y paises pero no los modifica")
        void elIngenieroLeeSinEscribirFueraDeSuOficio() throws Exception {
            // Lleva person.read y location.read porque necesita ver a quien llamar y donde ir, pero
            // ninguna de las dos autoridades de escritura. Dar de baja una ciudad o una persona son
            // las operaciones vecinas de las que si puede hacer, y por eso conviene fijarlas.
            autorizado(get("/v1/api/persons").with(comoElGrupo("engineers")));

            prohibido(delete("/v1/api/persons/" + ID).with(comoElGrupo("engineers")));
            prohibido(delete("/v1/api/cities/" + ID).with(comoElGrupo("engineers")));
            prohibido(delete("/v1/api/countries/" + ID).with(comoElGrupo("engineers")));
        }

        @Test
        @DisplayName("un ingeniero no da de alta clientes, paises, personas ni encargados")
        void elIngenieroNoDaDeAltaNada() throws Exception {
            prohibido(post("/v1/api/clients")
                    .with(comoElGrupo("engineers"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(unCliente()));
            prohibido(post("/v1/api/countries")
                    .with(comoElGrupo("engineers"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(unPais()));
            prohibido(post("/v1/api/persons")
                    .with(comoElGrupo("engineers"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(unaPersona()));
            prohibido(post("/v1/api/managers")
                    .with(comoElGrupo("engineers"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(unEncargado()));
        }

        @Test
        @DisplayName("un ingeniero tampoco da de baja al cliente ni a su sede")
        void elIngenieroNoDaDeBajaAlCliente() throws Exception {
            prohibido(delete("/v1/api/clients/" + ID).with(comoElGrupo("engineers")));
            prohibido(delete("/v1/api/headquarters/" + ID).with(comoElGrupo("engineers")));
            prohibido(delete("/v1/api/service-areas/" + ID).with(comoElGrupo("engineers")));
        }

        @Test
        @DisplayName("un cliente lee su ficha, sus areas y sus equipos")
        void elClienteLeeLoSuyo() throws Exception {
            autorizado(get("/v1/api/clients").with(comoElGrupo("clients")));
            autorizado(get("/v1/api/service-areas/" + ID).with(comoElGrupo("clients")));
            autorizado(get("/v1/api/client-equipments").with(comoElGrupo("clients")));
        }

        @Test
        @DisplayName("un cliente no escribe nada en ningun modulo")
        void elClienteNoEscribeNada() throws Exception {
            prohibido(delete("/v1/api/clients/" + ID).with(comoElGrupo("clients")));
            prohibido(delete("/v1/api/clients/" + ID + "/emails/" + ID).with(comoElGrupo("clients")));
            prohibido(delete("/v1/api/service-areas/" + ID).with(comoElGrupo("clients")));
            prohibido(delete("/v1/api/brands/" + ID).with(comoElGrupo("clients")));
            prohibido(delete("/v1/api/client-equipments/" + ID).with(comoElGrupo("clients")));
        }

        @Test
        @DisplayName("un cliente no ve personas, paises ni encargados")
        void elClienteNoVeLoQueNoEsSuyo() throws Exception {
            prohibido(get("/v1/api/persons").with(comoElGrupo("clients")));
            prohibido(get("/v1/api/countries").with(comoElGrupo("clients")));
            prohibido(get("/v1/api/managers").with(comoElGrupo("clients")));
        }

        @Test
        @DisplayName("un cliente ve sus equipos pero no vincula ninguno a un area")
        void elClienteNoVinculaEquipos() throws Exception {
            // Lleva equipment.read y service-area.read, que son las dos autoridades que intervienen
            // en la ruta de vincular. Tenerlas ambas y aun asi no poder vincular es lo que demuestra
            // que la operacion pide equipment.assign y no se contenta con leer los dos extremos.
            prohibido(post("/v1/api/service-areas/" + ID + "/equipments")
                    .with(comoElGrupo("clients"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(unEquipoDeCliente()));
        }

        @Test
        @DisplayName("un administrador del realm escribe en todos los modulos")
        void elAdministradorEscribeEnTodo() throws Exception {
            // Los otros dos grupos se comprueban con los roles del realm; este se comprobaba solo
            // con admin.full a secas. Pero el realm no le da admin.full a solas: le da los dieciocho
            // roles a la vez. Si la expansion se estorbara con los roles ya presentes, o si el realm
            // dejara de incluir admin.full en el grupo, solo lo veria una prueba que use el grupo.
            autorizado(delete("/v1/api/persons/" + ID).with(comoElGrupo("admins")));
            autorizado(delete("/v1/api/countries/" + ID).with(comoElGrupo("admins")));
            autorizado(delete("/v1/api/clients/" + ID).with(comoElGrupo("admins")));
            autorizado(delete("/v1/api/service-areas/" + ID).with(comoElGrupo("admins")));
            autorizado(delete("/v1/api/managers/" + ID).with(comoElGrupo("admins")));
            autorizado(delete("/v1/api/client-equipments/" + ID).with(comoElGrupo("admins")));
            autorizado(patch("/v1/api/client-equipments/" + ID + "/service-area/" + ID)
                    .with(comoElGrupo("admins")));
        }
    }

    private static String unCliente() {
        return """
               {"documento":"900123456","tipoIdentificacion":"NIT_JURIDICO","razonSocial":"Hospital Central"}
               """;
    }

    private static String unPais() {
        return """
               {"codigoIso":"COL","nombre":"Colombia"}
               """;
    }

    private static String unaPersona() {
        return """
               {"cedula":"1234567890","primerNombre":"Ada","primerApellido":"Lovelace",
                "tipoPersona":"MANAGER"}
               """;
    }

    private static String unEncargado() {
        return """
               {"cedula":"1234567890","primerNombre":"Ada","primerApellido":"Lovelace",
                "tipo":"HEADQUARTER","idAsignacion":"%s","correos":[{"valor":"ada@hospital.com"}]}
               """
                .formatted(ID);
    }

    private static String unEquipoDeCliente() {
        return """
               {"serie":"SN-0001","idModelo":"%s"}
               """.formatted(ID);
    }
}
