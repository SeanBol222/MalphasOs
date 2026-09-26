package com.malphasos.malphasos.bootstrap.config.openApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.malphasos.malphasos.TestcontainersConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publica el contrato del API en {@code contracts/openapi/}, un archivo por grupo.
 *
 * <p><b>No es un volcado de conveniencia: es el mecanismo por el que un cambio de contrato se
 * revisa.</b> El archivo versionado es la línea base; si el API cambia, esta clase lo reescribe y
 * la diferencia aparece en {@code git status} para que alguien la mire antes de commitearla. Sin
 * eso, el frontend descubriría el cambio en ejecución, o no lo descubriría.
 *
 * <p>El frontend genera sus tipos de estos archivos y no de un servidor levantado: así compila sin
 * necesidad de tener el backend en marcha, y la versión del contrato que usa está fijada en el
 * repositorio en lugar de depender de qué hubiera corriendo ese día.
 *
 * <p>Escribe fuera del módulo, en la raíz del repositorio, que se localiza subiendo desde el
 * directorio de trabajo por la misma razón y con la misma solución que {@code RealmFixture}: ese
 * directorio no es el mismo al lanzar la batería desde la raíz que desde el módulo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiContractDumpTest {

    private static final String DESTINO = "contracts/openapi";

    @Autowired private MockMvc mockMvc;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"client", "equipment", "person", "location", "work-order", "reports"})
    @DisplayName("el grupo publica su contrato y queda escrito en contracts/openapi")
    void publicaSuContrato(String grupo) throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs/" + grupo))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode documento = JsonMapper.builder().build().readTree(json);

        assertThat(documento.path("openapi").asString())
                .describedAs("El documento debe declarar su version de OpenAPI")
                .startsWith("3.");

        escribir(grupo, documento);
    }

    /**
     * Escribe el contrato con sangría estable y las claves ordenadas.
     *
     * <p>Sin formatear, springdoc lo devuelve en una sola línea y cualquier cambio produce una
     * diferencia de un único renglón ilegible. Con sangría, la diferencia señala el campo que
     * cambió, que es para lo que se versiona.
     *
     * <p><b>Y sin ordenar las claves, el archivo cambiaba en cada corrida sin que el API cambiara.</b>
     * Descubierto el 2026-09-26: springdoc construye los códigos de respuesta en un mapa sin orden
     * garantizado, de modo que dos ejecuciones seguidas intercambiaban {@code 409} y {@code 500} y
     * producían 376 líneas de diferencia falsa. Un archivo que se ensucia solo no se puede revisar,
     * y revisarlo es la única razón por la que se versiona: el defecto vaciaba de sentido a esta
     * clase entera. {@code WRITE_PROPERTIES_SORTED} lo deja determinista.
     */
    private void escribir(String grupo, JsonNode documento) throws Exception {
        Path destino = raizDelRepositorio().resolve(DESTINO);
        Files.createDirectories(destino);

        String formateado = JsonMapper.builder()
                .enable(JsonNodeFeature.WRITE_PROPERTIES_SORTED)
                .build()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(documento);

        Files.writeString(destino.resolve(grupo + ".json"), formateado + "\n");
    }

    private static Path raizDelRepositorio() {
        Path directorio = Path.of("").toAbsolutePath();

        while (directorio != null) {
            if (Files.isDirectory(directorio.resolve(".git"))) {
                return directorio;
            }
            directorio = directorio.getParent();
        }

        throw new IllegalStateException(
                "No se encontro la raiz del repositorio subiendo desde " + Path.of("").toAbsolutePath());
    }
}
