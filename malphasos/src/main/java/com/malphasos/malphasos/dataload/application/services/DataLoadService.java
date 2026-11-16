package com.malphasos.malphasos.dataload.application.services;

import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import com.malphasos.malphasos.dataload.application.model.DataLoadReport;
import com.malphasos.malphasos.dataload.application.model.FormatCheck;
import com.malphasos.malphasos.dataload.application.model.LoadDocument;
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
 * <p>Una carga lleva el catálogo, como mucho uno, y cualquier número de clientes con su historial, sueltos
 * o en un ZIP. Desde el 2026-11-16 la carga entera de un sistema es <b>una</b> carga: hasta entonces eran
 * de uno a tres archivos, y el catálogo se subía otra vez con cada cliente, porque una {@code ref} solo
 * enlaza dentro de una carga. Sigue siendo así, y por eso conviene subirlo todo junto.
 */
@Service
@RequiredArgsConstructor
public class DataLoadService implements DataLoadServicePort {

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

    private Revision revisar(List<DataLoadFile> subidos) {
        LoadPackage paquete = new LoadPackage(subidos);
        List<DataLoadFile> archivos = paquete.archivos;
        List<DataLoadProblem> errores = new ArrayList<>(paquete.errores);
        List<DataLoadProblem> avisos = new ArrayList<>(paquete.avisos);

        if (archivos.isEmpty() || archivos.size() > LoadPackage.MAXIMO_DE_ARCHIVOS) {
            errores.add(DataLoadProblem.of(null, "Una carga lleva de 1 a " + LoadPackage.MAXIMO_DE_ARCHIVOS
                    + " archivos: el catálogo y cada cliente con su historial, sueltos o en un ZIP. Se recibieron "
                    + archivos.size()));
        }
        if (!errores.isEmpty()) {
            return new Revision(new DataLoadReport(errores, avisos), List.of());
        }

        // Lo que una carga no puede traer dos veces: el catálogo, un cliente o el historial de un cliente.
        // Repetido, cada ref suya saldría duplicada: un error por cada una, que solo repetiría este.
        Map<String, String> unicos = new LinkedHashMap<>();
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

            for (String[] clave : unicos(revision)) {
                String otro = unicos.putIfAbsent(clave[0], archivo.nombre());
                if (otro != null) {
                    repetido = true;
                    errores.add(DataLoadProblem.of(archivo.nombre(), "La carga trae " + clave[1] + " dos veces: aquí y en "
                            + otro + ". Cada cosa va en un solo archivo"));
                }
            }
        }

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

    /** Lo que de un archivo no puede estar en otro, con cómo decirlo: la llave y su descripción. */
    private static List<String[]> unicos(FormatCheck revision) {
        List<String[]> claves = new ArrayList<>();
        if ("catalogo".equals(revision.contenido())) {
            claves.add(new String[] {"catalogo", "el catálogo"});
        }
        LoadDocument documento = revision.documento();
        if (documento == null) {
            return claves;
        }
        if ("historial".equals(revision.contenido()) && documento.cliente() != null) {
            claves.add(new String[] {"historial|" + documento.cliente(), "el historial del cliente «" + documento.cliente() + "»"});
        }
        documento.clientes().forEach(c -> claves.add(new String[] {"cliente|" + c.documento().trim(),
                "el cliente con documento " + c.documento().trim()}));
        return claves;
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
