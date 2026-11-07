package com.malphasos.malphasos.dataload.infrastructure.input.model.response;

import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import com.malphasos.malphasos.dataload.application.model.DataLoadReport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** El informe de una carga: si se puede aplicar, sus errores y sus avisos. */
@Schema(name = "DataLoadReportResponse")
public record DataLoadReportResponse(
        @Schema(description = "Sin errores: se puede aplicar") boolean valida,
        @Schema(description = "Lo que impide aplicarla") List<Problema> errores,
        @Schema(description = "Lo que conviene leer y no detiene nada") List<Problema> avisos) {

    @Schema(name = "DataLoadProblemResponse")
    public record Problema(
            @Schema(description = "El archivo, o nulo si es de la carga entera") String archivo,
            @Schema(description = "Dónde dentro del archivo, como puntero JSON: /catalogo/protocolos/9/nombre; nulo si es del archivo entero")
            String ruta,
            String mensaje) {

        static Problema from(DataLoadProblem problema) {
            return new Problema(problema.archivo(), problema.ruta(), problema.mensaje());
        }
    }

    public static DataLoadReportResponse from(DataLoadReport informe) {
        return new DataLoadReportResponse(informe.valida(),
                informe.errores().stream().map(Problema::from).toList(),
                informe.avisos().stream().map(Problema::from).toList());
    }
}
