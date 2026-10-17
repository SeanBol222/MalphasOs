package com.malphasos.malphasos.report.application.ports.output;

import com.malphasos.malphasos.report.domain.serviceReport.DocumentImage;
import java.util.Optional;

/**
 * Las imágenes que congelaron los informes —el logo de la empresa desde V37, la firma del ingeniero desde
 * V38—. Cada versión se guarda una vez, por su huella: mil informes con la misma firma la guardan una
 * sola vez, y cambiarla después no altera los que ya se cerraron.
 */
public interface DocumentImageStorePort {

    /** Guarda esa versión si no estaba. */
    void store(DocumentImage imagen);

    Optional<DocumentImage> find(String huella);
}
