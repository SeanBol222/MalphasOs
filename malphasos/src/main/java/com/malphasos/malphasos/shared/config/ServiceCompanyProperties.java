package com.malphasos.malphasos.shared.config;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Los datos de la empresa que presta el servicio técnico, leídos del prefijo {@code app.empresa}.
 *
 * <p>Son el membrete de los documentos que se entregan al cliente —la hoja de vida y el informe de
 * mantenimiento— y no un dato de ningún cliente, de modo que no van en la base.
 *
 * <p><b>Ninguno vive en el código</b>, decidido por el usuario el 2026-10-16: MalphasOS es un producto
 * aparte de la empresa que lo usa y va a ser de código abierto. Se leen de {@code config/empresa.yaml},
 * fuera de git, que {@code application.yaml} importa; el repositorio solo versiona
 * {@code config/empresa.example.yaml}, con una empresa inventada. Sin nombre la aplicación no arranca:
 * un documento sin membrete no se puede entregar, y fallar al arrancar lo dice antes que el primer
 * documento en blanco.
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.empresa")
public class ServiceCompanyProperties {

    /** El directorio de la configuración de la empresa, contra el que se resuelve el logo. */
    private String directorio;

    @NotBlank(message = "falta la empresa que presta el servicio: copia config/empresa.example.yaml en"
            + " config/empresa.yaml y llénalo")
    private String nombre;

    private String nit;

    private String direccion;

    private String ciudad;

    /** Teléfonos fijos; vacía si no tiene. */
    private List<String> telefonos = new ArrayList<>();

    private String movil;

    private String correo;

    /** El archivo del logo, relativo a {@link #directorio}: PNG, JPG o SVG. Sin él, el membrete es el nombre. */
    private String logo;

    /** El texto de garantía que cierra el informe de mantenimiento. Sin él, el informe no lo lleva. */
    private String garantia;

    /** La aclaración del proceso del informe. Sin ella, el informe no la lleva. */
    private String aclaracion;

    /** El código con que la empresa controla el formato de la hoja de vida en su sistema de calidad. */
    private String codigoHojaDeVida;

    /** Lo mismo para el informe de mantenimiento. */
    private String codigoInforme;
}
