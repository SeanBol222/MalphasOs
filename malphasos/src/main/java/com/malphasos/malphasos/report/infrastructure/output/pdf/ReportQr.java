package com.malphasos.malphasos.report.infrastructure.output.pdf;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;

/**
 * El QR del informe, como un trazo SVG desde la matriz de módulos: un cuadrado de 1 × 1 por módulo negro.
 * Es lo que dibujaba el navegador con {@code qrcode} hasta el 2026-10-17, con la misma corrección de
 * errores, M.
 *
 * @param lado cuántos módulos tiene por lado, sin la zona en blanco: la pone la plantilla
 */
public record ReportQr(String direccion, int lado, String trazo) {

    public static ReportQr of(String direccion) {
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

        return new ReportQr(direccion, modulos.getWidth(), trazo.toString());
    }
}
