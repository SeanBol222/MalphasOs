package com.malphasos.malphasos.dataload.application.services;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Cómo se comparan los nombres de una carga con los de MalphasOS: sin tildes, sin mayúsculas y sin
 * espacios de sobra, como dice la guía del formato. «Bogota D.C» y «Bogotá d.c» son el mismo nombre.
 *
 * <p>Pública porque el escritor de la carga, al aplicarla, reconoce lo que ya existe igual que la validación.
 */
public final class Names {

    private Names() {}

    public static String plano(String nombre) {
        return Normalizer.normalize(nombre == null ? "" : nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }

    /**
     * Los nombres más parecidos a uno que no está, para sugerirlos: los que quedan a pocas letras, o
     * que uno contiene al otro —«Bogota D.C.» y «Bogotá»—, del más cercano al más lejano. Ninguno si
     * nada se le parece.
     */
    static List<String> parecidos(String buscado, List<String> nombres, int cuantos) {
        String b = plano(buscado);
        int tope = Math.max(2, b.length() / 3);
        return nombres.stream()
                .filter(n -> distancia(b, plano(n)) <= tope || contiene(b, plano(n)))
                .sorted(Comparator.comparingInt(n -> distancia(b, plano(n))))
                .limit(cuantos)
                .toList();
    }

    /** Uno empieza por el otro como palabra entera: «bogota d.c.» y «bogota», no «cali» y «california». */
    private static boolean contiene(String a, String b) {
        String corto = a.length() <= b.length() ? a : b;
        String largo = a.length() <= b.length() ? b : a;
        return !corto.isEmpty() && (largo.equals(corto) || largo.startsWith(corto + " ") || largo.startsWith(corto + "."));
    }

    /** Cuántas letras hay que cambiar, poner o quitar para pasar de uno a otro (Levenshtein). */
    static int distancia(String a, String b) {
        int[] anterior = new int[b.length() + 1];
        int[] actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            anterior[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cambio = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                actual[j] = Math.min(Math.min(actual[j - 1] + 1, anterior[j] + 1), anterior[j - 1] + cambio);
            }
            int[] t = anterior;
            anterior = actual;
            actual = t;
        }
        return anterior[b.length()];
    }
}
