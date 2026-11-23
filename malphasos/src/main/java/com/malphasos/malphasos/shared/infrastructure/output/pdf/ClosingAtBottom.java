package com.malphasos.malphasos.shared.infrastructure.output.pdf;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.DoubleFunction;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Lleva el cierre de un documento —las firmas y lo que va con ellas— al pie de la última página en que
 * cae, aunque sea lo único de esa página (2026-11-21, pedido por el usuario). Sin esto, cuando lo de
 * arriba no cabía en una hoja, las firmas subían a lo alto de la siguiente y quedaban a media página.
 *
 * <p><b>Por qué se mide el PDF y no se resuelve con CSS.</b> Con una sola página basta un
 * {@code margin-top: auto} dentro de una hoja de alto mínimo, y las plantillas lo siguen usando. Con
 * varias no: el relleno que se repite en cada trozo, la cabecera de tabla que se repite y las filas que
 * no se parten consumen alto que el cálculo de la hoja no ve, de modo que una hoja de cuatro páginas
 * empujaba el cierre a una quinta, solo y arriba. Probado contra Gotenberg el mismo día. Lo único que sabe
 * dónde cayó cada cosa es Chromium al paginar, y eso solo se lee en el PDF.
 *
 * <p>Así que se convierte una vez, se busca la última página con la {@link Spec#marca marca} del cierre y
 * la línea más baja que hay en ella por encima del {@link Spec#limite límite} —el pie de Gotenberg va
 * por debajo—, y se mide cuánto le falta para quedar donde quedaría si el documento cupiera en una
 * página: {@link Spec#baseObjetivo}. La plantilla mete ese hueco antes del cierre y se convierte otra
 * vez. Lo de antes del cierre no se mueve, así que solo cambia de sitio el cierre.
 */
public final class ClosingAtBottom {

    /** Por debajo de esto ya está al pie: no vale una segunda conversión. En puntos. */
    static final double YA_ESTA = 2;

    private ClosingAtBottom() {}

    /**
     * Qué es el cierre de un documento y dónde tiene que quedar, en puntos desde el borde de arriba.
     *
     * @param marca un texto que solo está en el cierre, o en él y en páginas anteriores
     * @param baseObjetivo la línea de base de la última línea del cierre cuando está al pie
     * @param limite lo que está más abajo no es del documento, sino del pie de cada página
     */
    public record Spec(String marca, double baseObjetivo, double limite) {}

    /** Dónde cayó el cierre: en qué página, contada desde 1, y la línea de base de su última línea. */
    public record Posicion(int pagina, double base) {}

    /** Cuántas conversiones más, como mucho, para llegar al pie. Casi siempre bastan una o dos. */
    static final int VUELTAS = 5;

    /**
     * Convierte con el cierre al pie de la última página en que cae.
     *
     * <p>La primera conversión lo deja donde diga el CSS; si le sobra sitio debajo, se baja eso y se mide
     * otra vez, porque Chromium no lo baja siempre lo calculado —al empezar una página descuenta parte del
     * margen, y una tabla que se alarga repite su cabecera—. Si se pasó del pie, sube lo que sobra, pero
     * nunca por encima de donde lo dejó el CSS. Si una vuelta lo pasa a otra página, se descarta y se
     * prueba con la mitad: más vale un poco por encima del pie que solo en una hoja nueva. No encontrarlo
     * no es un error: el PDF es válido, solo que sin acomodar.
     *
     * @param convertir el PDF, dados los píxeles CSS que hay que bajar el cierre
     */
    public static byte[] acomodar(DoubleFunction<byte[]> convertir, Spec cierre) {
        byte[] pdf = convertir.apply(0);
        Optional<Posicion> donde = posicion(pdf, cierre);
        double bajada = 0;
        double paso = 0;

        for (int vuelta = 0; vuelta < VUELTAS && donde.isPresent(); vuelta++) {
            if (paso == 0) {
                double falta = cierre.baseObjetivo() - donde.get().base();
                // Hacia arriba solo deshace lo que se bajó: lo que dejó el CSS no se sube.
                paso = Math.max(falta * 4 / 3, -bajada);

                if (Math.abs(paso) * 3 / 4 < YA_ESTA) {
                    break;
                }
            }

            byte[] candidato = convertir.apply(bajada + paso);
            Optional<Posicion> ahora = posicion(candidato, cierre);

            if (ahora.isEmpty()) {
                break;
            }

            if (ahora.get().pagina() != donde.get().pagina()) {
                paso /= 2;
                continue;
            }

            pdf = candidato;
            donde = ahora;
            bajada += paso;
            paso = 0;
        }

        return pdf;
    }

    /**
     * La última página con la marca del cierre y la línea más baja que hay en ella por encima del límite,
     * o nada si no está o el PDF no se puede leer.
     */
    public static Optional<Posicion> posicion(byte[] pdf, Spec cierre) {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            for (int pagina = documento.getNumberOfPages(); pagina >= 1; pagina--) {
                Lineas lineas = new Lineas(cierre.limite());
                lineas.setStartPage(pagina);
                lineas.setEndPage(pagina);
                String texto = lineas.getText(documento);

                if (normalizar(texto).contains(normalizar(cierre.marca()))) {
                    return Optional.of(new Posicion(pagina, lineas.masBaja));
                }
            }
        } catch (IOException ilegible) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    /** El estilo que baja el cierre en la plantilla, o nada. Con punto decimal, sea cual sea el idioma. */
    public static String estilo(double pixeles) {
        return pixeles > 0 ? String.format(Locale.ROOT, "margin-top: %.2fpx", pixeles) : null;
    }

    /** El PDF parte los espacios como quiere: la marca se compara sin ellos. */
    private static String normalizar(String texto) {
        return texto.replaceAll("\\s+", "");
    }

    /** La línea de base más baja de la página por encima del límite. */
    private static final class Lineas extends PDFTextStripper {

        private final double limite;
        private double masBaja;

        Lineas(double limite) throws IOException {
            this.limite = limite;
        }

        @Override
        protected void writeString(String texto, List<TextPosition> letras) throws IOException {
            for (TextPosition letra : letras) {
                if (letra.getYDirAdj() < limite && !letra.getUnicode().isBlank()) {
                    masBaja = Math.max(masBaja, letra.getYDirAdj());
                }
            }

            super.writeString(texto, letras);
        }
    }
}
