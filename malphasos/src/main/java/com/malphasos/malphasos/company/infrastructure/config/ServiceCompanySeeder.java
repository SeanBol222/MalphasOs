package com.malphasos.malphasos.company.infrastructure.config;

import com.malphasos.malphasos.company.application.ports.input.ServiceCompanyServicePort;
import com.malphasos.malphasos.company.application.services.serviceCompany.commands.SaveServiceCompanyCommand;
import com.malphasos.malphasos.company.application.services.serviceCompany.commands.SeedServiceCompanyCommand;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Siembra la empresa del archivo de arranque en una base que todavía no tiene ninguna (2026-10-17).
 *
 * <p>Una instalación nueva arranca así con su membrete sin un paso a mano, y una que ya la tiene no se
 * entera: lo que un administrador editó no lo pisa un archivo que se quedó viejo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceCompanySeeder implements ApplicationRunner {

    private final ServiceCompanyProperties archivo;
    private final ServiceCompanyServicePort serviceCompanyServicePort;

    @Override
    public void run(ApplicationArguments args) {
        // Con la empresa ya en la base el archivo no se mira: ni siquiera su logo, que podría no estar.
        if (serviceCompanyServicePort.find().isPresent()) {
            return;
        }
        if (archivo.getNombre() == null || archivo.getNombre().isBlank()) {
            log.warn("La empresa que presta el servicio no esta configurada: los documentos saldran sin"
                    + " membrete hasta que un administrador la llene, o hasta arrancar con"
                    + " config/empresa.yaml");
            return;
        }

        boolean sembrada = serviceCompanyServicePort.seed(new SeedServiceCompanyCommand(
                new SaveServiceCompanyCommand(archivo.getNombre(), archivo.getNit(), archivo.getDireccion(),
                        archivo.getCiudad(), List.copyOf(archivo.getTelefonos()), archivo.getMovil(),
                        archivo.getCorreo(), archivo.getGarantia(), archivo.getAclaracion(),
                        archivo.getCodigoHojaDeVida(), archivo.getCodigoInforme()),
                ServiceCompanyLogo.leer(archivo.getDirectorio(), archivo.getLogo())));

        if (sembrada) {
            log.info("Empresa sembrada desde el archivo de arranque: {}", archivo.getNombre());
        }
    }
}
