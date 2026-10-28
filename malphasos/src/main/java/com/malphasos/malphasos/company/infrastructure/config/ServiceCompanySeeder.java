package com.malphasos.malphasos.company.infrastructure.config;

import com.malphasos.malphasos.company.application.ports.input.ServiceCompanyServicePort;
import com.malphasos.malphasos.company.application.services.serviceCompany.commands.SaveServiceCompanyCommand;
import com.malphasos.malphasos.company.application.services.serviceCompany.commands.SeedServiceCompanyCommand;
import com.malphasos.malphasos.location.application.ports.input.CityServicePort;
import com.malphasos.malphasos.location.domain.city.City;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
// Antes que cualquier otro arranque: congelar los informes viejos copia la empresa, y tiene que estar.
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ServiceCompanySeeder implements ApplicationRunner {

    private final ServiceCompanyProperties archivo;
    private final ServiceCompanyServicePort serviceCompanyServicePort;
    private final CityServicePort cityServicePort;

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
                        ciudadDelArchivo(), List.copyOf(archivo.getTelefonos()), archivo.getMovil(),
                        archivo.getCorreo(), archivo.getGarantia(), archivo.getAclaracion(),
                        archivo.getCodigoHojaDeVida(), archivo.getCodigoInforme()),
                ServiceCompanyLogo.leer(archivo.getDirectorio(), archivo.getLogo())));

        if (sembrada) {
            log.info("Empresa sembrada desde el archivo de arranque: {}", archivo.getNombre());
        }
    }

    /**
     * El archivo dice la ciudad por su nombre, y la empresa la guarda del catálogo (V44). Se busca como
     * la pantalla y como la migración: sin mayúsculas, sin tildes y sin espacios alrededor. Si no casa con
     * una sola, la empresa se siembra sin ciudad y lo dice: un membrete sin ciudad se corrige en la
     * pantalla, y un arranque que no levanta por eso no.
     */
    private UUID ciudadDelArchivo() {
        String nombre = archivo.getCiudad();
        if (nombre == null || nombre.isBlank()) {
            return null;
        }

        List<City> casan = cityServicePort.findAll().stream()
                .filter(ciudad -> normal(ciudad.getNombre()).equals(normal(nombre)))
                .toList();
        if (casan.size() == 1) {
            return casan.getFirst().getId();
        }
        log.warn("La ciudad del archivo de arranque, «{}», casa con {} ciudades del catalogo y tiene que"
                + " casar con una: la empresa se siembra sin ciudad", nombre, casan.size());

        return null;
    }

    private static String normal(String texto) {
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
