package com.malphasos.malphasos.report.domain.serviceReport;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * La firma de quien recibió el servicio (V39, 2026-10-17): la huella de su imagen, guardada aparte una vez
 * por versión, y cuándo firmó. Quién firmó lo dice {@link Handover}, que una firma exige.
 */
public record ReceiptSignature(String huella, LocalDateTime firmada) {

    public ReceiptSignature {
        Objects.requireNonNull(huella, "Una firma lleva la huella de su imagen");
        Objects.requireNonNull(firmada, "Una firma lleva cuándo se firmó");
    }
}
