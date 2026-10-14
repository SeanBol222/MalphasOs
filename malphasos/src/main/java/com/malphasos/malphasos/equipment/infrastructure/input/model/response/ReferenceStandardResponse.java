package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(name = "ReferenceStandardResponse")
public record ReferenceStandardResponse(
        UUID id,
        String nombre,
        String marca,
        String serie,
        String certificado,
        LocalDate vencimientoCertificado,
        boolean estadoActivo) {

    public static ReferenceStandardResponse from(ReferenceStandard patron) {
        return new ReferenceStandardResponse(patron.getId(), patron.getNombre(), patron.getMarca(),
                patron.getSerie(), patron.getCertificado(), patron.getVencimientoCertificado(), patron.isEstadoActivo());
    }

    public static List<ReferenceStandardResponse> fromList(List<ReferenceStandard> patrones) {
        return patrones.stream().map(ReferenceStandardResponse::from).toList();
    }
}
