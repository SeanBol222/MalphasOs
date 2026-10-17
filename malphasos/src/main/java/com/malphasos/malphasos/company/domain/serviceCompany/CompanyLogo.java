package com.malphasos.malphasos.company.domain.serviceCompany;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

/**
 * El logo de la empresa: los bytes de la imagen y su tipo (2026-10-17).
 *
 * <p>El tipo no se cree, se mira: se deduce de los primeros bytes y no de lo que diga quien lo sube,
 * porque un documento que se entrega al cliente no puede pintar algo que dice ser una imagen y no lo es.
 * Un SVG se acepta porque se pinta dentro de {@code <img>}, donde el navegador no ejecuta sus scripts.
 */
public record CompanyLogo(byte[] contenido, String tipo) {

    public static final int TAMANO_MAXIMO = 512 * 1024;

    public static final String PNG = "image/png";
    public static final String JPEG = "image/jpeg";
    public static final String SVG = "image/svg+xml";

    private static final byte[] FIRMA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] FIRMA_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    public CompanyLogo {
        if (contenido == null || contenido.length == 0) {
            throw new IllegalArgumentException("El logo de la empresa no puede estar vacío");
        }
        if (contenido.length > TAMANO_MAXIMO) {
            throw new IllegalArgumentException("El logo de la empresa pesa " + contenido.length
                    + " bytes y el máximo es " + TAMANO_MAXIMO);
        }
        contenido = contenido.clone();
        String deducido = tipoDe(contenido);
        // Al rehidratar llega el tipo guardado: si no coincide con los bytes, algo los alteró por fuera.
        if (tipo != null && !tipo.equals(deducido)) {
            throw new IllegalArgumentException(
                    "El logo de la empresa dice ser " + tipo + " y sus bytes son de " + deducido);
        }
        tipo = deducido;
    }

    /** Un logo a partir de sus bytes: el tipo sale de ellos. */
    public static CompanyLogo of(byte[] contenido) {
        return new CompanyLogo(contenido, null);
    }

    @Override
    public byte[] contenido() {
        return contenido.clone();
    }

    /** El logo como {@code data:} URI, que es como lo pintan los documentos. */
    public String dataUri() {
        return "data:" + tipo + ";base64," + Base64.getEncoder().encodeToString(contenido);
    }

    private static String tipoDe(byte[] contenido) {
        if (empiezaPor(contenido, FIRMA_PNG)) {
            return PNG;
        }
        if (empiezaPor(contenido, FIRMA_JPEG)) {
            return JPEG;
        }
        if (esSvg(contenido)) {
            return SVG;
        }

        throw new IllegalArgumentException("El logo de la empresa tiene que ser PNG, JPG o SVG");
    }

    private static boolean empiezaPor(byte[] contenido, byte[] firma) {
        return contenido.length >= firma.length
                && Arrays.equals(Arrays.copyOf(contenido, firma.length), firma);
    }

    /** Texto que, quitando la declaración XML y los comentarios del principio, abre un elemento svg. */
    private static boolean esSvg(byte[] contenido) {
        String inicio = new String(contenido, 0, Math.min(contenido.length, 1024), StandardCharsets.UTF_8)
                .stripLeading()
                .toLowerCase(Locale.ROOT);

        return inicio.startsWith("<") && inicio.contains("<svg");
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof CompanyLogo logo && tipo.equals(logo.tipo) && Arrays.equals(contenido, logo.contenido);
    }

    @Override
    public int hashCode() {
        return 31 * tipo.hashCode() + Arrays.hashCode(contenido);
    }

    @Override
    public String toString() {
        return "CompanyLogo[" + tipo + ", " + contenido.length + " bytes]";
    }
}
