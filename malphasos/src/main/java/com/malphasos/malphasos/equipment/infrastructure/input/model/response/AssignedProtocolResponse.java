package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import com.malphasos.malphasos.equipment.application.model.AssignedProtocolDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** Un protocolo de un tipo de equipo, con el nombre y la descripción para no obligar a cruzar listas. */
@Schema(name = "AssignedProtocolResponse")
public record AssignedProtocolResponse(
        UUID idProtocolo,
        String nombre,
        String descripcion,
        @Schema(description = "1 es el primero. Van seguidas, sin huecos") int prioridad,
        @Schema(description = "Nula si el tipo no añade nada al texto general") String indicacion) {

    public static List<AssignedProtocolResponse> fromList(List<AssignedProtocolDetail> detalles) {
        return detalles.stream()
                .map(detalle -> new AssignedProtocolResponse(
                        detalle.protocolo().getId(),
                        detalle.protocolo().getNombre(),
                        detalle.protocolo().getDescripcion(),
                        detalle.prioridad(),
                        detalle.indicacion()))
                .toList();
    }
}
