package com.malphasos.malphasos.company.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Los datos de la empresa: van todos, porque así se corrigen. Lo que no venga queda vacío. */
@Schema(name = "ServiceCompanyRequest")
public record ServiceCompanyRequest(
        @Schema(example = "Servicios Biomédicos de Ejemplo S.A.S.")
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 160, message = "El nombre no puede pasar de 160 caracteres")
        String nombre,
        @Schema(description = "Opcional", example = "900.000.000-0")
        @Size(max = 30, message = "El NIT no puede pasar de 30 caracteres")
        String nit,
        @Schema(description = "Opcional")
        @Size(max = 200, message = "La direccion no puede pasar de 200 caracteres")
        String direccion,
        @Schema(description = "Opcional: la ciudad, del catalogo de ubicaciones")
        UUID idCiudad,
        @Schema(description = "Telefonos fijos, como se imprimen; hasta cinco")
        @Size(max = 5, message = "La empresa tiene como mucho cinco telefonos fijos")
        List<@Size(max = 30, message = "Un telefono no puede pasar de 30 caracteres") String> telefonos,
        @Schema(description = "Opcional")
        @Size(max = 30, message = "El movil no puede pasar de 30 caracteres")
        String movil,
        @Schema(description = "Opcional", example = "contacto@empresa.example")
        @Size(max = 120, message = "El correo no puede pasar de 120 caracteres")
        String correo,
        @Schema(description = "Opcional: el texto de garantia que cierra el informe")
        @Size(max = 2000, message = "La garantia no puede pasar de 2000 caracteres")
        String garantia,
        @Schema(description = "Opcional: la aclaracion del proceso del informe")
        @Size(max = 2000, message = "La aclaracion no puede pasar de 2000 caracteres")
        String aclaracion,
        @Schema(description = "Opcional: el codigo del formato de la hoja de vida", example = "FT-001")
        @Size(max = 40, message = "El codigo de la hoja de vida no puede pasar de 40 caracteres")
        String codigoHojaDeVida,
        @Schema(description = "Opcional: el codigo del formato del informe", example = "FT-002")
        @Size(max = 40, message = "El codigo del informe no puede pasar de 40 caracteres")
        String codigoInforme) {
}
