package com.malphasos.malphasos.shared.domain.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Invariante estructural de todos los agregados: <b>la igualdad se define por identidad</b>.
 *
 * <p><b>Existe porque una mutación lo destapó el 2026-10-04.</b> Se quitó
 * {@code onlyExplicitlyIncluded = true} de {@code EquipmentType} —de modo que su igualdad pasaba a
 * compararse por todos sus datos— y la batería entera siguió verde: 856 pruebas. Al contarlo,
 * <b>diecisiete clases</b> usaban esa anotación y solo <b>dos</b> tenían una aserción de igualdad por
 * identidad, {@code City} y {@code Brand}.
 *
 * <p><b>Y la prueba que parecía cubrirlo mentía en su nombre.</b>
 * {@code CatalogAggregatesTest.identidadYRehidratacion} se llama «los seis agregados comparan por
 * identidad, y rehidratar no emite» y de los seis solo afirma la igualdad de {@code Brand}: de los otros
 * cinco comprueba únicamente que rehidratar no emita eventos. Un nombre que afirma más que el cuerpo es
 * peor que un nombre vago, porque se lee en una revisión y se da por hecho.
 *
 * <p><b>Por qué esto lee el código fuente y no usa reflexión</b>, que es lo que haría cualquiera:
 * {@code @EqualsAndHashCode} de Lombok es {@code @Retention(RetentionPolicy.SOURCE)} —comprobado sobre
 * {@code lombok-1.18.30.jar} con {@code javap}—, de modo que <b>no existe en el bytecode</b> y
 * {@code Class.getAnnotation} devuelve {@code null}. El invariante vive en la forma del código, así que
 * se comprueba donde vive. {@code RestAuthorizationCoverageTest} sí usa reflexión porque las anotaciones
 * de Spring son {@code RUNTIME}.
 *
 * <p>Lo que se exige, y por qué cada cosa:
 *
 * <ul>
 *   <li><b>{@code onlyExplicitlyIncluded = true}</b>. Sin eso Lombok compara todos los campos, y dos
 *       agregados con los mismos datos resultan iguales: afecta a {@code contains}, a {@code Set} y a
 *       cualquier {@code distinct()} sobre una colección del dominio.
 *   <li><b>Nunca {@code callSuper = true}</b>. Es el defecto exacto del sistema original:
 *       {@code AggregateRoot} no redefine {@code equals}, de modo que la comparación acabaría en la
 *       identidad de {@code Object} y <b>dos agregados con los mismos datos nunca serían iguales</b>.
 *       Las dos variantes están prohibidas y por razones opuestas.
 *   <li><b>Exactamente un {@code @EqualsAndHashCode.Include}</b>. Dos campos incluidos vuelven a ser
 *       igualdad por datos con más pasos.
 * </ul>
 */
class AggregateIdentityContractTest {

    /** Sube desde el directorio de trabajo hasta encontrar el módulo, como hace {@code RealmFixture}. */
    private static Path raizDeFuentes() {
        Path actual = Path.of("").toAbsolutePath();

        while (actual != null) {
            Path candidata = actual.resolve("src/main/java/com/malphasos/malphasos");

            if (Files.isDirectory(candidata)) {
                return candidata;
            }

            actual = actual.getParent();
        }

        throw new IllegalStateException("No se encontro src/main/java subiendo desde " + Path.of("").toAbsolutePath());
    }

    private static List<Path> agregados() throws IOException {
        try (Stream<Path> ficheros = Files.walk(raizDeFuentes())) {
            List<Path> encontrados = new ArrayList<>();

            for (Path fichero : ficheros.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (Files.readString(fichero, StandardCharsets.UTF_8).contains("extends AggregateRoot")) {
                    encontrados.add(fichero);
                }
            }

            return encontrados;
        }
    }

    private static final Pattern ANOTACION =
            Pattern.compile("@EqualsAndHashCode(\\(([^)]*)\\))?\\s*(?=\\n\\s*(public|@|final|abstract))");

    @Test
    @DisplayName("todos los agregados comparan por identidad, y son mas de los que nadie habia contado")
    void todosComparanPorIdentidad() throws IOException {
        List<Path> agregados = agregados();

        // Si este numero baja, alguien borro un agregado o cambio la herencia: las dos cosas hay que
        // mirarlas. Si sube, el agregado nuevo ya esta cubierto por esta prueba sin tocar nada.
        assertThat(agregados)
                .describedAs("Clases que extienden AggregateRoot")
                .hasSizeGreaterThanOrEqualTo(14);

        List<String> incumplen = new ArrayList<>();

        for (Path fichero : agregados) {
            String fuente = Files.readString(fichero, StandardCharsets.UTF_8);
            String nombre = fichero.getFileName().toString();
            Matcher anotacion = ANOTACION.matcher(fuente);

            if (!anotacion.find()) {
                incumplen.add(nombre + ": no declara @EqualsAndHashCode sobre la clase");
                continue;
            }

            String argumentos = anotacion.group(2) == null ? "" : anotacion.group(2);

            if (!argumentos.contains("onlyExplicitlyIncluded = true")) {
                incumplen.add(nombre + ": compara por datos, le falta onlyExplicitlyIncluded = true");
            }

            if (argumentos.contains("callSuper = true")) {
                incumplen.add(nombre + ": callSuper = true acaba en la identidad de Object");
            }

            long incluidos = fuente.lines().filter(l -> l.contains("@EqualsAndHashCode.Include")).count();

            if (incluidos != 1) {
                incumplen.add(nombre + ": tiene " + incluidos + " campos incluidos y debe tener 1");
            }
        }

        assertThat(incumplen)
                .describedAs("La igualdad de un agregado se define solo por su identidad")
                .isEmpty();
    }
}
