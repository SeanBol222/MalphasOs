package com.malphasos.malphasos.dataload;

import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Los archivos de {@code contracts/carga}: el esquema publicado y los tres ejemplos inventados.
 *
 * <p>Se buscan subiendo desde el directorio de trabajo, como el realm en {@code RealmFixture}: no es
 * el mismo al lanzar la batería desde la raíz que desde el módulo. Ninguno lleva datos reales.
 */
public final class EjemplosDeCarga {

    public static final String CATALOGO = "ejemplo-1-catalogo.json";
    public static final String CLIENTE = "ejemplo-2-cliente.json";
    public static final String HISTORIAL = "ejemplo-3-historial.json";
    public static final String ESQUEMA = "malphasos-carga-1.schema.json";

    private EjemplosDeCarga() {}

    public static Path ruta(String archivo) {
        Path directorio = Path.of("").toAbsolutePath();
        while (directorio != null) {
            Path candidato = directorio.resolve("contracts/carga").resolve(archivo);
            if (Files.isRegularFile(candidato)) {
                return candidato;
            }
            directorio = directorio.getParent();
        }
        throw new IllegalStateException("No se encontro contracts/carga/" + archivo + " subiendo desde " + Path.of("").toAbsolutePath());
    }

    public static String texto(String archivo) {
        try {
            return Files.readString(ruta(archivo), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static DataLoadFile archivo(String archivo) {
        return new DataLoadFile(archivo, texto(archivo).getBytes(StandardCharsets.UTF_8));
    }

    /** Un archivo con el texto dado, para probar lo que el ejemplo no trae. */
    public static DataLoadFile archivo(String nombre, String texto) {
        return new DataLoadFile(nombre, texto.getBytes(StandardCharsets.UTF_8));
    }
}
