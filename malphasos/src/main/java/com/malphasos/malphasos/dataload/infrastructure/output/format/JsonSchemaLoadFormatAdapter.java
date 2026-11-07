package com.malphasos.malphasos.dataload.infrastructure.output.format;

import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import com.malphasos.malphasos.dataload.application.model.FormatCheck;
import com.malphasos.malphasos.dataload.application.ports.output.LoadFormatPort;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Comprueba cada archivo contra el esquema publicado, con la misma definición que usa el conversor.
 *
 * <p>El esquema vive en {@code contracts/carga} y aquí hay una copia en el classpath, porque la imagen
 * del backend se construye sin ver el resto del repositorio. {@code CargaSchemaCopyTest} falla si las
 * dos difieren: una sola fuente de verdad, vigilada.
 *
 * <p>Los formatos —fecha, correo— se comprueban como reglas y no como anotaciones, que es lo que
 * JSON Schema 2020-12 haría por defecto: una fecha imposible no debe pasar la validación.
 */
@Component
public class JsonSchemaLoadFormatAdapter implements LoadFormatPort {

    static final String ESQUEMA = "/carga/malphasos-carga-1.schema.json";
    private static final Set<String> CONTENIDOS = Set.of("catalogo", "cliente", "historial");
    private static final Locale ESPANOL = Locale.forLanguageTag("es");

    private final JsonMapper json = JsonMapper.builder().build();
    private final Schema esquema = cargarEsquema();

    private static Schema cargarEsquema() {
        try (InputStream entrada = JsonSchemaLoadFormatAdapter.class.getResourceAsStream(ESQUEMA)) {
            if (entrada == null) {
                throw new IllegalStateException("No esta en el classpath el esquema de la carga: " + ESQUEMA);
            }
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(entrada);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el esquema de la carga", e);
        }
    }

    @Override
    public FormatCheck check(DataLoadFile archivo) {
        JsonNode documento;
        try {
            documento = json.readTree(archivo.contenido());
        } catch (JacksonException e) {
            return new FormatCheck(null, List.of(DataLoadProblem.of(archivo.nombre(),
                    "No es un JSON válido: " + e.getOriginalMessage())));
        }

        List<Error> incumplidos = esquema.validate(documento, contexto -> contexto.executionConfig(config -> config
                .formatAssertionsEnabled(true)
                .locale(ESPANOL)));

        String contenido = documento.path("contenido").asString(null);
        return new FormatCheck(CONTENIDOS.contains(contenido) ? contenido : null, incumplidos.stream()
                .map(e -> new DataLoadProblem(archivo.nombre(), e.getInstanceLocation().toString(), e.getMessage()))
                .toList());
    }
}
