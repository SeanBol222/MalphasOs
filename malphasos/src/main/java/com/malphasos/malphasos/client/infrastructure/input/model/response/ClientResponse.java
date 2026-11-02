package com.malphasos.malphasos.client.infrastructure.input.model.response;

import com.malphasos.malphasos.client.domain.client.IdentificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
@Schema(name = "ClientResponse")
public record ClientResponse(
        UUID id,
        String documento,
        IdentificationType tipoIdentificacion,
        String razonSocial,
        @Schema(description = "Encabeza el numero de sus hojas de vida", example = "CDN") String sigla,
        UUID idPais,
        boolean estadoActivo,
        List<ContactResponse> correos,
        List<ContactResponse> telefonos,
        @Schema(description = "Quienes lo supervisan: tienen cuenta, ven lo de este cliente y aprueban sus ordenes")
        List<SupervisorResponse> supervisores) {

    @Schema(name = "ClientSupervisorResponse")
    public record SupervisorResponse(
            UUID idPersona,
            @Schema(description = "Su nombre completo, para que quien lee el cliente lo nombre sin leer "
                    + "personas (2026-11-02); nulo si la ficha no existe", example = "Luis Pena")
            String nombre,
            @Schema(description = "Como se le nombra en los documentos; nulo, sin cargo",
                    example = "Ingeniero biomedico")
            String cargo) {
    }
}
