package com.malphasos.malphasos.shared.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * El logo de la empresa, leído una vez al arrancar y entregado como {@code data:} URI.
 *
 * <p>Viaja dentro del membrete y no por una ruta propia: los dos documentos que lo imprimen ya piden el
 * membrete, y una imagen aparte necesitaría su propia autoridad para algo que no es un recurso del
 * dominio. Por eso se limita el tamaño: un logo de medio megabyte en cada hoja de vida ya es mucho.
 *
 * <p>Un logo configurado que no se puede leer tumba el arranque, igual que el nombre que falta: es
 * preferible a imprimir documentos sin él sin que nadie se entere.
 */
@Component
public class ServiceCompanyLogo {

    static final long TAMANO_MAXIMO = 512 * 1024;

    private static final Map<String, String> TIPOS = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "svg", "image/svg+xml");

    private final String dataUri;

    public ServiceCompanyLogo(ServiceCompanyProperties empresa) {
        this.dataUri = leer(empresa.getDirectorio(), empresa.getLogo());
    }

    /** El logo como {@code data:} URI, o vacío si la empresa no configuró ninguno. */
    public Optional<String> dataUri() {
        return Optional.ofNullable(dataUri);
    }

    static String leer(String directorio, String logo) {
        if (logo == null || logo.isBlank()) {
            return null;
        }

        String extension = logo.substring(logo.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String tipo = TIPOS.get(extension);
        if (tipo == null) {
            throw new IllegalStateException("El logo de la empresa tiene que ser PNG, JPG o SVG, y es " + logo);
        }

        Path archivo = Path.of(directorio == null ? "." : directorio).resolve(logo);
        if (!Files.isRegularFile(archivo)) {
            throw new IllegalStateException("No se encuentra el logo de la empresa en " + archivo.toAbsolutePath());
        }

        try {
            long tamano = Files.size(archivo);
            if (tamano > TAMANO_MAXIMO) {
                throw new IllegalStateException("El logo de la empresa pesa " + tamano
                        + " bytes y el máximo es " + TAMANO_MAXIMO);
            }
            return "data:" + tipo + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(archivo));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el logo de la empresa en " + archivo, e);
        }
    }
}
