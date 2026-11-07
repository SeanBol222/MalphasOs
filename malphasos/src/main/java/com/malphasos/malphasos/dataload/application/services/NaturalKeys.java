package com.malphasos.malphasos.dataload.application.services;

import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import com.malphasos.malphasos.dataload.application.model.ExistingRecords;
import com.malphasos.malphasos.dataload.application.model.LoadDocument;
import com.malphasos.malphasos.dataload.application.model.ReadFile;
import com.malphasos.malphasos.dataload.application.model.SectionCount;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Lo que la carga trae y MalphasOS ya tiene, reconocido por su llave natural —la tabla de la guía—, y
 * que se ignorará al aplicar. Los nombres se comparan sin tildes, sin mayúsculas y sin espacios de sobra.
 *
 * <p>Las órdenes y los reportes no tienen llave natural y siempre son nuevos. Un equipo sin serie
 * tampoco la tiene: de un cliente nuevo entra con serie provisional, y de uno que ya existía se rechaza,
 * porque no hay con qué saber si ya está.
 */
final class NaturalKeys {

    private final ExistingRecords existentes;
    private final Map<String, Integer[]> conteo = new LinkedHashMap<>();
    final List<DataLoadProblem> errores = new ArrayList<>();
    final List<DataLoadProblem> avisos = new ArrayList<>();

    NaturalKeys(ExistingRecords existentes) {
        this.existentes = existentes;
    }

    List<SectionCount> resumen() {
        return conteo.entrySet().stream()
                .map(e -> new SectionCount(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    void check(List<ReadFile> archivos) {
        Set<String> marcas = planos(existentes.marcas());
        Set<String> fabricantes = planos(existentes.fabricantes());
        Set<String> protocolos = planos(existentes.protocolos());
        Set<String> tipos = planos(existentes.tiposEquipo());
        Set<String> modelos = existentes.modelos().stream().map(NaturalKeys::clave).collect(Collectors.toSet());
        Set<String> series = planos(existentes.seriesDePatrones());
        Set<String> cedulas = existentes.cedulas().stream().map(String::trim).collect(Collectors.toSet());
        Map<String, ExistingRecords.ExistingClient> clientes = existentes.clientes().stream()
                .collect(Collectors.toMap(c -> c.documento().trim(), c -> c, (a, b) -> a));
        Set<String> equipos = existentes.equipos().stream().filter(e -> e.serie() != null)
                .map(e -> clave(e.modelo()) + "|" + Names.plano(e.serie())).collect(Collectors.toSet());

        // Lo que cada ref nombra, en toda la carga: un equipo nombra su modelo, el modelo su tipo y su marca.
        Map<String, String> nombrePorRef = new HashMap<>();
        Map<String, ExistingRecords.ModelName> modeloPorRef = new HashMap<>();
        Map<String, LoadDocument.Client> clientePorArea = new HashMap<>();
        for (ReadFile archivo : archivos) {
            LoadDocument.Catalog catalogo = archivo.documento().catalogo();
            catalogo.marcas().forEach(m -> nombrePorRef.put(m.ref(), m.nombre()));
            catalogo.tiposEquipo().forEach(t -> nombrePorRef.put(t.ref(), t.nombre()));
            for (LoadDocument.Client cliente : archivo.documento().clientes()) {
                cliente.sedes().forEach(s -> s.areas().forEach(a -> clientePorArea.put(a.ref(), cliente)));
            }
        }
        for (ReadFile archivo : archivos) {
            archivo.documento().catalogo().modelos().forEach(m -> modeloPorRef.put(m.ref(), new ExistingRecords.ModelName(
                    nombrePorRef.get(m.tipo()), nombrePorRef.get(m.marca()), m.nombre())));
        }

        int sinSerie = 0;
        for (ReadFile archivo : archivos) {
            LoadDocument documento = archivo.documento();
            LoadDocument.Catalog catalogo = documento.catalogo();
            contar("marcas", catalogo.marcas(), m -> marcas.contains(Names.plano(m.nombre())));
            contar("fabricantes", catalogo.fabricantes(), f -> fabricantes.contains(Names.plano(f.nombre())));
            contar("protocolos", catalogo.protocolos(), p -> protocolos.contains(Names.plano(p.nombre())));
            contar("tiposEquipo", catalogo.tiposEquipo(), t -> tipos.contains(Names.plano(t.nombre())));
            contar("modelos", catalogo.modelos(), m -> modelos.contains(clave(modeloPorRef.get(m.ref()))));
            contar("patrones", catalogo.patrones(), p -> series.contains(Names.plano(p.serie())));
            contar("personas", documento.personas(), p -> cedulas.contains(p.cedula().trim()));

            for (LoadDocument.Client cliente : documento.clientes()) {
                ExistingRecords.ExistingClient yaEsta = clientes.get(cliente.documento().trim());
                contar("clientes", List.of(cliente), c -> yaEsta != null);
                for (LoadDocument.Site sede : cliente.sedes()) {
                    ExistingRecords.ExistingSite sedeQueEsta = yaEsta == null ? null : yaEsta.sedes().stream()
                            .filter(s -> Names.plano(s.nombre()).equals(Names.plano(sede.nombre()))).findFirst().orElse(null);
                    contar("sedes", List.of(sede), s -> sedeQueEsta != null);
                    Set<String> areas = sedeQueEsta == null ? Set.of() : planos(sedeQueEsta.areas());
                    contar("areas", sede.areas(), a -> areas.contains(Names.plano(a.nombre())));
                }
            }

            for (int i = 0; i < documento.equipos().size(); i++) {
                LoadDocument.Equipment equipo = documento.equipos().get(i);
                if (equipo.serie() != null) {
                    contar("equipos", List.of(equipo),
                            e -> equipos.contains(clave(modeloPorRef.get(e.modelo())) + "|" + Names.plano(e.serie())));
                    continue;
                }
                LoadDocument.Client cliente = clientePorArea.get(equipo.area());
                if (cliente != null && clientes.containsKey(cliente.documento().trim())) {
                    errores.add(new DataLoadProblem(archivo.nombre(), "/equipos/" + i, "El equipo «" + equipo.ref()
                            + "» no tiene serie y su cliente ya está en MalphasOS: sin serie no hay con qué saber si ya está."
                            + " Se le pone la serie en el archivo, o se registra desde la pantalla"));
                } else {
                    sinSerie++;
                    contar("equipos", List.of(equipo), e -> false);
                }
            }

            contar("ordenes", documento.ordenes(), o -> false);
            documento.ordenes().forEach(o -> contar("reportes", o.reportes(), r -> false));
        }

        if (sinSerie > 0) {
            avisos.add(DataLoadProblem.of(null, sinSerie == 1
                    ? "Un equipo entra sin serie y recibirá una provisional, P: y el comienzo de su identificador"
                    : sinSerie + " equipos entran sin serie y recibirán una provisional, P: y el comienzo de su identificador"));
        }
    }

    private <T> void contar(String seccion, List<T> registros, Predicate<T> existe) {
        for (T registro : registros) {
            Integer[] cuenta = conteo.computeIfAbsent(seccion, s -> new Integer[] {0, 0});
            cuenta[existe.test(registro) ? 1 : 0]++;
        }
    }

    private static Set<String> planos(List<String> nombres) {
        return nombres.stream().map(Names::plano).collect(Collectors.toSet());
    }

    private static String clave(ExistingRecords.ModelName modelo) {
        return modelo == null ? "" : Names.plano(modelo.tipo()) + "|" + Names.plano(modelo.marca()) + "|" + Names.plano(modelo.nombre());
    }
}
