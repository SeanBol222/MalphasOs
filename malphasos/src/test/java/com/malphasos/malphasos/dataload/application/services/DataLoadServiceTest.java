package com.malphasos.malphasos.dataload.application.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.dataload.EjemplosDeCarga;
import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import com.malphasos.malphasos.dataload.application.model.DataLoadReport;
import com.malphasos.malphasos.dataload.application.model.ExistingRecords;
import com.malphasos.malphasos.dataload.application.model.ReadFile;
import com.malphasos.malphasos.dataload.application.model.SectionCount;
import com.malphasos.malphasos.dataload.application.ports.output.LocationCatalogPort;
import com.malphasos.malphasos.dataload.application.ports.output.MetrologyCatalogPort;
import com.malphasos.malphasos.dataload.infrastructure.output.format.JsonSchemaLoadFormatAdapter;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La validación de una carga con el esquema de verdad: lo que se ve mirando los archivos juntos. */
class DataLoadServiceTest {

    /** El catálogo de ubicación de la prueba: Colombia con cuatro ciudades, y Estados Unidos sin ninguna. */
    private static final LocationCatalogPort UBICACION = new LocationCatalogPort() {
        @Override
        public boolean countryExists(String codigoIso) {
            return List.of("COL", "USA").contains(codigoIso);
        }

        @Override
        public List<String> citiesOf(String codigoIso) {
            return "COL".equals(codigoIso) ? List.of("Bogotá", "Medellín", "Cali", "Soacha") : List.of();
        }
    };

    /** La cédula del ingeniero de las órdenes del ejemplo. */
    private static final String INGENIERO = "1000000001";

    /**
     * Lo que hay cuando se sube un historial: el catálogo se cargó solo antes, y su ingeniero recibió cuenta y
     * guardó su firma. Nada más.
     */
    private static final ExistingRecords SOLO_EL_INGENIERO = new ExistingRecords(List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(INGENIERO), List.of(), List.of(), List.of(INGENIERO));

    /** Lo que MalphasOS ya tiene en cada prueba: solo el ingeniero, salvo que la prueba lo cambie. */
    private ExistingRecords existentes = SOLO_EL_INGENIERO;

    /** Las tres magnitudes de los ejemplos, con algunas de sus unidades. */
    private static final MetrologyCatalogPort METROLOGIA = codigo -> Optional.ofNullable(Map.of(
            "presion", List.of("mmHg", "kPa"),
            "temperatura", List.of("°C", "°F"),
            "humedad_relativa", List.of("%HR", "%")).get(codigo));

    /** Lo que llegó a escribirse, si llegó: el escritor de verdad se prueba contra PostgreSQL. */
    private List<ReadFile> escritos;

    private final DataLoadService servicio = new DataLoadService(new JsonSchemaLoadFormatAdapter(), UBICACION,
            () -> existentes, METROLOGIA, archivos -> escritos = archivos);

    private static int nuevos(DataLoadReport informe, String seccion) {
        return informe.resumen().stream().filter(s -> s.seccion().equals(seccion)).findFirst().orElseThrow().nuevos();
    }

    private static int yaEstan(DataLoadReport informe, String seccion) {
        return informe.resumen().stream().filter(s -> s.seccion().equals(seccion)).findFirst().orElseThrow().existentes();
    }

    private DataLoadReport conCliente(String de, String a) {
        String cliente = EjemplosDeCarga.texto(EjemplosDeCarga.CLIENTE).replaceFirst(de, a);
        assertThat(cliente).describedAs("El ejemplo tiene que traer " + de).isNotEqualTo(EjemplosDeCarga.texto(EjemplosDeCarga.CLIENTE));
        return servicio.validate(List.of(CATALOGO, EjemplosDeCarga.archivo("cliente.json", cliente), HISTORIAL));
    }

    private static final DataLoadFile CATALOGO = EjemplosDeCarga.archivo(EjemplosDeCarga.CATALOGO);
    private static final DataLoadFile CLIENTE = EjemplosDeCarga.archivo(EjemplosDeCarga.CLIENTE);
    private static final DataLoadFile HISTORIAL = EjemplosDeCarga.archivo(EjemplosDeCarga.HISTORIAL);

