package com.malphasos.malphasos.dataload.application.services;

import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import com.malphasos.malphasos.dataload.application.model.ExistingRecords;
import com.malphasos.malphasos.dataload.application.model.LoadDocument;
import com.malphasos.malphasos.dataload.application.model.ReadFile;
import com.malphasos.malphasos.dataload.application.model.SectionCount;
import java.time.LocalDate;
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
 * porque no hay con qué saber si ya está. Por eso también un historial no entra en días en que su
 * cliente ya tiene órdenes: sería la misma carga dos veces.
 *
 * <p>Un modelo y una serie son de un solo equipo en toda la base, no por cliente: dos equipos de la carga
 * con los mismos se rechazan, y uno que ya es de otro cliente también, porque la carga lo tomaría por el
 * mismo equipo y el historial quedaría en el cliente equivocado.
 *
 * <p>Y el ingeniero de cada orden tiene firma, guardada en MalphasOS o en la carga: el reporte se congela
 * al cargar, y uno congelado sin firma no la recibe nunca.
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
        Map<String, ExistingRecords.EquipmentName> equipos = existentes.equipos().stream().filter(e -> e.serie() != null)
                .collect(Collectors.toMap(e -> clave(e.modelo()) + "|" + Names.plano(e.serie()), e -> e, (a, b) -> a));
        Map<String, String> equiposDeLaCarga = new HashMap<>();

        // Lo que cada ref nombra, en toda la carga: un equipo nombra su modelo, el modelo su tipo y su marca.
        Map<String, String> nombrePorRef = new HashMap<>();
        Map<String, ExistingRecords.ModelName> modeloPorRef = new HashMap<>();
        Map<String, LoadDocument.Client> clientePorArea = new HashMap<>();
        Map<String, LoadDocument.Client> clientePorRef = new HashMap<>();
        Map<String, LoadDocument.Person> personaPorRef = new HashMap<>();
        for (ReadFile archivo : archivos) {
            LoadDocument.Catalog catalogo = archivo.documento().catalogo();
            archivo.documento().personas().forEach(p -> personaPorRef.put(p.ref(), p));
            archivo.documento().clientes().forEach(c -> clientePorRef.put(c.ref(), c));
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
            contar("firmas", documento.personas().stream().filter(p -> p.firma() != null).toList(),
                    p -> conFirmaGuardada(p));

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
                LoadDocument.Client cliente = clientePorArea.get(equipo.area());
                if (equipo.serie() != null) {
                    String llave = clave(modeloPorRef.get(equipo.modelo())) + "|" + Names.plano(equipo.serie());
                    String ruta = "/equipos/" + i + "/serie";
                    String otro = equiposDeLaCarga.putIfAbsent(llave, archivo.nombre() + ", " + ruta);
                    ExistingRecords.EquipmentName queEsta = equipos.get(llave);
                    if (otro != null) {
                        errores.add(new DataLoadProblem(archivo.nombre(), ruta, "El equipo «" + equipo.ref()
                                + "» tiene el mismo modelo y la misma serie, «" + equipo.serie() + "», que el de " + otro
                                + ". En MalphasOS un modelo y una serie son de un solo equipo: si son dos, a uno se le"
                                + " quita la serie en el archivo y recibe la provisional"));
                    } else if (queEsta != null && queEsta.documentoCliente() != null && cliente != null
                            && !queEsta.documentoCliente().trim().equals(cliente.documento().trim())) {
                        errores.add(new DataLoadProblem(archivo.nombre(), ruta, "El equipo «" + equipo.ref()
                                + "» tiene el modelo y la serie, «" + equipo.serie() + "», de un equipo que ya es del"
                                + " cliente " + queEsta.documentoCliente() + ": la carga lo tomaría por ese, y su historial"
                                + " quedaría en otro cliente. Se le quita la serie en el archivo, o se corrige"));
                    }
                    contar("equipos", List.of(equipo), e -> queEsta != null);
                    continue;
                }
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

        for (ReadFile archivo : archivos) {
            fechasOcupadas(archivo, clientePorRef, clientes);
            ingenieros(archivo, personaPorRef);
        }

        if (sinSerie > 0) {
            avisos.add(DataLoadProblem.of(null, sinSerie == 1
                    ? "Un equipo entra sin serie y recibirá una provisional, P: y el comienzo de su identificador"
                    : sinSerie + " equipos entran sin serie y recibirán una provisional, P: y el comienzo de su identificador"));
        }
    }

    /** Una orden del historial en un día en que su cliente ya tiene otra: se dice la primera y cuántas son. */
    private void fechasOcupadas(ReadFile archivo, Map<String, LoadDocument.Client> clientePorRef,
            Map<String, ExistingRecords.ExistingClient> clientes) {
        LoadDocument documento = archivo.documento();
        LoadDocument.Client cliente = documento.cliente() == null ? null : clientePorRef.get(documento.cliente());
        ExistingRecords.ExistingClient yaEsta = cliente == null ? null : clientes.get(cliente.documento().trim());
        if (yaEsta == null) {
            return;
        }
        Set<LocalDate> ocupadas = Set.copyOf(yaEsta.fechasDeOrdenes());
        List<Integer> chocan = new ArrayList<>();
        for (int i = 0; i < documento.ordenes().size(); i++) {
            if (ocupadas.contains(documento.ordenes().get(i).fecha())) {
                chocan.add(i);
            }
        }
        if (chocan.isEmpty()) {
            return;
        }
        LoadDocument.Order primera = documento.ordenes().get(chocan.getFirst());
        errores.add(new DataLoadProblem(archivo.nombre(), "/ordenes/" + chocan.getFirst(), "El cliente «"
                + cliente.razonSocial() + "» ya tiene una orden el " + primera.fecha()
                + (chocan.size() > 1 ? ", y otras " + (chocan.size() - 1) + " órdenes de este historial caen en días con órdenes suyas" : "")
                + ": parece que este historial ya se cargó. Un historial no se carga dos veces"));
    }

    /** Ya tiene firma en MalphasOS: la de la carga no la sustituye. */
    private boolean conFirmaGuardada(LoadDocument.Person persona) {
        return existentes.cedulasConFirma().stream().anyMatch(c -> c.trim().equals(persona.cedula().trim()));
    }

    /** Cada ingeniero se dice una vez, en la primera orden que lo nombra, con cuántas lo nombran. */
    private void ingenieros(ReadFile archivo, Map<String, LoadDocument.Person> personaPorRef) {
        Map<String, List<Integer>> ordenesPorIngeniero = new LinkedHashMap<>();
        List<LoadDocument.Order> ordenes = archivo.documento().ordenes();
        for (int i = 0; i < ordenes.size(); i++) {
            ordenesPorIngeniero.computeIfAbsent(ordenes.get(i).ingeniero(), r -> new ArrayList<>()).add(i);
        }
        ordenesPorIngeniero.forEach((ref, indices) -> {
            LoadDocument.Person persona = personaPorRef.get(ref);
            if (persona == null) {
                return; // la ref sin destino ya es un error de las refs
            }
            String problema;
            if (!"ENGINEER".equals(persona.tipo())) {
                problema = "no es ingeniero: el tipo de la persona es " + persona.tipo();
            } else if (persona.firma() == null && !conFirmaGuardada(persona)) {
                problema = "no tiene firma, ni guardada en MalphasOS ni en la carga. Se pone en su campo firma del"
                        + " catálogo, o la guarda en su cuenta antes de cargar: el reporte se congela al cargar y no la"
                        + " recibiría después";
            } else {
                return;
            }
            errores.add(new DataLoadProblem(archivo.nombre(), "/ordenes/" + indices.getFirst() + "/ingeniero",
                    "El ingeniero «" + ref + "» " + problema
                    + (indices.size() > 1 ? ". Lo nombran " + indices.size() + " órdenes de este historial" : "")));
        });
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
