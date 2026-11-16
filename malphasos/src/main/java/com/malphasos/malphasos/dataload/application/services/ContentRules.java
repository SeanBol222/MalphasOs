package com.malphasos.malphasos.dataload.application.services;

import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import com.malphasos.malphasos.dataload.application.model.LoadDocument;
import com.malphasos.malphasos.dataload.application.model.ReadFile;
import com.malphasos.malphasos.dataload.application.ports.output.LocationCatalogPort;
import com.malphasos.malphasos.dataload.application.ports.output.MetrologyCatalogPort;
import com.malphasos.malphasos.person.domain.person.PersonSignature;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Las reglas que el esquema no puede ver porque miran valores, y no la forma: que un país y una ciudad
 * estén en el catálogo de MalphasOS, que la magnitud y la unidad de cada verificación estén en el
 * catálogo metrológico, que el supervisor de un cliente sea una persona de tipo supervisor, que dos
 * patrones de la carga no compartan serie, que la firma de una persona sea un PNG que MalphasOS admita,
 * de alguien de la casa, y que una orden no traiga dos reportes del mismo equipo.
 *
 * <p>Solo mira los archivos que cumplieron el esquema: en uno que no lo cumple no hay valores en los
 * que confiar, y el esquema ya dijo por qué.
 */
final class ContentRules {

    private final LocationCatalogPort ubicacion;
    private final MetrologyCatalogPort metrologia;
    private final Map<String, Optional<List<String>>> unidades = new HashMap<>();
    private final Map<String, Boolean> paises = new HashMap<>();
    private final Map<String, List<String>> ciudades = new HashMap<>();

    ContentRules(LocationCatalogPort ubicacion, MetrologyCatalogPort metrologia) {
        this.ubicacion = ubicacion;
        this.metrologia = metrologia;
    }

    List<DataLoadProblem> check(List<ReadFile> archivos) {
        List<DataLoadProblem> errores = new ArrayList<>();
        Map<String, String> series = new LinkedHashMap<>();
        // El esquema solo deja supervisores en el archivo del cliente, pero un cliente puede nombrar a una
        // persona del catálogo: un ingeniero no supervisa a un cliente, como en la pantalla.
        Map<String, LoadDocument.Person> personaPorRef = new HashMap<>();
        archivos.forEach(a -> a.documento().personas().forEach(p -> personaPorRef.put(p.ref(), p)));

        for (ReadFile archivo : archivos) {
            LoadDocument documento = archivo.documento();
            String nombre = archivo.nombre();

            for (int i = 0; i < documento.catalogo().fabricantes().size(); i++) {
                pais(documento.catalogo().fabricantes().get(i).pais(), nombre, "/catalogo/fabricantes/" + i + "/pais", errores);
            }
            for (int t = 0; t < documento.catalogo().tiposEquipo().size(); t++) {
                List<LoadDocument.Verification> verificaciones = documento.catalogo().tiposEquipo().get(t).verificaciones();
                for (int v = 0; v < verificaciones.size(); v++) {
                    magnitud(verificaciones.get(v), nombre, "/catalogo/tiposEquipo/" + t + "/verificaciones/" + v, errores);
                }
            }
            for (int i = 0; i < documento.catalogo().patrones().size(); i++) {
                LoadDocument.ReferenceStandard patron = documento.catalogo().patrones().get(i);
                String ruta = "/catalogo/patrones/" + i + "/serie";
                String anterior = series.putIfAbsent(Names.plano(patron.serie()), nombre + ", " + ruta);
                if (anterior != null) {
                    errores.add(new DataLoadProblem(nombre, ruta, "Dos patrones de la carga tienen la serie «"
                            + patron.serie() + "»: el otro está en " + anterior + ". MalphasOS no admite una serie repetida"
                            + " entre los patrones, y se corrige en el archivo"));
                }
            }
            for (int p = 0; p < documento.personas().size(); p++) {
                firma(documento.personas().get(p), nombre, "/personas/" + p + "/firma", errores);
            }
            for (int o = 0; o < documento.ordenes().size(); o++) {
                unReportePorEquipo(documento.ordenes().get(o), nombre, "/ordenes/" + o, errores);
            }
            for (int c = 0; c < documento.clientes().size(); c++) {
                LoadDocument.Client cliente = documento.clientes().get(c);
                pais(cliente.pais(), nombre, "/clientes/" + c + "/pais", errores);
                for (int s = 0; s < cliente.supervisores().size(); s++) {
                    LoadDocument.Person persona = personaPorRef.get(cliente.supervisores().get(s).persona());
                    if (persona != null && !"SUPERVISOR".equals(persona.tipo())) {
                        errores.add(new DataLoadProblem(nombre, "/clientes/" + c + "/supervisores/" + s + "/persona",
                                "«" + persona.ref() + "» no es supervisor sino " + persona.tipo()
                                        + ": el supervisor de un cliente es una persona del cliente, y va en su archivo"));
                    }
                }
                for (int s = 0; s < cliente.sedes().size(); s++) {
                    ciudad(cliente.sedes().get(s).ciudad(), nombre, "/clientes/" + c + "/sedes/" + s + "/ciudad", errores);
                }
            }
        }
        return errores;
    }

