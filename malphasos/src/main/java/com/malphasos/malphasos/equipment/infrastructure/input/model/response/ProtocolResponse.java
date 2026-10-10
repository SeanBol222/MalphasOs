package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import com.malphasos.malphasos.equipment.domain.protocol.Protocol;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "ProtocolResponse")
public record ProtocolResponse(UUID id, String nombre, String descripcion, boolean estadoActivo) {

    public static ProtocolResponse from(Protocol protocolo) {
        return new ProtocolResponse(
                protocolo.getId(), protocolo.getNombre(), protocolo.getDescripcion(), protocolo.isEstadoActivo());
    }

    public static List<ProtocolResponse> fromList(List<Protocol> protocolos) {
        return protocolos.stream().map(ProtocolResponse::from).toList();
    }
}
