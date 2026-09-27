package com.malphasos.malphasos.location;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprueba el contenido de la migración {@code V7}, la que siembra países y ciudades.
 *
 * <p><b>Lee el archivo y no la base de datos, y es a propósito.</b> {@link LocationSchemaTest} hace
 * {@code DELETE FROM ciudad} y {@code DELETE FROM pais} después de cada uno de sus métodos, de modo
 * que en una ejecución completa los datos de referencia desaparecen del contenedor: una prueba que
 * contara filas pasaría o fallaría según el orden en que se ejecutaran las clases, que es la peor
 * clase de prueba. Aquí se verifica lo que el archivo dice, que es lo que se aplica en cada
 * instalación.
 *
 * <p>Lo que se fija es lo que un error de generación rompería sin que nadie se enterara: un nombre más
 * largo de lo que la columna admite, un código ISO mal formado, o dos ciudades con el mismo nombre en
 * el mismo país —que el esquema rechaza y dejaría la migración a medias, con la aplicación sin
 * arrancar—.
 */
class LocationSeedDataTest {

    private static final String MIGRACION = "src/main/resources/db/migration/V7__seed_location_reference_data.sql";

    /** Lo que la columna admite: {@code varchar(50)} en las dos tablas. */
    private static final int LARGO_MAXIMO = 50;

    private static final String SQL = leerMigracion();

    @Nested
    @DisplayName("Los países")
    class Paises {

        private final List<String[]> filas = filasDeDosCampos(bloque(0));

        @Test
        @DisplayName("son los 249 de la ISO 3166-1")
        void sonLosDeLaIso() {
            assertThat(filas).hasSize(249);
        }

        @Test
        @DisplayName("llevan código alfa-3 en mayúsculas, que es lo que el CHECK exige")
        void codigoValido() {
            assertThat(filas).allSatisfy(fila -> assertThat(fila[0]).matches("^[A-Z]{3}$"));
        }

        @Test
        @DisplayName("no repiten código ni nombre: el esquema declara únicos los dos")
        void sinRepetidos() {
            assertThat(filas.stream().map(fila -> fila[0])).doesNotHaveDuplicates();
            assertThat(filas.stream().map(fila -> fila[1])).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("caben en la columna")
        void caben() {
            assertThat(filas).allSatisfy(fila -> assertThat(fila[1]).hasSizeLessThanOrEqualTo(LARGO_MAXIMO));
        }

        @Test
        @DisplayName("están en español, que es lo que se lee en un desplegable")
        void enEspanol() {
            // Tres que cambian de nombre al traducirlos: si alguien regenerara el archivo sin la
            // traduccion de iso-codes, volverian a decir Germany, Netherlands y United States.
            List<String> nombres = filas.stream().map(fila -> fila[1]).toList();

            assertThat(nombres).contains("Alemania", "Países Bajos", "Estados Unidos", "Colombia");
            assertThat(nombres).doesNotContain("Germany", "Netherlands", "United States");
        }
    }

    @Nested
    @DisplayName("Los municipios de Colombia")
    class Municipios {

        private final List<String> nombres =
                filasDeUnCampo(bloque(1)).stream().map(fila -> fila[0]).toList();

        @Test
        @DisplayName("son más de mil, que es el orden de magnitud real del país")
        void sonMasDeMil() {
            assertThat(nombres).hasSizeGreaterThan(1000);
        }

        @Test
        @DisplayName("no se repiten, porque el esquema exige nombre único dentro del país")
        void sinRepetidos() {
            // Es la restriccion que obligo a desambiguar: Colombia tiene cuatro municipios llamados
            // La Union. Si dos llegaran iguales, la migracion fallaria a medias y la aplicacion no
            // arrancaria.
            assertThat(nombres).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("los repetidos llevan su departamento entre paréntesis")
        void desambiguados() {
            assertThat(nombres)
                    .contains("La Unión (Antioquia)", "La Unión (Nariño)", "Villanueva (Casanare)");
            // Y los que no se repiten, no: anadirlo a todos haria ilegible el desplegable.
            assertThat(nombres).contains("Medellín", "Bogotá", "Leticia");
        }

        @Test
        @DisplayName("caben en la columna, incluso con el departamento añadido")
        void caben() {
            assertThat(nombres).allSatisfy(nombre -> assertThat(nombre).hasSizeLessThanOrEqualTo(LARGO_MAXIMO));
        }
    }

