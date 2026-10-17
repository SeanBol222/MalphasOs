package com.malphasos.malphasos.company.infrastructure.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * La empresa del archivo de arranque, leída del prefijo {@code app.empresa}.
 *
 * <p><b>Desde el 2026-10-17 solo siembra el primer arranque</b>: la empresa vive en la base y la edita un
 * administrador. Si la base no tiene ninguna y este archivo trae nombre, {@link ServiceCompanySeeder} la
 * crea con él; a partir de ahí el archivo no pisa nada. Sin archivo la aplicación arranca igual, y los
 * documentos salen sin membrete hasta que alguien la configure. (Hasta ese día era la única fuente, y sin
 * nombre la aplicación no arrancaba.)
 *
 * <p>Ninguno de sus datos vive en el código, decidido por el usuario el 2026-10-16: MalphasOS es un
 * producto aparte de la empresa que lo usa. El repositorio solo versiona
 * {@code config/empresa.example.yaml}, con una empresa inventada.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.empresa")
public class ServiceCompanyProperties {

    /** El directorio de la configuración de la empresa, contra el que se resuelve el logo. */
    private String directorio;

    /** Sin él no hay nada que sembrar. */
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
