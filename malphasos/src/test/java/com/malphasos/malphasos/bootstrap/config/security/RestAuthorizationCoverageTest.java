package com.malphasos.malphasos.bootstrap.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invariantes estructurales del modelo de permisos: se recorren por reflexión todos los
 * controladores y se exige que cada operación esté protegida y que la autoridad que nombra exista.
 *
 * <p>Existe porque el fallo que persigue es silencioso. Escribir
 * {@code hasAuthority('equipmentt.read')} compila, arranca y responde 403 a todo el mundo para
 * siempre; olvidar la anotación entera compila, arranca y deja el endpoint abierto a cualquier
 * autenticado. Ninguna de las dos cosas rompe nada visible, igual que un {@code pathsToMatch} de
 * OpenAPI que no casa con ninguna ruta. Y las ochenta y tres anotaciones se pusieron con un script
 * por número de línea, que es justo la manera de equivocarse en una sola sin notarlo.
 *
 * <p>No usa el contexto de Spring: escanea el classpath. Así corre en milisegundos y no depende de
 * que la aplicación arranque, que es otra cosa distinta de la que aquí se comprueba.
 */
class RestAuthorizationCoverageTest {

    private static final String PAQUETE_RAIZ = "com.malphasos.malphasos";

    /** La única forma admitida: una sola autoridad, nombrada literalmente. */
    private static final Pattern UNA_SOLA_AUTORIDAD = Pattern.compile("^hasAuthority\\('([^']+)'\\)$");

    /**
     * La segunda forma valida: delegar en un bean cuando la autoridad exigida depende del dato.
     *
     * <p>Existe desde el 2026-09-13 por la escalera de usuarios: al editar o retirar a alguien, lo
     * que se puede hacer depende de <b>que tipo de persona es</b>, y eso no esta en la ruta sino en
     * la fila. Una autoridad literal no lo puede expresar.
     *
     * <p><b>Es una excepcion acotada, no una puerta abierta.</b> Otra prueba fija que solo la usen
     * las operaciones que reciben un identificador de persona: en cuanto sirva para esquivar una
     * autoridad literal en otro sitio, el modelo de permisos vuelve a estar repartido.
     */
    private static final Pattern DELEGA_EN_UN_BEAN =
            Pattern.compile("^@(\\w+)\\.\\w+\\([^)]*\\)$");

    /** Vocabulario real, leído de las constantes de {@link ApiAuthority}. */
    private static final Set<String> VOCABULARIO = vocabularioDeclarado();

    private static final List<Class<?>> CONTROLADORES = escanearControladores();

