package com.malphasos.malphasos.shared.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Los datos de la empresa que presta el servicio técnico, leídos del prefijo {@code app.empresa}.
 *
 * <p>Son el membrete de los documentos que se entregan al cliente —la hoja de vida hoy, el reporte
 * de servicio cuando tenga PDF— y no un dato de ningún cliente, de modo que no van en la base. Van en
 * configuración y no en el {@code .env}: no son secretos ni cambian de una máquina a otra, y el
 * {@code .env} está fuera de git. Cada valor se puede sobrescribir con una variable de entorno, como
 * el origen de CORS.
 *
 * <p>Los valores versionados son los vigentes el 2026-10-05, confirmados por el usuario.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.empresa")
public class ServiceCompanyProperties {

    private String nombre;

    private String direccion;

    private String ciudad;

    /** Teléfonos fijos. Vacía hoy, porque la empresa ya no tiene; si vuelve a tener, es configuración. */
    private List<String> telefonos = new ArrayList<>();

    private String movil;

    private String correo;
}
