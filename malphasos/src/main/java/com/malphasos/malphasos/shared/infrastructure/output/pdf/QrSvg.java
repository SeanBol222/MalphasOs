package com.malphasos.malphasos.shared.infrastructure.output.pdf;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;

/**
 * El QR de un documento en PDF, como un trazo SVG desde la matriz de módulos: un cuadrado de 1 × 1 por
 * módulo negro. Es lo que dibujaba el navegador con {@code qrcode} hasta el 2026-10-17, con la misma
 * corrección de errores, M. Se llamaba {@code ReportQr} y vivía con el informe hasta el 2026-10-18, cuando
 * la hoja de vida pasó también al servidor.
 *
 * @param lado cuántos módulos tiene por lado, sin la zona en blanco: la pone la plantilla
 */
public record QrSvg(String direccion, int lado, String trazo) {

    public static QrSvg of(String direccion) {
        ByteMatrix modulos;
        try {
            modulos = Encoder.encode(direccion, ErrorCorrectionLevel.M).getMatrix();
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo codificar el QR de " + direccion, e);
        }

        StringBuilder trazo = new StringBuilder();
        for (int fila = 0; fila < modulos.getHeight(); fila++) {
            for (int columna = 0; columna < modulos.getWidth(); columna++) {
                if (modulos.get(columna, fila) == 1) {
                    trazo.append('M').append(columna).append(' ').append(fila).append("h1v1h-1z");
                }
            }
        }

        return new QrSvg(direccion, modulos.getWidth(), trazo.toString());
    }
}
