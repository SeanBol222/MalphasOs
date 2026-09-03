package com.malphasos.malphasos.client.infrastructure.input.model.response;

import com.malphasos.malphasos.client.domain.manager.ManagerType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;

/**
 * Un encargado.
 *
 * <p>{@code idPersona} es también su identificador: un encargado es una persona con ese rol.
 */
@Builder
@Schema(name = "ManagerResponse")
public record ManagerResponse(
        UUID idPersona,
        ManagerType tipo,
        @Schema(description = "Sede de la que se encarga, o null si se encarga de un area")
        UUID idSede,
        @Schema(description = "Area de la que se encarga, o null si se encarga de una sede")
        UUID idAreaServicio,
        boolean estadoActivo) {
}
