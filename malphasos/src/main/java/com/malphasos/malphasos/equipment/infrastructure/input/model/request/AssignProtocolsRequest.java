package com.malphasos.malphasos.equipment.infrastructure.input.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Los protocolos de un tipo de equipo, en el orden de su prioridad: el primero de la lista es la
 * prioridad 1. No se manda ninguna prioridad: la deriva el servidor de la posición, para que no pueda
 * llegar un hueco ni una repetida. Una lista vacía los quita todos.
 */
@Schema(name = "AssignProtocolsRequest")
public record AssignProtocolsRequest(
        @NotNull(message = "La lista de protocolos es obligatoria, aunque esté vacía")
        @Valid
        List<Item> protocolos) {

    @Schema(name = "AssignProtocolsItem")
    public record Item(
            @NotNull(message = "Cada protocolo necesita su identificador")
            UUID idProtocolo,
            @Schema(description = "Lo que este tipo añade al texto general del protocolo", example = "Usar alcohol isopropílico, no al 70 %")
            @Size(max = 300, message = "La indicación no puede pasar de 300 caracteres")
            String indicacion) {
    }
}
