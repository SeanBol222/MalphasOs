package com.malphasos.malphasos.dataload.application.services;

import com.malphasos.malphasos.dataload.application.model.DataLoadFile;
import com.malphasos.malphasos.dataload.application.model.DataLoadProblem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Abre los ZIP de una carga (2026-11-16): el conversor entrega un solo archivo con el catálogo y el par de
 * cada cliente, y la carga lo valida como si se hubieran subido sueltos. Cada archivo del ZIP se nombra
 * por su ruta dentro de él —{@code clientes/fana/historial.json}—, que es como el informe dice dónde está
 * cada problema.
 *
 * <p>Lo que no es un JSON —una carpeta, los metadatos que añade un Mac, un léeme— se ignora con un aviso.
 * Y se lee con tope: un ZIP de pocos kilobytes puede descomprimirse en gigas.
 */
final class LoadPackage {

    static final int MAXIMO_DE_ARCHIVOS = 500;
    static final long MAXIMO_DESCOMPRIMIDO = 200L * 1024 * 1024;
    private static final byte[] FIRMA_ZIP = {'P', 'K', 3, 4};

    final List<DataLoadFile> archivos = new ArrayList<>();
    final List<DataLoadProblem> errores = new ArrayList<>();
    final List<DataLoadProblem> avisos = new ArrayList<>();
    private long descomprimido;

    LoadPackage(List<DataLoadFile> subidos) {
        for (DataLoadFile subido : subidos) {
            if (esZip(subido)) {
                abrir(subido);
            } else {
                archivos.add(subido);
            }
        }
    }

    static boolean esZip(DataLoadFile archivo) {
        byte[] bytes = archivo.contenido();
        return bytes != null && bytes.length >= FIRMA_ZIP.length
                && Arrays.equals(Arrays.copyOf(bytes, FIRMA_ZIP.length), FIRMA_ZIP);
    }

    private void abrir(DataLoadFile zip) {
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip.contenido()))) {
            ZipEntry elemento;
            while ((elemento = entrada.getNextEntry()) != null) {
                String ruta = elemento.getName();
                if (elemento.isDirectory()) {
                    continue;
                }
                String nombre = ruta.substring(ruta.lastIndexOf('/') + 1);
                if (ruta.startsWith("__MACOSX/") || nombre.startsWith(".")) {
                    continue;
                }
                if (!nombre.toLowerCase().endsWith(".json")) {
                    avisos.add(DataLoadProblem.of(zip.nombre(), "Se ignora " + ruta + ": la carga solo lee los .json del ZIP"));
                    continue;
                }
                if (archivos.size() >= MAXIMO_DE_ARCHIVOS) {
                    errores.add(DataLoadProblem.of(zip.nombre(), "El ZIP trae más de " + MAXIMO_DE_ARCHIVOS + " archivos"));
                    return;
                }
                byte[] contenido = leer(entrada, zip.nombre());
                if (contenido == null) {
                    return;
                }
                archivos.add(new DataLoadFile(ruta, contenido));
            }
        } catch (ZipException e) {
            errores.add(DataLoadProblem.of(zip.nombre(), "No es un ZIP que se pueda abrir: " + e.getMessage()));
        } catch (IOException e) {
            errores.add(DataLoadProblem.of(zip.nombre(), "No se pudo leer el ZIP: " + e.getMessage()));
        }
    }

    /** Lee una entrada sin pasar del tope de toda la carga; nulo, con su error, si lo pasa. */
    private byte[] leer(InputStream entrada, String zip) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        byte[] trozo = new byte[64 * 1024];
        int leidos;
        while ((leidos = entrada.read(trozo)) != -1) {
            descomprimido += leidos;
            if (descomprimido > MAXIMO_DESCOMPRIMIDO) {
                errores.add(DataLoadProblem.of(zip, "El ZIP descomprimido pasa de " + MAXIMO_DESCOMPRIMIDO / 1024 / 1024
                        + " MB, que es el máximo de una carga"));
                return null;
            }
            salida.write(trozo, 0, leidos);
        }
        return salida.toByteArray();
    }
}
