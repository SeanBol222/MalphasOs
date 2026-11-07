package com.malphasos.malphasos.dataload.application.services;

import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import com.malphasos.malphasos.dataload.application.model.LoadDocument;
import com.malphasos.malphasos.dataload.application.model.ReadFile;
import com.malphasos.malphasos.dataload.application.ports.output.LocationCatalogPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Las reglas que el esquema no puede ver porque miran valores, y no la forma: que un país y una ciudad
 * estén en el catálogo de MalphasOS, y que dos patrones de la carga no compartan serie.
 *
 * <p>Solo mira los archivos que cumplieron el esquema: en uno que no lo cumple no hay valores en los
 * que confiar, y el esquema ya dijo por qué.
 */
final class ContentRules {

    private final LocationCatalogPort ubicacion;
    private final Map<String, Boolean> paises = new HashMap<>();
    private final Map<String, List<String>> ciudades = new HashMap<>();

    ContentRules(LocationCatalogPort ubicacion) {
        this.ubicacion = ubicacion;
    }

    List<DataLoadProblem> check(List<ReadFile> archivos) {
        List<DataLoadProblem> errores = new ArrayList<>();
        Map<String, String> series = new LinkedHashMap<>();

        for (ReadFile archivo : archivos) {
            LoadDocument documento = archivo.documento();
            String nombre = archivo.nombre();

            for (int i = 0; i < documento.catalogo().fabricantes().size(); i++) {
                pais(documento.catalogo().fabricantes().get(i).pais(), nombre, "/catalogo/fabricantes/" + i + "/pais", errores);
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
            for (int c = 0; c < documento.clientes().size(); c++) {
                LoadDocument.Client cliente = documento.clientes().get(c);
                pais(cliente.pais(), nombre, "/clientes/" + c + "/pais", errores);
                for (int s = 0; s < cliente.sedes().size(); s++) {
                    ciudad(cliente.sedes().get(s).ciudad(), nombre, "/clientes/" + c + "/sedes/" + s + "/ciudad", errores);
                }
            }
        }
        return errores;
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
