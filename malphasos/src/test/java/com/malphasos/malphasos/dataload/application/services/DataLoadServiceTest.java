package com.malphasos.malphasos.dataload.application.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.malphasos.malphasos.dataload.EjemplosDeCarga;
import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import com.malphasos.malphasos.dataload.application.model.DataLoadReport;
import com.malphasos.malphasos.dataload.infrastructure.output.format.JsonSchemaLoadFormatAdapter;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La validación de una carga con el esquema de verdad: lo que se ve mirando los archivos juntos. */
class DataLoadServiceTest {

    private final DataLoadService servicio = new DataLoadService(new JsonSchemaLoadFormatAdapter());

    private static final DataLoadFile CATALOGO = EjemplosDeCarga.archivo(EjemplosDeCarga.CATALOGO);
    private static final DataLoadFile CLIENTE = EjemplosDeCarga.archivo(EjemplosDeCarga.CLIENTE);
    private static final DataLoadFile HISTORIAL = EjemplosDeCarga.archivo(EjemplosDeCarga.HISTORIAL);

    @Test
    @DisplayName("los tres ejemplos juntos son una carga valida, sin errores ni avisos")
    void losTresEjemplos() {
        DataLoadReport informe = servicio.validate(List.of(CATALOGO, CLIENTE, HISTORIAL));

        assertThat(informe.valida()).isTrue();
        assertThat(informe.errores()).isEmpty();
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
    @DisplayName("los errores de cada archivo llegan al informe con el nombre de su archivo")
    void losErroresDeCadaArchivo() {
        DataLoadReport informe = servicio.validate(List.of(CATALOGO, EjemplosDeCarga.archivo("roto.json", "no es json")));

        assertThat(informe.valida()).isFalse();
        assertThat(informe.errores()).extracting(DataLoadProblem::archivo).containsExactly("roto.json");
    }
}
