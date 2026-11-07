package com.malphasos.malphasos.dataload.infrastructure.output.format;

import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import com.malphasos.malphasos.dataload.application.model.FormatCheck;
import com.malphasos.malphasos.dataload.application.model.RefKind;
import com.malphasos.malphasos.dataload.application.model.RefMention;
import com.malphasos.malphasos.dataload.application.ports.output.LoadFormatPort;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Comprueba cada archivo contra el esquema publicado, con la misma definición que usa el conversor.
 *
 * <p>El esquema vive en {@code contracts/carga} y aquí hay una copia en el classpath, porque la imagen
 * del backend se construye sin ver el resto del repositorio. {@code CargaSchemaCopyTest} falla si las
 * dos difieren: una sola fuente de verdad, vigilada.
 *
 * <p>Los formatos —fecha, correo— se comprueban como reglas y no como anotaciones, que es lo que
 * JSON Schema 2020-12 haría por defecto: una fecha imposible no debe pasar la validación.
 */
@Component
public class JsonSchemaLoadFormatAdapter implements LoadFormatPort {

    static final String ESQUEMA = "/carga/malphasos-carga-1.schema.json";
    private static final Set<String> CONTENIDOS = Set.of("catalogo", "cliente", "historial");
    private static final Locale ESPANOL = Locale.forLanguageTag("es");

    private final JsonMapper json = JsonMapper.builder().build();
    private final Schema esquema = cargarEsquema();

    private static Schema cargarEsquema() {
        try (InputStream entrada = JsonSchemaLoadFormatAdapter.class.getResourceAsStream(ESQUEMA)) {
            if (entrada == null) {
                throw new IllegalStateException("No esta en el classpath el esquema de la carga: " + ESQUEMA);
            }
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(entrada);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el esquema de la carga", e);
        }
    }

    @Override
    public FormatCheck check(DataLoadFile archivo) {
        JsonNode documento;
        try {
            documento = json.readTree(archivo.contenido());
        } catch (JacksonException e) {
            return FormatCheck.unreadable(List.of(DataLoadProblem.of(archivo.nombre(),
                    "No es un JSON válido: " + e.getOriginalMessage())));
        }

        List<Error> incumplidos = esquema.validate(documento, contexto -> contexto.executionConfig(config -> config
                .formatAssertionsEnabled(true)
                .locale(ESPANOL)));

        String contenido = documento.path("contenido").asString(null);
        Refs refs = new Refs(archivo.nombre());
        refs.recorrer(documento);
        return new FormatCheck(CONTENIDOS.contains(contenido) ? contenido : null, incumplidos.stream()
                .map(e -> new DataLoadProblem(archivo.nombre(), e.getInstanceLocation().toString(), e.getMessage()))
                .toList(), refs.declaradas, refs.usadas);
    }

    /**
     * Las {@code ref} de un archivo: dónde se declara cada cosa y dónde se nombra otra. Sigue la forma
     * del esquema, y no adivina: lo que no está donde el esquema lo pone no se recoge, y el esquema ya
     * habrá dicho por qué.
     */
    private static final class Refs {

        private final String archivo;
        private final List<RefMention> declaradas = new ArrayList<>();
        private final List<RefMention> usadas = new ArrayList<>();

        Refs(String archivo) {
            this.archivo = archivo;
        }

