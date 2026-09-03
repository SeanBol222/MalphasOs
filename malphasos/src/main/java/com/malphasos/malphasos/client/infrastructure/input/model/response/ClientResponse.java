package com.malphasos.malphasos.client.infrastructure.input.model.response;

import com.malphasos.malphasos.client.domain.client.IdentificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;

@Builder
@Schema(name = "ClientResponse")
public record ClientResponse(
        UUID id,
        String documento,
        IdentificationType tipoIdentificacion,
        String razonSocial,
        UUID idPais,
        boolean estadoActivo,
        List<ContactResponse> correos,
        List<ContactResponse> telefonos,
        @Schema(description = "Identificadores de las personas que lo representan legalmente")
        Set<UUID> representantes) {
}