    @Test
    @DisplayName("los tres ejemplos juntos son una carga valida, que con solo su ingeniero en MalphasOS crea lo demas")
    void losTresEjemplos() {
        DataLoadReport informe = servicio.validate(List.of(CATALOGO, CLIENTE, HISTORIAL));

        assertThat(informe.valida()).isTrue();
        assertThat(informe.errores()).isEmpty();
        assertThat(informe.resumen()).extracting(SectionCount::seccion).containsExactly("marcas", "fabricantes",
                "protocolos", "tiposEquipo", "modelos", "patrones", "personas", "clientes", "sedes", "areas", "equipos",
                "ordenes", "reportes");
        assertThat(informe.resumen()).filteredOn(s -> !s.seccion().equals("personas"))
                .allSatisfy(s -> assertThat(s.existentes()).isZero());
        assertThat(nuevos(informe, "personas")).isEqualTo(1);
        assertThat(yaEstan(informe, "personas")).isEqualTo(1);
        assertThat(nuevos(informe, "reportes")).isEqualTo(3);
    }

    @Test
    @DisplayName("lo que ya existe se reconoce por su llave natural, sin tildes ni mayusculas, y se cuenta aparte")
    void loQueYaExiste() {
        existentes = new ExistingRecords(List.of("WELCH ALLYN"), List.of(), List.of("desinfeccion tecnica inicial"),
                List.of("Tensiometro analogo"), List.of(new ExistingRecords.ModelName("Tensiómetro análogo", "Welch Allyn", "ds44")),
                List.of("md-0001"), List.of(" 1000000001 "),
                List.of(new ExistingRecords.ExistingClient("900000001-1",
                        List.of(new ExistingRecords.ExistingSite("PRINCIPAL", List.of("consultorio"))))),
                List.of(new ExistingRecords.EquipmentName(
                        new ExistingRecords.ModelName("Tensiómetro análogo", "Welch Allyn", "DS44"), "wa-123456")));
        // Aquí solo se miran los conteos: el equipo sin serie de ese cliente da un error, que prueba
        // sinSerieClienteQueYaEsta.
        DataLoadReport informe = servicio.validate(List.of(CATALOGO, CLIENTE));

        assertThat(yaEstan(informe, "marcas")).isEqualTo(1);
        assertThat(nuevos(informe, "marcas")).isEqualTo(1);
        assertThat(yaEstan(informe, "protocolos")).isEqualTo(1);
        assertThat(yaEstan(informe, "tiposEquipo")).isEqualTo(1);
        assertThat(yaEstan(informe, "modelos")).isEqualTo(1);
        assertThat(yaEstan(informe, "patrones")).isEqualTo(1);
        assertThat(yaEstan(informe, "personas")).isEqualTo(1);
        assertThat(yaEstan(informe, "clientes")).isEqualTo(1);
        assertThat(yaEstan(informe, "sedes")).isEqualTo(1);
        assertThat(yaEstan(informe, "areas")).isEqualTo(1);
        assertThat(nuevos(informe, "areas")).isEqualTo(1);
        assertThat(yaEstan(informe, "equipos")).isEqualTo(1);
    }

    @Test
    @DisplayName("un area se reconoce dentro de su sede, y una sede dentro de su cliente: el mismo nombre en otra es nuevo")
    void cadaUnoEnLoSuyo() {
        existentes = new ExistingRecords(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new ExistingRecords.ExistingClient("900000001-1",
                                List.of(new ExistingRecords.ExistingSite("Otra sede", List.of("Consultorio", "Enfermería")))),
                        new ExistingRecords.ExistingClient("800000002-2",
                                List.of(new ExistingRecords.ExistingSite("Principal", List.of())))),
                List.of());

        DataLoadReport informe = servicio.validate(List.of(CATALOGO, CLIENTE));