        void recorrer(JsonNode raiz) {
            JsonNode catalogo = raiz.path("catalogo");
            declarar(catalogo.path("marcas"), "/catalogo/marcas", RefKind.MARCA);
            declarar(catalogo.path("fabricantes"), "/catalogo/fabricantes", RefKind.FABRICANTE);
            declarar(catalogo.path("protocolos"), "/catalogo/protocolos", RefKind.PROTOCOLO);
            cada(catalogo.path("tiposEquipo"), "/catalogo/tiposEquipo", (tipo, ruta) -> {
                declarar(tipo, ruta, RefKind.TIPO_EQUIPO);
                cada(tipo.path("protocolos"), ruta + "/protocolos",
                        (p, r) -> usar(p.path("protocolo"), r + "/protocolo", RefKind.PROTOCOLO));
                declarar(tipo.path("verificaciones"), ruta + "/verificaciones", RefKind.VERIFICACION);
            });
            cada(catalogo.path("modelos"), "/catalogo/modelos", (modelo, ruta) -> {
                declarar(modelo, ruta, RefKind.MODELO);
                usar(modelo.path("tipo"), ruta + "/tipo", RefKind.TIPO_EQUIPO);
                usar(modelo.path("marca"), ruta + "/marca", RefKind.MARCA);
                usar(modelo.path("fabricante"), ruta + "/fabricante", RefKind.FABRICANTE);
            });
            declarar(catalogo.path("patrones"), "/catalogo/patrones", RefKind.PATRON);
            declarar(raiz.path("personas"), "/personas", RefKind.PERSONA);
            cada(raiz.path("clientes"), "/clientes", (cliente, ruta) -> {
                declarar(cliente, ruta, RefKind.CLIENTE);
                cada(cliente.path("supervisores"), ruta + "/supervisores",
                        (s, r) -> usar(s.path("persona"), r + "/persona", RefKind.PERSONA));
                cada(cliente.path("sedes"), ruta + "/sedes", (sede, rs) -> {
                    declarar(sede, rs, RefKind.SEDE);
                    declarar(sede.path("areas"), rs + "/areas", RefKind.AREA);
                });
            });
            cada(raiz.path("equipos"), "/equipos", (equipo, ruta) -> {
                declarar(equipo, ruta, RefKind.EQUIPO);
                usar(equipo.path("area"), ruta + "/area", RefKind.AREA);
                usar(equipo.path("modelo"), ruta + "/modelo", RefKind.MODELO);
            });
            usar(raiz.path("cliente"), "/cliente", RefKind.CLIENTE);
            cada(raiz.path("ordenes"), "/ordenes", (orden, ruta) -> {
                declarar(orden, ruta, RefKind.ORDEN);
                usar(orden.path("sede"), ruta + "/sede", RefKind.SEDE);
                usar(orden.path("ingeniero"), ruta + "/ingeniero", RefKind.PERSONA);
                cada(orden.path("reportes"), ruta + "/reportes", (reporte, rr) -> {
                    declarar(reporte, rr, RefKind.REPORTE);
                    usar(reporte.path("equipo"), rr + "/equipo", RefKind.EQUIPO);
                    cada(reporte.path("pasos"), rr + "/pasos",
                            (p, r) -> usar(p.path("protocolo"), r + "/protocolo", RefKind.PROTOCOLO));
                    cada(reporte.path("verificaciones"), rr + "/verificaciones", (v, rv) -> {
                        usar(v.path("verificacion"), rv + "/verificacion", RefKind.VERIFICACION);
                        cada(v.path("patrones"), rv + "/patrones", (p, r) -> usar(p, r, RefKind.PATRON));
                    });
                    usar(reporte.path("recibe").path("persona"), rr + "/recibe/persona", RefKind.PERSONA);
                });
            });
        }

        /** La ref de un objeto, o la de cada objeto si es una lista. */
        private void declarar(JsonNode nodo, String ruta, RefKind clase) {
            if (nodo.isArray()) {
                cada(nodo, ruta, (n, r) -> declarar(n, r, clase));
            } else if (nodo.path("ref").isString()) {
                declaradas.add(new RefMention(nodo.path("ref").asString(), clase, archivo, ruta + "/ref"));
            }
        }

        private void usar(JsonNode nodo, String ruta, RefKind clase) {
            if (nodo.isString()) {
                usadas.add(new RefMention(nodo.asString(), clase, archivo, ruta));
            }
        }

        private static void cada(JsonNode lista, String ruta, java.util.function.BiConsumer<JsonNode, String> accion) {
            for (int i = 0; i < lista.size() && lista.isArray(); i++) {
                accion.accept(lista.get(i), ruta + "/" + i);
            }
        }
    }
}