    private static Set<String> vocabularioDeclarado() {
        return java.util.Arrays.stream(ApiAuthority.class.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()) && f.getType() == String.class)
                .map(f -> {
                    try {
                        return (String) f.get(null);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException("No se pudo leer " + f.getName(), e);
                    }
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<Class<?>> escanearControladores() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Class<?>> encontrados = new ArrayList<>();
        for (BeanDefinition definicion : scanner.findCandidateComponents(PAQUETE_RAIZ)) {
            try {
                encontrados.add(Class.forName(definicion.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Controlador escaneado pero no cargable", e);
            }
        }
        encontrados.sort(java.util.Comparator.comparing(Class::getName));

        return List.copyOf(encontrados);
    }

    /** Todo método que publique una ruta, sea cual sea el verbo. */
    private static List<Method> operacionesDe(Class<?> controlador) {
        return java.util.Arrays.stream(controlador.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class))
                .sorted(java.util.Comparator.comparing(Method::getName))
                .toList();
    }

    private static List<Method> todasLasOperaciones() {
        return CONTROLADORES.stream().flatMap(c -> operacionesDe(c).stream()).toList();
    }

    private static String autoridadDe(Method operacion) {
        PreAuthorize anotacion = AnnotatedElementUtils.findMergedAnnotation(operacion, PreAuthorize.class);
        if (anotacion == null) {
            return null;
        }
        Matcher m = UNA_SOLA_AUTORIDAD.matcher(anotacion.value().trim());

        return m.matches() ? m.group(1) : null;
    }

    /** Si la anotacion delega la decision en un bean en vez de nombrar una autoridad. */
    private static boolean delegaEnUnBean(Method operacion) {
        PreAuthorize anotacion = AnnotatedElementUtils.findMergedAnnotation(operacion, PreAuthorize.class);

        return anotacion != null && DELEGA_EN_UN_BEAN.matcher(anotacion.value().trim()).matches();
    }

    /** Si la operacion vive bajo una ruta de personas y recibe el identificador de una. */
    private static boolean recibeUnaPersonaPorIdentificador(Method operacion) {
        RequestMapping mapeo = AnnotatedElementUtils.findMergedAnnotation(
                operacion.getDeclaringClass(), RequestMapping.class);
        boolean bajoPersonas = mapeo != null
                && Arrays.stream(mapeo.value()).anyMatch(ruta -> ruta.startsWith("/v1/api/persons"));

        return bajoPersonas
                && Arrays.stream(operacion.getParameters())
                        .anyMatch(p -> p.getType() == UUID.class);
    }

    private static String nombre(Method operacion) {
        return operacion.getDeclaringClass().getSimpleName() + "#" + operacion.getName();
    }

    /** Verbos que publica la operación, resueltos a través de {@code @GetMapping} y compañía. */
    private static Set<RequestMethod> verbosDe(Method operacion) {
        RequestMapping mapeo = AnnotatedElementUtils.findMergedAnnotation(operacion, RequestMapping.class);

        return mapeo == null ? Set.of() : Set.of(mapeo.method());
    }

    /**
     * Una autoridad que solo permite consultar.
     *
     * <p>Se decide por el sufijo del nombre y no por una lista escrita a mano, para que una
     * autoridad de lectura de un módulo futuro entre sola en estas comprobaciones.
     */
    private static boolean esDeLectura(String autoridad) {
        return autoridad != null && autoridad.endsWith(".read");
    }

    @Test
    @DisplayName("el escaneo encuentra los controladores y sus operaciones")
    void elEscaneoEncuentraAlgo() {
        // Sin esta comprobacion, un escaneo que no encontrara nada haria pasar en vacio a todas las
        // demas pruebas de esta clase, que es exactamente el fallo silencioso que vienen a evitar.
        // Los numeros son un suelo, no un inventario: crecen cuando se anaden endpoints.
        assertThat(CONTROLADORES).hasSizeGreaterThanOrEqualTo(15);
        assertThat(todasLasOperaciones()).hasSizeGreaterThanOrEqualTo(83);
    }

    @Test
    @DisplayName("toda operacion publicada declara un @PreAuthorize")
    void todaOperacionEstaProtegida() {
        List<String> sinProteger = todasLasOperaciones().stream()
                .filter(m -> AnnotatedElementUtils.findMergedAnnotation(m, PreAuthorize.class) == null)
                .map(RestAuthorizationCoverageTest::nombre)
                .toList();

        assertThat(sinProteger)
                .describedAs("Una operacion sin @PreAuthorize queda abierta a cualquier autenticado")
                .isEmpty();
    }

    @Test
    @DisplayName("ningun controlador declara @PreAuthorize a nivel de clase")
    void ningunPreAuthorizeDeClase() {
        // Si lo hubiera, una operacion sin anotacion propia seguiria protegida y la prueba anterior
        // daria un falso positivo. Prohibirlo mantiene el modelo legible operacion por operacion.
        List<String> conAnotacionDeClase = CONTROLADORES.stream()
                .filter(c -> AnnotatedElementUtils.findMergedAnnotation(c, PreAuthorize.class) != null)
                .map(Class::getSimpleName)
                .toList();

        assertThat(conAnotacionDeClase).isEmpty();
    }

    @Test
    @DisplayName("toda operacion exige exactamente una autoridad, nombrada literalmente")
    void todaOperacionExigeUnaSolaAutoridad() {
        List<String> conFormaRara = todasLasOperaciones().stream()
                .filter(m -> autoridadDe(m) == null && !delegaEnUnBean(m))
                .map(RestAuthorizationCoverageTest::nombre)
                .toList();

        assertThat(conFormaRara)
                .describedAs("Se espera hasAuthority('<recurso>.<accion>'), o delegar en un bean "
                        + "cuando la autoridad depende del dato. Sin hasAnyAuthority ni hasRole")
                .isEmpty();
    }

    @Test
    @DisplayName("toda autoridad citada por un controlador existe en ApiAuthority")
    void todaAutoridadCitadaExiste() {
        // Un nombre mal escrito no falla al arrancar: deja el endpoint inaccesible para siempre,
        // porque nadie puede traer en el token una autoridad que el realm no define.
        List<String> desconocidas = todasLasOperaciones().stream()
                .filter(m -> !delegaEnUnBean(m))
                .map(m -> nombre(m) + " -> " + autoridadDe(m))
                .filter(par -> !VOCABULARIO.contains(par.substring(par.indexOf(" -> ") + 4)))
                .toList();

        assertThat(desconocidas).isEmpty();
    }

    @Test
    @DisplayName("delegar en un bean solo se admite donde el tipo del objetivo no esta en la ruta")
    void laExcepcionEstaAcotada() {
        // Sin esto, la forma de bean seria la via para esquivar cualquier autoridad literal, y el
        // modelo de permisos volveria a estar repartido por los controladores. Solo las operaciones
        // que reciben una persona por identificador pueden usarla, porque son las unicas donde la
        // autoridad exigida no se puede saber leyendo la ruta.
        List<String> indebidas = todasLasOperaciones().stream()
                .filter(RestAuthorizationCoverageTest::delegaEnUnBean)
                .filter(m -> !recibeUnaPersonaPorIdentificador(m))
                .map(RestAuthorizationCoverageTest::nombre)
                .toList();

        assertThat(indebidas)
                .describedAs("Delegar en un bean solo vale si la autoridad depende de la fila")
                .isEmpty();
    }

    @Test
    @DisplayName("ningun controlador nombra al administrador")
    void ningunControladorNombraAlAdministrador() {
        // El objetivo del modelo: quien es administrador se decide en ApiAuthority. En cuanto una
        // anotacion lo nombre, la regla vuelve a estar repartida por los ochenta y tres endpoints.
        List<String> nombranAlAdministrador = todasLasOperaciones().stream()
                .filter(m -> ApiAuthority.ADMIN_FULL.equals(autoridadDe(m))
                        || ApiAuthority.SUPER_ADMIN_FULL.equals(autoridadDe(m)))
                .map(RestAuthorizationCoverageTest::nombre)
                .toList();

        assertThat(nombranAlAdministrador).isEmpty();
    }

    @Test
    @DisplayName("toda autoridad de recurso de un modulo ya construido protege algun endpoint")
    void ningunaAutoridadSobra() {
        Set<String> citadas =
                todasLasOperaciones().stream().map(RestAuthorizationCoverageTest::autoridadDe).collect(Collectors.toSet());

        Set<String> sinUsar = ApiAuthority.RESOURCE_AUTHORITIES.stream()
                .filter(a -> !citadas.contains(a))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(sinUsar)
                .describedAs("Una autoridad que no protege nada es un permiso que el realm concede en vano")
                .isEmpty();
    }

    @Test
    @DisplayName("las tres autoridades de work-order protegen ya sus endpoints")
    void lasAutoridadesDeWorkOrderProtegenSuModulo() {
        // Hasta el 2026-09-12 esta prueba afirmaba lo contrario —que no protegian nada— como
        // omision consciente: las tres figuraban en el catalogo y en el realm porque el grupo de
        // ingenieros ya las tenia, pero el modulo no existia. Fallo el dia que aparecieron sus
        // endpoints, que es exactamente lo que se le pedia, y se sustituyo por esta.
        Set<String> citadas =
                todasLasOperaciones().stream().map(RestAuthorizationCoverageTest::autoridadDe).collect(Collectors.toSet());

        assertThat(citadas).contains("work-order.read", "work-order.write", "work-order.assign");
    }

    @Test
    @DisplayName("asignar es la unica operacion que exige work-order.assign")
    void workOrderAssignProtegeSoloLaAsignacion() {
        // Es lo que permite que un coordinador reparta trabajo sin poder alterar lo que se va a
        // hacer. Si algun dia otra operacion la exige, esa separacion deja de existir en silencio.
        List<Method> conAssign = todasLasOperaciones().stream()
                .filter(m -> "work-order.assign".equals(autoridadDe(m)))
                .toList();

        assertThat(conAssign).singleElement()
                .extracting(Method::getName)
                .isEqualTo("assign");
    }

    @Test
    @DisplayName("client.delete protege una sola operacion, y esta en el controlador del cliente")
    void clientDeleteProtegeSoloLaBajaDelCliente() {
        // Es el reparto que no es el obvio: los sub-recursos del cliente son client.write. Si
        // client.delete apareciera en una segunda operacion, dar de baja a un cliente entero habria
        // dejado de ser un permiso aparte.
        List<String> operaciones = todasLasOperaciones().stream()
                .filter(m -> ApiAuthority.CLIENT_DELETE.equals(autoridadDe(m)))
                .map(RestAuthorizationCoverageTest::nombre)
                .toList();

        assertThat(operaciones).hasSize(1).allMatch(n -> n.startsWith("ClientRestAdapter#"));
    }

    @Test
    @DisplayName("equipment.assign protege dos operaciones, las dos del equipo del cliente")
    void equipmentAssignProtegeSoloVincularYTrasladar() {
        List<String> operaciones = todasLasOperaciones().stream()
                .filter(m -> ApiAuthority.EQUIPMENT_ASSIGN.equals(autoridadDe(m)))
                .map(RestAuthorizationCoverageTest::nombre)
                .toList();

        assertThat(operaciones).hasSize(2).allMatch(n -> n.startsWith("ClientEquipmentRestAdapter#"));
    }

    @Test
    @DisplayName("no existe engineer.write: al encargado se le asigna, no se le escribe")
    void noExisteEngineerWrite() {
        assertThat(VOCABULARIO).doesNotContain("engineer.write");
    }

    @Test
    @DisplayName("toda consulta se protege con una autoridad de lectura")
    void todoGetPideLectura() {
        // El desfase de una linea del script se manifiesta en las dos direcciones, y esta es la que
        // ninguna prueba de HTTP ve venir: un GET protegido por equipment.write sigue respondiendo,
        // solo que deja fuera al grupo clients, que solo lee. Nadie lo nota hasta que un cliente
        // llama por telefono. La otra direccion la cubre todaEscrituraPideAlgoMasQueLectura.
        List<Method> consultas = todasLasOperaciones().stream()
                .filter(m -> verbosDe(m).contains(RequestMethod.GET))
                .toList();

        assertThat(consultas)
                .describedAs("Si el verbo no se resolviera, la comprobacion siguiente pasaria en vacio")
                .hasSizeGreaterThanOrEqualTo(27);

        List<String> consultasQueNoPidenLectura = consultas.stream()
                .filter(m -> !esDeLectura(autoridadDe(m)))
                .map(m -> nombre(m) + " -> " + autoridadDe(m))
                .toList();

        assertThat(consultasQueNoPidenLectura)
                .describedAs("Un GET tras una autoridad de escritura cierra la consulta a quien solo lee")
                .isEmpty();
    }

    @Test
    @DisplayName("ninguna operacion que no sea una consulta se conforma con una autoridad de lectura")
    void todaEscrituraPideAlgoMasQueLectura() {
        // La direccion peligrosa: un DELETE protegido por equipment.read lo puede ejecutar cualquier
        // cliente. SecurityIntegrationTest lo comprueba sobre dieciseis rutas concretas; aqui se
        // comprueba sobre las ochenta y tres, que es donde el descuido se esconde.
        List<Method> escrituras = todasLasOperaciones().stream()
                .filter(m -> !verbosDe(m).contains(RequestMethod.GET))
                .toList();

        assertThat(escrituras).hasSizeGreaterThanOrEqualTo(56);

        List<String> escriturasQueSoloPidenLectura = escrituras.stream()
                .filter(m -> esDeLectura(autoridadDe(m)))
                .map(m -> nombre(m) + " -> " + autoridadDe(m))
                .toList();

        assertThat(escriturasQueSoloPidenLectura)
                .describedAs("Una operacion que modifica no puede exigir solo la autoridad de leer")
                .isEmpty();
    }

    @Test
    @DisplayName("todo controlador REST se llama RestAdapter")
    void losControladoresSiguenLaConvencionDeNombre() {
        assertThat(CONTROLADORES).allSatisfy(c -> assertThat(c.getSimpleName()).endsWith("RestAdapter"));
    }
}
