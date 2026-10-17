package com.malphasos.malphasos.report.application.ports.output;

import com.malphasos.malphasos.company.domain.serviceCompany.CompanyLogo;
import java.util.Optional;

/**
 * Las versiones del logo de la empresa que congelaron los informes (V37, 2026-10-17). Cada versión se
 * guarda una vez, por su huella: mil informes con el mismo logo lo guardan una sola vez, y cambiar el
 * logo de la empresa no altera los que ya se cerraron.
 */
public interface DocumentLogoStorePort {

    /** Guarda esa versión si no estaba. */
    void store(CompanyLogo logo);

    Optional<CompanyLogo> find(String huella);
}
