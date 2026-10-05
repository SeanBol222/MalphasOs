package com.malphasos.malphasos.client.domain.client;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * La sigla de un cliente: tres a seis caracteres que encabezan el número de sus hojas de vida
 * —«HV-CDN-0001»—.
 *
 * <p><b>Se genera sola a partir de la razón social</b> y se puede corregir después; no se regenera si
 * la razón social cambia, porque corregir una tilde no debe cambiar cómo se numeran los equipos de un
 * cliente. Las tres cosas, decididas por el usuario el 2026-10-05.
 *
 * <p><b>La regla tiene una gemela en SQL</b>, la función {@code sigla_base} de {@code V19}, con la que la
 * migración dio sigla a los clientes que ya existían. Las dos tienen que generar lo mismo, y una prueba
 * lo exige con la misma lista de razones sociales: si una cambia sin la otra, un cliente creado hoy y
 * uno migrado ayer se numerarían con reglas distintas.
 */
public final class ClientAcronym {

    /** Mayúsculas y dígitos, empezando por letra, de tres a seis. */
    private static final Pattern FORMATO = Pattern.compile("^[A-Z][A-Z0-9]{2,5}$");

    /** Las formas jurídicas, que no distinguen a nadie: casi todos los clientes son S.A.S. */
    private static final Set<String> FORMAS_JURIDICAS = Set.of("SAS", "SA", "LTDA", "EU", "SCA", "SENC", "S", "C");

    /** Las palabras vacías: «Clínica del Norte» es CN, no CDN. */
    private static final Set<String> PALABRAS_VACIAS = Set.of("DE", "DEL", "LA", "LAS", "LOS", "EL", "Y", "E", "EN");

    private ClientAcronym() {
    }

    /**
     * La sigla que le corresponde a una razón social, sin mirar si ya la tiene otro cliente: el
     * desempate con un número es de quien sabe qué siglas existen, que es el servicio.
     *
     * <ol>
     *   <li>mayúsculas, sin tildes ni puntuación —«S.A.S.» es «SAS»—;</li>
     *   <li>fuera las formas jurídicas y las palabras vacías;</li>
     *   <li>la inicial de cada palabra que queda, hasta seis;</li>
     *   <li>si salen menos de tres, se completa con las letras siguientes de la última palabra;</li>
     *   <li>si aun así no llega, o no empieza por letra, se completa o se antepone una «C».</li>
     * </ol>
     */
    public static String base(String razonSocial) {
        List<String> palabras = palabrasSignificativas(razonSocial);

        StringBuilder sigla = new StringBuilder();
        for (String palabra : palabras) {
            if (sigla.length() == 6) {
                break;
            }
            sigla.append(palabra.charAt(0));
        }

        if (sigla.length() < 3 && !palabras.isEmpty()) {
            String ultima = palabras.getLast();
            for (int i = 1; i < ultima.length() && sigla.length() < 3; i++) {
                sigla.append(ultima.charAt(i));
            }
        }

        if (sigla.isEmpty() || !Character.isLetter(sigla.charAt(0))) {
            sigla.insert(0, 'C');
        }
        while (sigla.length() < 3) {
            sigla.append('C');
        }

        return sigla.length() > 6 ? sigla.substring(0, 6) : sigla.toString();
    }

    /**
     * La sigla con un desempate: «CDN», «CDN2», «CDN3». Si con el número no cabe en seis, se recorta la
     * base y no el número, porque el número es lo que la distingue.
     */
    public static String conDesempate(String base, int numero) {
        if (numero <= 1) {
            return base;
        }

        String sufijo = String.valueOf(numero);
        int espacio = 6 - sufijo.length();

        return (base.length() > espacio ? base.substring(0, espacio) : base) + sufijo;
    }

    /** Valida y normaliza una sigla escrita a mano. */
    public static String validar(String sigla) {
        String normalizada = sigla == null ? "" : sigla.trim().toUpperCase(Locale.ROOT);

        if (!FORMATO.matcher(normalizada).matches()) {
            throw new IllegalArgumentException(
                    "La sigla de un cliente son de tres a seis letras o digitos, empezando por letra,"
                            + " y se recibio «" + sigla + "»");
        }

        return normalizada;
    }

    private static List<String> palabrasSignificativas(String razonSocial) {
        String texto = Normalizer.normalize(razonSocial == null ? "" : razonSocial, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replace(".", "")
                .replaceAll("[^A-Z0-9]+", " ")
                .trim();

        List<String> palabras = new ArrayList<>();
        for (String palabra : texto.isEmpty() ? new String[0] : texto.split(" ")) {
            if (!FORMAS_JURIDICAS.contains(palabra) && !PALABRAS_VACIAS.contains(palabra)) {
                palabras.add(palabra);
            }
        }

        // Una razón social que solo tuviera palabras vacías o formas jurídicas se queda sin nada: se
        // usan entonces sus palabras tal cual, para no inventar una sigla de la nada.
        return palabras.isEmpty() && !texto.isEmpty() ? Arrays.asList(texto.split(" ")) : palabras;
    }
}