        assertThat(yaEstan(informe, "clientes")).isEqualTo(1);
        assertThat(yaEstan(informe, "sedes")).describedAs("«Principal» es de otro cliente").isZero();
        assertThat(yaEstan(informe, "areas")).describedAs("«Consultorio» es de otra sede").isZero();
    }

    @Test
    @DisplayName("un modelo es de su tipo y su marca: el mismo nombre en otra marca es otro modelo")
    void unModeloEsDeSuMarca() {
        existentes = new ExistingRecords(List.of(), List.of(), List.of(), List.of(),
                List.of(new ExistingRecords.ModelName("Tensiómetro análogo", "Otra marca", "DS44")),
                List.of(), List.of(), List.of(), List.of());

        assertThat(yaEstan(servicio.validate(List.of(CATALOGO)), "modelos")).isZero();
    }

    @Test
    @DisplayName("un equipo sin serie de un cliente nuevo entra con aviso de serie provisional")
    void sinSerieClienteNuevo() {
        DataLoadReport informe = servicio.validate(List.of(CATALOGO, CLIENTE));

        assertThat(informe.valida()).isTrue();
        assertThat(informe.avisos()).singleElement().extracting(DataLoadProblem::mensaje).asString()
                .contains("Un equipo entra sin serie").contains("provisional");
    }

    @Test
    @DisplayName("un equipo sin serie de un cliente que ya existia se rechaza: no hay con que saber si ya esta")
    void sinSerieClienteQueYaEsta() {
        existentes = new ExistingRecords(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new ExistingRecords.ExistingClient("900000001-1", List.of())), List.of());

        DataLoadReport informe = servicio.validate(List.of(CATALOGO, CLIENTE));

        assertThat(informe.errores()).singleElement().satisfies(e -> {
            assertThat(e.ruta()).isEqualTo("/equipos/1");
            assertThat(e.mensaje()).contains("«eq-0102»").contains("no tiene serie");
        });
        assertThat(informe.avisos()).isEmpty();
    }

    @Test
    @DisplayName("sin archivos, o con mas de tres, es un error de la carga entera")
    void cuantosArchivos() {
        assertThat(servicio.validate(List.of()).errores()).singleElement()
                .satisfies(e -> assertThat(e.archivo()).isNull())
                .extracting(DataLoadProblem::mensaje).asString().contains("de uno a tres archivos");
        assertThat(servicio.validate(Collections.nCopies(4, CATALOGO)).valida()).isFalse();
    }

    @Test
    @DisplayName("dos archivos con el mismo contenido son un error, que nombra a los dos")
    void dosDelMismoContenido() {
        DataLoadFile otro = new DataLoadFile("otro-catalogo.json", CATALOGO.contenido());

        DataLoadReport informe = servicio.validate(List.of(CATALOGO, otro));

        assertThat(informe.errores()).singleElement().satisfies(e -> {
            assertThat(e.archivo()).isEqualTo("otro-catalogo.json");
            assertThat(e.mensaje()).contains("dos archivos de catalogo").contains(EjemplosDeCarga.CATALOGO);
        });
    }

    @Test
    @DisplayName("el historial solo, sin el catalogo ni su cliente, no resuelve sus ref: se suben juntos")
    void elHistorialSolo() {
        DataLoadReport informe = servicio.validate(List.of(HISTORIAL));

        assertThat(informe.valida()).isFalse();
        assertThat(informe.errores()).allSatisfy(e -> assertThat(e.mensaje()).contains("no está declarada"));
        assertThat(informe.errores()).extracting(DataLoadProblem::ruta).contains("/cliente", "/ordenes/0/sede", "/ordenes/0/ingeniero");
    }

    @Test
    @DisplayName("una ref que falta se dice una sola vez, en su primer uso, con cuantas veces se usa")
    void unaVezPorRef() {
        DataLoadReport informe = servicio.validate(List.of(HISTORIAL));

        assertThat(informe.errores()).extracting(DataLoadProblem::mensaje)
                .doesNotHaveDuplicates()
                .filteredOn(m -> m.contains("«prot-desinfeccion»")).singleElement().asString()
                .contains("se usa").contains("veces");
    }

    @Test
    @DisplayName("una ref declarada dos veces entre archivos distintos es un error en la segunda, que nombra la primera")
    void unaRefDosVeces() {
        String cliente = EjemplosDeCarga.texto(EjemplosDeCarga.CLIENTE).replaceFirst("\"ref\": \"eq-0101\"", "\"ref\": \"mod-ds44\"");
        assertThat(cliente).describedAs("El ejemplo tiene que traer el equipo eq-0101").doesNotContain("\"ref\": \"eq-0101\"");

        DataLoadReport informe = servicio.validate(List.of(CATALOGO, EjemplosDeCarga.archivo("cliente.json", cliente), HISTORIAL));

        assertThat(informe.errores()).filteredOn(e -> e.mensaje().contains("ya está declarada")).singleElement().satisfies(e -> {
            assertThat(e.archivo()).isEqualTo("cliente.json");
            assertThat(e.mensaje()).contains(EjemplosDeCarga.CATALOGO).contains("/catalogo/modelos/0/ref");
        });
    }

    @Test
    @DisplayName("una ref que nombra otra clase de cosa es un error: un modelo donde se espera un area")
    void otraClase() {
        String cliente = EjemplosDeCarga.texto(EjemplosDeCarga.CLIENTE).replaceFirst("\"area\": \"[^\"]+\"", "\"area\": \"mod-ds44\"");

        DataLoadReport informe = servicio.validate(List.of(CATALOGO, EjemplosDeCarga.archivo("cliente.json", cliente), HISTORIAL));

        assertThat(informe.errores()).singleElement().satisfies(e -> {
            assertThat(e.ruta()).isEqualTo("/equipos/0/area");
            assertThat(e.mensaje()).contains("es un modelo y aquí se espera un área de servicio");
        });
    }

    @Test
    @DisplayName("una ciudad sin tilde ni mayusculas es la del catalogo: no es error")
    void ciudadSinTildes() {
        assertThat(conCliente("\"nombre\": \"Bogotá\"", "\"nombre\": \"  BOGOTA \"").errores()).isEmpty();
    }

    @Test
    @DisplayName("una ciudad que no esta se rechaza sugiriendo la que mas se le parece")
    void ciudadConSugerencia() {
        assertThat(conCliente("\"nombre\": \"Bogotá\"", "\"nombre\": \"Bogota D.C.\"").errores()).singleElement()
                .satisfies(e -> {
                    assertThat(e.ruta()).isEqualTo("/clientes/0/sedes/0/ciudad/nombre");
                    assertThat(e.mensaje()).contains("no está en el catálogo de COL").contains("¿Es Bogotá?");
                });
        assertThat(conCliente("\"nombre\": \"Bogotá\"", "\"nombre\": \"Medelin\"").errores())
                .singleElement().extracting(DataLoadProblem::mensaje).asString().contains("¿Es Medellín?");
        assertThat(conCliente("\"nombre\": \"Bogotá\"", "\"nombre\": \"Tunja\"").errores())
                .singleElement().extracting(DataLoadProblem::mensaje).asString().doesNotContain("¿Es");
    }

    @Test
    @DisplayName("un pais que no esta en el catalogo es un error, y su ciudad no se busca")
    void paisDesconocido() {
        DataLoadReport informe = conCliente("\"pais\": \"COL\",\n            \"nombre\": \"Bogotá\"",
                "\"pais\": \"ZZZ\",\n            \"nombre\": \"Bogotá\"");

        assertThat(informe.errores()).singleElement().satisfies(e -> {
            assertThat(e.ruta()).isEqualTo("/clientes/0/sedes/0/ciudad/pais");
            assertThat(e.mensaje()).contains("«ZZZ»");
        });
    }

    @Test
    @DisplayName("dos patrones con la misma serie, sin tildes ni mayusculas, son un error en el segundo")
    void serieDePatronRepetida() {
        String catalogo = EjemplosDeCarga.texto(EjemplosDeCarga.CATALOGO);
        java.util.regex.Matcher series = java.util.regex.Pattern.compile("\"serie\": \"([^\"]+)\"").matcher(catalogo);
        assertThat(series.find()).isTrue();
        String primera = series.group(1);
        assertThat(series.find()).describedAs("El ejemplo tiene que traer dos patrones").isTrue();
        String repetida = catalogo.replace("\"serie\": \"" + series.group(1) + "\"", "\"serie\": \" " + primera.toLowerCase() + "\"");

        DataLoadReport informe = servicio.validate(List.of(EjemplosDeCarga.archivo("catalogo.json", repetida), CLIENTE, HISTORIAL));

        assertThat(informe.errores()).singleElement().satisfies(e -> {
            assertThat(e.ruta()).isEqualTo("/catalogo/patrones/1/serie");
            assertThat(e.mensaje()).contains("catalogo.json, /catalogo/patrones/0/serie");
        });
    }

    private DataLoadReport conClienteQueYaTieneOrdenes(String documento, String... fechas) {
        existentes = new ExistingRecords(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(INGENIERO),
                List.of(new ExistingRecords.ExistingClient(documento, List.of(),
                        Arrays.stream(fechas).map(LocalDate::parse).toList())),
                List.of(), List.of(INGENIERO));
        return servicio.validate(List.of(CATALOGO, CLIENTE, HISTORIAL));
    }

    private static List<DataLoadProblem> deLasOrdenes(DataLoadReport informe) {
        return informe.errores().stream().filter(e -> e.ruta() != null && e.ruta().startsWith("/ordenes")).toList();
    }

    @Test
    @DisplayName("un historial en un dia en que su cliente ya tiene una orden se rechaza: ya se cargo")
    void historialEnFechaOcupada() {
        DataLoadReport informe = conClienteQueYaTieneOrdenes("900000001-1", "2025-05-02");

        assertThat(deLasOrdenes(informe)).singleElement().satisfies(e -> {
            assertThat(e.archivo()).isEqualTo(EjemplosDeCarga.HISTORIAL);
            assertThat(e.ruta()).isEqualTo("/ordenes/1");
            assertThat(e.mensaje()).contains("Clínica Los Almendros").contains("2025-05-02").contains("no se carga dos veces")
                    .doesNotContain("otras");
        });
    }

    @Test
    @DisplayName("si chocan varias ordenes se dice la primera y cuantas mas, en un solo error")
    void historialConVariasFechasOcupadas() {
        DataLoadReport informe = conClienteQueYaTieneOrdenes("900000001-1", "2025-05-02", "2025-03-10");

        assertThat(deLasOrdenes(informe)).singleElement().satisfies(e -> {
            assertThat(e.ruta()).isEqualTo("/ordenes/0");
            assertThat(e.mensaje()).contains("2025-03-10").contains("otras 1 órdenes");
        });
    }

    @Test
    @DisplayName("las ordenes de otro dia, o de otro cliente en el mismo dia, no ocupan la fecha")
    void fechaLibre() {
        assertThat(deLasOrdenes(conClienteQueYaTieneOrdenes("900000001-1", "2025-03-11", "2025-05-01"))).isEmpty();
        assertThat(deLasOrdenes(conClienteQueYaTieneOrdenes("800000002-2", "2025-03-10", "2025-05-02"))).isEmpty();
    }

    @Test
    @DisplayName("el ingeniero que no esta en MalphasOS rechaza el historial, una vez por ingeniero")
    void ingenieroQueNoEsta() {
        existentes = new ExistingRecords(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());

        DataLoadReport informe = servicio.validate(List.of(CATALOGO, CLIENTE, HISTORIAL));

        assertThat(informe.errores()).singleElement().satisfies(e -> {
            assertThat(e.ruta()).isEqualTo("/ordenes/0/ingeniero");
            assertThat(e.mensaje()).contains("«per-ing-1»").contains("todavía no está en MalphasOS")
                    .contains("Lo nombran 2 órdenes");
        });
    }

    @Test
    @DisplayName("el ingeniero sin firma guardada rechaza el historial: el reporte se congela al cargar")
    void ingenieroSinFirma() {
        existentes = new ExistingRecords(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(INGENIERO),
                List.of(), List.of(), List.of("1000000002"));

        assertThat(servicio.validate(List.of(CATALOGO, CLIENTE, HISTORIAL)).errores()).singleElement()
                .extracting(DataLoadProblem::mensaje).asString().contains("no tiene su firma guardada");
    }

    @Test
    @DisplayName("una orden la hace un ingeniero: un administrador con firma no vale")
    void ingenieroQueNoEsIngeniero() {
        String catalogo = EjemplosDeCarga.texto(EjemplosDeCarga.CATALOGO);
        String conAdmin = catalogo.replace("\"tipo\": \"ENGINEER\"", "\"tipo\": \"ADMIN\"");
        assertThat(conAdmin).describedAs("El ejemplo tiene que traer un ingeniero").isNotEqualTo(catalogo);

        DataLoadReport informe = servicio.validate(List.of(EjemplosDeCarga.archivo("catalogo.json", conAdmin), CLIENTE, HISTORIAL));

        assertThat(informe.errores()).singleElement().extracting(DataLoadProblem::mensaje).asString()
                .contains("no es ingeniero").contains("ADMIN");
    }

    private DataLoadReport conCatalogo(String de, String a) {
        String catalogo = EjemplosDeCarga.texto(EjemplosDeCarga.CATALOGO);
        String cambiado = catalogo.replaceFirst(de, a);
        assertThat(cambiado).describedAs("El ejemplo tiene que traer " + de).isNotEqualTo(catalogo);
        return servicio.validate(List.of(EjemplosDeCarga.archivo("catalogo.json", cambiado)));
    }

    @Test
    @DisplayName("una magnitud que no esta en el catalogo metrologico es un error, en su campo")
    void magnitudDesconocida() {
        assertThat(conCatalogo("\"magnitud\": \"presion\"", "\"magnitud\": \"presión\"").errores()).singleElement()
                .satisfies(e -> {
                    assertThat(e.ruta()).isEqualTo("/catalogo/tiposEquipo/0/verificaciones/0/magnitud");
                    assertThat(e.mensaje()).contains("«presión»").contains("código");
                });
    }

    @Test
    @DisplayName("una unidad de otra magnitud es un error que dice cuales tiene la suya")
    void unidadDeOtraMagnitud() {
        assertThat(conCatalogo("\"unidad\": \"mmHg\"", "\"unidad\": \"°C\"").errores()).singleElement()
                .satisfies(e -> {
                    assertThat(e.ruta()).isEqualTo("/catalogo/tiposEquipo/0/verificaciones/0/unidad");
                    assertThat(e.mensaje()).contains("«°C»").contains("mmHg, kPa");
                });
    }

    @Test
    @DisplayName("aplicar una carga valida escribe los archivos leidos, en el orden en que llegaron")
    void aplicarEscribe() {
        DataLoadReport informe = servicio.apply(List.of(HISTORIAL, CATALOGO, CLIENTE));

        assertThat(informe.valida()).isTrue();
        assertThat(escritos).extracting(ReadFile::nombre)
                .containsExactly(EjemplosDeCarga.HISTORIAL, EjemplosDeCarga.CATALOGO, EjemplosDeCarga.CLIENTE);
    }

    @Test
    @DisplayName("aplicar una carga con errores no escribe nada y devuelve el informe de la validacion")
    void aplicarConErroresNoEscribe() {
        existentes = new ExistingRecords(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());

        DataLoadReport informe = servicio.apply(List.of(CATALOGO, CLIENTE, HISTORIAL));

        assertThat(informe.valida()).isFalse();
        assertThat(informe.errores()).singleElement().extracting(DataLoadProblem::mensaje).asString()
                .contains("todavía no está en MalphasOS");
        assertThat(escritos).isNull();
    }

    @Test
    @DisplayName("el supervisor de un cliente es un supervisor: un ingeniero del catalogo no vale")
    void supervisorQueNoLoEs() {
        DataLoadReport informe = conCliente("\"persona\": \"per-sup-1\"", "\"persona\": \"per-ing-1\"");

        assertThat(informe.errores()).singleElement().satisfies(e -> {
            assertThat(e.ruta()).isEqualTo("/clientes/0/supervisores/0/persona");
            assertThat(e.mensaje()).contains("«per-ing-1»").contains("ENGINEER");
        });
    }

    @Test
    @DisplayName("los errores de cada archivo llegan al informe con el nombre de su archivo")
    void losErroresDeCadaArchivo() {
        DataLoadReport informe = servicio.validate(List.of(CATALOGO, EjemplosDeCarga.archivo("roto.json", "no es json")));

        assertThat(informe.valida()).isFalse();
        assertThat(informe.errores()).extracting(DataLoadProblem::archivo).containsExactly("roto.json");
    }
}
