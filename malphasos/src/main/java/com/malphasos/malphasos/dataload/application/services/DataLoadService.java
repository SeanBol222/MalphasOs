package com.malphasos.malphasos.dataload.application.services;

import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import com.malphasos.malphasos.dataload.application.model.DataLoadReport;
import com.malphasos.malphasos.dataload.application.model.FormatCheck;
import com.malphasos.malphasos.dataload.application.model.ReadFile;
import com.malphasos.malphasos.dataload.application.model.RefMention;
import com.malphasos.malphasos.dataload.application.model.SectionCount;
import com.malphasos.malphasos.dataload.application.ports.input.DataLoadServicePort;
import com.malphasos.malphasos.dataload.application.ports.output.ExistingRecordsPort;
import com.malphasos.malphasos.dataload.application.ports.output.LoadFormatPort;
import com.malphasos.malphasos.dataload.application.ports.output.LoadWriterPort;
import com.malphasos.malphasos.dataload.application.ports.output.LocationCatalogPort;
import com.malphasos.malphasos.dataload.application.ports.output.MetrologyCatalogPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Valida una carga: primero cada archivo contra el esquema, después lo que solo se ve mirando los
 * archivos juntos.
 *
 * <p>Una carga lleva de uno a tres archivos, y como mucho uno de cada contenido: el catálogo, un
 * cliente y su historial. Los que se nombran entre sí se suben juntos, porque una {@code ref} solo
 * enlaza dentro de una carga: por eso el historial de un cliente se sube con el catálogo y con ese
 * cliente, aunque ya se hubieran cargado antes.
 */
@Service
@RequiredArgsConstructor
public class DataLoadService implements DataLoadServicePort {

    static final int MAXIMO_DE_ARCHIVOS = 3;

    private final LoadFormatPort loadFormatPort;
    private final LocationCatalogPort locationCatalogPort;
    private final ExistingRecordsPort existingRecordsPort;
    private final MetrologyCatalogPort metrologyCatalogPort;
    private final LoadWriterPort loadWriterPort;

    /** Lo que dice la validación, y los archivos leídos que la cumplieron. */
    private record Revision(DataLoadReport informe, List<ReadFile> leidos) {}

    @Override
    @Transactional(readOnly = true)
    public DataLoadReport validate(List<DataLoadFile> archivos) {
        return revisar(archivos).informe();
    }

    /**
     * Valida otra vez y, sin errores, escribe todo en una transacción. Se valida otra vez porque entre
     * validar y aplicar pudo cambiar lo que hay, y porque nada impide llamar a aplicar sin haber validado.
     */
    @Override
    @Transactional
    public DataLoadReport apply(List<DataLoadFile> archivos) {
        Revision revision = revisar(archivos);
        if (revision.informe().valida()) {
            loadWriterPort.write(revision.leidos());
        }
        return revision.informe();
    }

    private Revision revisar(List<DataLoadFile> archivos) {
        List<DataLoadProblem> errores = new ArrayList<>();

        if (archivos.isEmpty() || archivos.size() > MAXIMO_DE_ARCHIVOS) {
            errores.add(DataLoadProblem.of(null, "Una carga lleva de uno a tres archivos: el catálogo, un cliente"
                    + " y su historial. Se recibieron " + archivos.size()));
            return new Revision(new DataLoadReport(errores, List.of()), List.of());
        }

        Map<String, String> porContenido = new LinkedHashMap<>();
        boolean repetido = false;
        List<RefMention> declaradas = new ArrayList<>();
        List<RefMention> usadas = new ArrayList<>();
        List<ReadFile> leidos = new ArrayList<>();
        for (DataLoadFile archivo : archivos) {
            FormatCheck revision = loadFormatPort.check(archivo);
            errores.addAll(revision.errores());
            declaradas.addAll(revision.declaradas());
            usadas.addAll(revision.usadas());
            if (revision.documento() != null) {
                leidos.add(new ReadFile(archivo.nombre(), revision.documento()));
            }

            String otro = revision.contenido() == null ? null : porContenido.putIfAbsent(revision.contenido(), archivo.nombre());
            if (otro != null) {
                repetido = true;
                errores.add(DataLoadProblem.of(archivo.nombre(), "La carga trae dos archivos de " + revision.contenido()
                        + ": este y " + otro + ". Una carga lleva como mucho uno de cada contenido"));
            }
        }

        // Con un contenido repetido, cada ref y cada patrón del segundo archivo saldrían duplicados: un
        // error por cada uno, que solo repetiría el de arriba.
        List<DataLoadProblem> avisos = new ArrayList<>();
        List<SectionCount> resumen = List.of();
        if (!repetido) {
            errores.addAll(comprobarRefs(declaradas, usadas));
            errores.addAll(new ContentRules(locationCatalogPort, metrologyCatalogPort).check(leidos));

            NaturalKeys existentes = new NaturalKeys(existingRecordsPort.read());
            existentes.check(leidos);
            errores.addAll(existentes.errores);
            avisos.addAll(existentes.avisos);
            resumen = existentes.resumen();
        }

        return new Revision(new DataLoadReport(errores, avisos, resumen), leidos);
    }

    /**
     * Cada ref se declara una sola vez en toda la carga, y cada una que se usa existe y es de la clase
     * que se espera allí. El esquema no lo puede ver: mira un archivo, y una ref enlaza tres.
     */
    static List<DataLoadProblem> comprobarRefs(List<RefMention> declaradas, List<RefMention> usadas) {
        List<DataLoadProblem> errores = new ArrayList<>();
        Map<String, RefMention> porRef = new LinkedHashMap<>();

        for (RefMention declarada : declaradas) {
            RefMention anterior = porRef.putIfAbsent(declarada.ref(), declarada);
            if (anterior != null) {
                errores.add(new DataLoadProblem(declarada.archivo(), declarada.ruta(), "La ref «" + declarada.ref()
                        + "» ya está declarada en " + anterior.archivo() + ", " + anterior.ruta()
                        + ": una ref es única entre los archivos de una carga"));
            }
        }

        // Una ref que falta se dice una vez, en su primer uso, con cuántos lleva: el historial subido sin el
        // catálogo nombra cada protocolo en cada reporte, y daría un error por cada paso.
        Map<String, List<RefMention>> sinDestino = new LinkedHashMap<>();
        for (RefMention usada : usadas) {
            RefMention destino = porRef.get(usada.ref());
            if (destino == null) {
                sinDestino.computeIfAbsent(usada.ref(), r -> new ArrayList<>()).add(usada);
            } else if (destino.clase() != usada.clase()) {
                errores.add(new DataLoadProblem(usada.archivo(), usada.ruta(), "La ref «" + usada.ref() + "» es "
                        + destino.clase().descripcion() + " y aquí se espera " + usada.clase().descripcion()));
            }
        }

        sinDestino.forEach((ref, usos) -> {
            RefMention primera = usos.getFirst();
            errores.add(new DataLoadProblem(primera.archivo(), primera.ruta(), "La ref «" + ref
                    + "» no está declarada en ningún archivo de esta carga. Se esperaba " + primera.clase().descripcion()
                    + (usos.size() > 1 ? ", y se usa " + usos.size() + " veces" : "")
                    + ": si es de una carga anterior, ese archivo se sube también"));
        });

        return errores;
    }
}