    @Nested
    @DisplayName("Las capitales del resto del mundo")
    class Capitales {

        private final List<String[]> filas = filasDeDosCampos(bloque(2));

        @Test
        @DisplayName("hay una por país, o más si el país declara varias")
        void hayUnaPorPais() {
            assertThat(filas).hasSizeGreaterThan(200);
        }

        @Test
        @DisplayName("no incluyen Colombia, que entra con todos sus municipios")
        void sinColombia() {
            assertThat(filas.stream().map(fila -> fila[0])).doesNotContain("COL");
        }

        @Test
        @DisplayName("no repiten ciudad dentro del mismo país")
        void sinRepetidosPorPais() {
            assertThat(filas.stream().map(fila -> fila[0] + "|" + fila[1])).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("caben en la columna")
        void caben() {
            assertThat(filas).allSatisfy(fila -> assertThat(fila[1]).hasSizeLessThanOrEqualTo(LARGO_MAXIMO));
        }
    }

    @Test
    @DisplayName("las tres inserciones toleran que los datos ya existan")
    void idempotente() {
        // Sin esto, aplicarla sobre una base de datos donde alguien ya dio de alta Colombia a mano
        // reventaria el arranque entero por una fila.
        //
        // Se cuenta la sentencia con su punto y coma, no la frase: la cabecera del archivo tambien la
        // nombra al explicar por que esta, y contar la frase daba cuatro.
        long sentencias = Pattern.compile("ON CONFLICT DO NOTHING;").matcher(SQL).results().count();

        assertThat(sentencias).isEqualTo(3);
    }

    // ---------------------------------------------------------------------------

    /** El contenido del bloque {@code VALUES} número {@code indice}, en orden de aparición. */
    private static String bloque(int indice) {
        Matcher bloques = Pattern.compile("FROM \\(VALUES(.*?)\\) AS datos", Pattern.DOTALL).matcher(SQL);
        List<String> encontrados = new ArrayList<>();

        while (bloques.find()) {
            encontrados.add(bloques.group(1));
        }

        if (encontrados.size() != 3) {
            throw new IllegalStateException(
                    "Se esperaban tres bloques VALUES en la migracion y hay " + encontrados.size());
        }

        return encontrados.get(indice);
    }

    private static List<String[]> filasDeDosCampos(String bloque) {
        Matcher filas = Pattern.compile("\\('([^']*)', '((?:[^']|'')*)'\\)").matcher(bloque);
        List<String[]> resultado = new ArrayList<>();

        while (filas.find()) {
            resultado.add(new String[] {filas.group(1), filas.group(2).replace("''", "'")});
        }

        return resultado;
    }

    private static List<String[]> filasDeUnCampo(String bloque) {
        Matcher filas = Pattern.compile("\\('((?:[^']|'')*)'\\)").matcher(bloque);
        List<String[]> resultado = new ArrayList<>();

        while (filas.find()) {
            resultado.add(new String[] {filas.group(1).replace("''", "'")});
        }

        return resultado;
    }

    private static String leerMigracion() {
        try {
            return Files.readString(raizDelModulo().resolve(MIGRACION), StandardCharsets.UTF_8);
        } catch (IOException fallo) {
            throw new IllegalStateException("No se pudo leer " + MIGRACION, fallo);
        }
    }

    /**
     * La raíz del módulo, subiendo si hace falta.
     *
     * <p>Misma razón y misma solución que {@code RealmFixture}: el directorio de trabajo no es el mismo
     * al lanzar la batería desde la raíz del repositorio que desde el módulo.
     */
    private static Path raizDelModulo() {
        Path directorio = Path.of("").toAbsolutePath();

        if (Files.exists(directorio.resolve(MIGRACION))) {
            return directorio;
        }

        return directorio.resolve("malphasos");
    }
}
