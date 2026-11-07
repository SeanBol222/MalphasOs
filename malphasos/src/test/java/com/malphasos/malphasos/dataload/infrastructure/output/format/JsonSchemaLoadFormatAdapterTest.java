package com.malphasos.malphasos.dataload.infrastructure.output.format;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.dataload.EjemplosDeCarga;
import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import com.malphasos.malphasos.dataload.application.model.FormatCheck;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Cada archivo, contra el esquema publicado: lo que el conversor ya comprueba, comprobado otra vez aquí. */
class JsonSchemaLoadFormatAdapterTest {

    private final JsonSchemaLoadFormatAdapter adaptador = new JsonSchemaLoadFormatAdapter();

    @ParameterizedTest(name = "{0}")
    @CsvSource({"ejemplo-1-catalogo.json, catalogo", "ejemplo-2-cliente.json, cliente", "ejemplo-3-historial.json, historial"})
    @DisplayName("los tres ejemplos publicados cumplen el esquema y dicen qué contienen")
    void losEjemplosCumplen(String archivo, String contenido) {
        FormatCheck revision = adaptador.check(EjemplosDeCarga.archivo(archivo));

        assertThat(revision.errores()).isEmpty();
        assertThat(revision.contenido()).isEqualTo(contenido);
    }

    @Test
    @DisplayName("un archivo que no es JSON es un error del archivo entero, sin contenido")
    void noEsJson() {
        FormatCheck revision = adaptador.check(EjemplosDeCarga.archivo("roto.json", "{ \"formato\": "));

        assertThat(revision.contenido()).isNull();
        assertThat(revision.errores()).singleElement().satisfies(e -> {
            assertThat(e.archivo()).isEqualTo("roto.json");
            assertThat(e.ruta()).isNull();
            assertThat(e.mensaje()).startsWith("No es un JSON válido");
        });
    }

    @Test
    @DisplayName("un nombre de protocolo de 121 caracteres se rechaza con su ruta: el largo es el de MalphasOS")
    void unLargoDeMas() {
        String catalogo = EjemplosDeCarga.texto(EjemplosDeCarga.CATALOGO)
                .replaceFirst("\"nombre\": \"Desinfección técnica inicial\"", "\"nombre\": \"" + "a".repeat(121) + "\"");
        assertThat(catalogo).describedAs("El ejemplo tiene que traer ese protocolo").contains("a".repeat(121));

        FormatCheck revision = adaptador.check(EjemplosDeCarga.archivo("catalogo.json", catalogo));

        assertThat(revision.errores()).extracting(DataLoadProblem::ruta).containsExactly("/catalogo/protocolos/0/nombre");
        assertThat(revision.errores().getFirst().mensaje()).contains("120");
    }

    @Test
    @DisplayName("una fecha imposible no pasa: los formatos son reglas, no anotaciones")
    void unaFechaImposible() {
        String historial = EjemplosDeCarga.texto(EjemplosDeCarga.HISTORIAL).replaceFirst("\"fecha\": \"[0-9-]+\"", "\"fecha\": \"2025-02-30\"");
        assertThat(historial).contains("2025-02-30");

        FormatCheck revision = adaptador.check(EjemplosDeCarga.archivo("historial.json", historial));

        assertThat(revision.errores()).extracting(DataLoadProblem::ruta).containsExactly("/ordenes/0/fecha");
    }

    @Test
    @DisplayName("un contenido que el formato no conoce no se toma como contenido")
    void contenidoDesconocido() {
        FormatCheck revision = adaptador.check(EjemplosDeCarga.archivo("x.json",
                "{\"formato\": \"malphasos-carga/1\", \"contenido\": \"facturas\"}"));

        assertThat(revision.contenido()).isNull();
        assertThat(revision.errores()).isNotEmpty();
    }
}
