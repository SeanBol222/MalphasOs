package com.malphasos.malphasos.dataload.application.services;

import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import com.malphasos.malphasos.dataload.application.model.DataLoadReport;
import com.malphasos.malphasos.dataload.application.model.FormatCheck;
import com.malphasos.malphasos.dataload.application.ports.input.DataLoadServicePort;
import com.malphasos.malphasos.dataload.application.ports.output.LoadFormatPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Valida una carga: primero cada archivo contra el esquema, después lo que solo se ve mirando los
 * archivos juntos.
 *
 * <p>Una carga lleva de uno a tres archivos, y como mucho uno de cada contenido: el catálogo, un
 * cliente y su historial. Los que se nombran entre sí se suben juntos, porque una {@code ref} solo
 * enlaza dentro de una carga.
 */
@Service
@RequiredArgsConstructor
public class DataLoadService implements DataLoadServicePort {

    static final int MAXIMO_DE_ARCHIVOS = 3;

    private final LoadFormatPort loadFormatPort;

    @Override
    public DataLoadReport validate(List<DataLoadFile> archivos) {
        List<DataLoadProblem> errores = new ArrayList<>();

        if (archivos.isEmpty() || archivos.size() > MAXIMO_DE_ARCHIVOS) {
            errores.add(DataLoadProblem.of(null, "Una carga lleva de uno a tres archivos: el catálogo, un cliente"
                    + " y su historial. Se recibieron " + archivos.size()));
            return new DataLoadReport(errores, List.of());
        }

        Map<String, String> porContenido = new LinkedHashMap<>();
        for (DataLoadFile archivo : archivos) {
            FormatCheck revision = loadFormatPort.check(archivo);
            errores.addAll(revision.errores());

            String otro = revision.contenido() == null ? null : porContenido.putIfAbsent(revision.contenido(), archivo.nombre());
            if (otro != null) {
                errores.add(DataLoadProblem.of(archivo.nombre(), "La carga trae dos archivos de " + revision.contenido()
                        + ": este y " + otro + ". Una carga lleva como mucho uno de cada contenido"));
            }
        }

        return new DataLoadReport(errores, List.of());
    }
}
