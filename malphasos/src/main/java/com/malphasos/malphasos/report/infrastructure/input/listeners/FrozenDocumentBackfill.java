package com.malphasos.malphasos.report.infrastructure.input.listeners;

import com.malphasos.malphasos.report.application.ports.input.ServiceReportServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Congela al arrancar los informes de los reportes que se cerraron antes de V37 (2026-10-17).
 *
 * <p>Decidido por el usuario: con los datos del día en que se congelan, y marcados como tardíos para que
 * se sepa que su copia no es la del día del servicio. No se podía hacer en la migración: componer el
 * documento exige caminar la orden, la hoja de vida, el tipo y la empresa, y eso es Java, no SQL. Con
 * todos congelados no hace nada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
// Después de sembrar la empresa: el documento la copia.
@Order(Ordered.LOWEST_PRECEDENCE)
public class FrozenDocumentBackfill implements ApplicationRunner {

    private final ServiceReportServicePort serviceReportServicePort;

    @Override
    public void run(ApplicationArguments args) {
        int congelados = serviceReportServicePort.freezeClosedWithoutDocument();
        if (congelados > 0) {
            log.info("Congelados tarde {} informes de reportes cerrados antes de V37", congelados);
        }
    }
}
