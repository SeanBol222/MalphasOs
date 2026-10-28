package com.malphasos.malphasos.equipment.application.services.lifeSheet;

import com.malphasos.malphasos.company.application.ports.input.ServiceCompanyServicePort;
import com.malphasos.malphasos.company.domain.serviceCompany.ServiceCompany;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheet;
import com.malphasos.malphasos.equipment.application.model.lifeSheet.LifeSheetPdf;
import com.malphasos.malphasos.equipment.application.ports.input.LifeSheetPdfServicePort;
import com.malphasos.malphasos.equipment.application.ports.input.LifeSheetServicePort;
import com.malphasos.malphasos.equipment.application.ports.output.LifeSheetPdfPort;
import com.malphasos.malphasos.shared.application.model.ReadScope;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * La hoja de vida en PDF (2026-10-18). Hasta ese día se imprimía desde el navegador, y lo que salía
 * dependía del navegador y de su diálogo de impresión: el usuario lo vio distinto de la pantalla. Ahora la
 * compone y la convierte el servidor, como el informe de un reporte, y la pantalla muestra el PDF.
 *
 * <p>No es un documento congelado, al contrario que el informe: la hoja de vida es la de hoy, y su fecha
 * de actualización es la de impresión.
 */
@Service
public class LifeSheetPdfService implements LifeSheetPdfServicePort {

    private final LifeSheetServicePort lifeSheetServicePort;
    private final ServiceCompanyServicePort serviceCompanyServicePort;
    private final LifeSheetPdfPort lifeSheetPdfPort;
    private final Clock reloj;

    /**
     * La fecha de impresión es la de la zona de la empresa, no la del servidor: un contenedor corre en
     * UTC, y en Bogotá desde las siete de la noche eso ya es mañana —la misma trampa que la pantalla
     * evitaba con la fecha local del navegador—.
     */
    @Autowired
    public LifeSheetPdfService(
            LifeSheetServicePort lifeSheetServicePort,
            ServiceCompanyServicePort serviceCompanyServicePort,
            LifeSheetPdfPort lifeSheetPdfPort,
            @Value("${app.time-zone:America/Bogota}") String zona) {
        this(lifeSheetServicePort, serviceCompanyServicePort, lifeSheetPdfPort, Clock.system(ZoneId.of(zona)));
    }

    LifeSheetPdfService(
            LifeSheetServicePort lifeSheetServicePort,
            ServiceCompanyServicePort serviceCompanyServicePort,
            LifeSheetPdfPort lifeSheetPdfPort,
            Clock reloj) {
        this.lifeSheetServicePort = lifeSheetServicePort;
        this.serviceCompanyServicePort = serviceCompanyServicePort;
        this.lifeSheetPdfPort = lifeSheetPdfPort;
        this.reloj = reloj;
    }

    /**
     * Sin transacción propia, a propósito: la conversión puede tardar segundos y tendría abierta una
     * conexión de la base todo ese rato. Las dos lecturas abren la suya.
     */
    @Override
    public LifeSheetPdf pdf(UUID idEquipoCliente, ReadScope alcance) {
        LifeSheet hoja = lifeSheetServicePort.findByEquipment(idEquipoCliente, alcance);

        Optional<ServiceCompany> empresa = serviceCompanyServicePort.find();
        byte[] contenido = lifeSheetPdfPort.render(LifeSheetPrintComposer.compose(
                hoja, empresa, empresa.map(serviceCompanyServicePort::cityName).orElse(null),
                LocalDate.now(reloj)));

        String numero = hoja.identificacion().numeroHojaVida();
        return new LifeSheetPdf(
                "hoja-de-vida-" + (numero == null || numero.isBlank() ? idEquipoCliente : numero) + ".pdf",
                contenido);
    }
}