    /**
     * Una orden tiene un reporte activo por equipo (UQ_reporte_servicio_activo, V9). Lo encontró la carga real
     * el 2026-11-16 al aplicar, con la validación limpia: dos visitas al mismo equipo con el mismo número de
     * orden. Son dos órdenes, y se parten en el archivo.
     */
    private static void unReportePorEquipo(LoadDocument.Order orden, String archivo, String ruta, List<DataLoadProblem> errores) {
        Map<String, Integer> primero = new HashMap<>();
        for (int r = 0; r < orden.reportes().size(); r++) {
            String equipo = orden.reportes().get(r).equipo();
            Integer anterior = primero.putIfAbsent(equipo, r);
            if (anterior != null) {
                errores.add(new DataLoadProblem(archivo, ruta + "/reportes/" + r + "/equipo", "La orden «" + orden.ref()
                        + "» trae otro reporte del equipo «" + equipo + "», el " + ruta + "/reportes/" + anterior
                        + ". Una orden tiene un reporte por equipo: si son dos visitas, son dos órdenes"));
            }
        }
    }

    /**
     * La firma la lee el mismo registro que guarda la de una cuenta, con sus reglas: PNG y hasta 256 KB. Un
     * supervisor no firma reportes, y su firma no tendría dónde usarse.
     */
    private static void firma(LoadDocument.Person persona, String archivo, String ruta, List<DataLoadProblem> errores) {
        if (persona.firma() == null) {
            return;
        }
        if (!"ENGINEER".equals(persona.tipo()) && !"ADMIN".equals(persona.tipo())) {
            errores.add(new DataLoadProblem(archivo, ruta, "«" + persona.ref() + "» es " + persona.tipo()
                    + " y solo la gente de la casa, ingenieros y administradores, lleva firma"));
            return;
        }
        try {
            new PersonSignature(Base64.getDecoder().decode(persona.firma()), LocalDateTime.now());
        } catch (IllegalArgumentException e) {
            errores.add(new DataLoadProblem(archivo, ruta, "La firma de «" + persona.ref() + "» no se puede guardar: "
                    + e.getMessage()));
        }
    }

    /** La unidad se compara tal cual: «%» y «%HR» son dos símbolos, y el catálogo tiene los dos. */
    private void magnitud(LoadDocument.Verification verificacion, String archivo, String ruta, List<DataLoadProblem> errores) {
        Optional<List<String>> deLaMagnitud = unidades.computeIfAbsent(verificacion.magnitud(), metrologia::unitsOf);
        if (deLaMagnitud.isEmpty()) {
            errores.add(new DataLoadProblem(archivo, ruta + "/magnitud", "La magnitud «" + verificacion.magnitud()
                    + "» no está en el catálogo metrológico de MalphasOS. Se nombra por su código: la lista está en GUIA.md"));
        } else if (!deLaMagnitud.get().contains(verificacion.unidad())) {
            errores.add(new DataLoadProblem(archivo, ruta + "/unidad", "La unidad «" + verificacion.unidad()
                    + "» no es de " + verificacion.magnitud() + " en el catálogo de MalphasOS. Las que tiene: "
                    + String.join(", ", deLaMagnitud.get())));
        }
    }

    private boolean pais(String codigoIso, String archivo, String ruta, List<DataLoadProblem> errores) {
        if (codigoIso == null) {
            return false;
        }
        boolean existe = paises.computeIfAbsent(codigoIso, ubicacion::countryExists);
        if (!existe) {
            errores.add(new DataLoadProblem(archivo, ruta, "El país «" + codigoIso + "» no está en el catálogo de MalphasOS"));
        }
        return existe;
    }

    /** La ciudad se busca sin tildes ni mayúsculas, y si no está se sugiere la que más se le parece. */
    private void ciudad(LoadDocument.City ciudad, String archivo, String ruta, List<DataLoadProblem> errores) {
        if (!pais(ciudad.pais(), archivo, ruta + "/pais", errores)) {
            return;
        }
        List<String> delPais = ciudades.computeIfAbsent(ciudad.pais(), ubicacion::citiesOf);
        List<String> iguales = delPais.stream().filter(n -> Names.plano(n).equals(Names.plano(ciudad.nombre()))).toList();

        if (iguales.size() > 1) {
            errores.add(new DataLoadProblem(archivo, ruta + "/nombre", "La ciudad «" + ciudad.nombre() + "» es ambigua en "
                    + ciudad.pais() + ": el catálogo tiene " + String.join(", ", iguales)));
        } else if (iguales.isEmpty()) {
            List<String> parecidas = Names.parecidos(ciudad.nombre(), delPais, 3);
            errores.add(new DataLoadProblem(archivo, ruta + "/nombre", "La ciudad «" + ciudad.nombre()
                    + "» no está en el catálogo de " + ciudad.pais()
                    + (parecidas.isEmpty() ? "" : ". ¿Es " + String.join(" o ", parecidas) + "?")));
        }
    }
}
