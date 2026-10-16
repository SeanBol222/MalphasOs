package com.malphasos.malphasos.equipment.infrastructure.input.model.response;

import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandard;
import com.malphasos.malphasos.equipment.domain.referenceStandard.ReferenceStandardSheet;
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
        String modelo,
        String codigoInterno,
        String rango,
        String resolucion,
        LocalDate fechaCalibracion,
        String empresaCalibradora,
        boolean estadoActivo) {

    public static ReferenceStandardResponse from(ReferenceStandard patron) {
        ReferenceStandardSheet ficha = patron.getFicha();
        return new ReferenceStandardResponse(patron.getId(), patron.getNombre(), patron.getMarca(),
                patron.getSerie(), patron.getCertificado(), patron.getVencimientoCertificado(), ficha.modelo(),
                ficha.codigoInterno(), ficha.rango(), ficha.resolucion(), ficha.fechaCalibracion(),
                ficha.empresaCalibradora(), patron.isEstadoActivo());
    }

    public static List<ReferenceStandardResponse> fromList(List<ReferenceStandard> patrones) {
        return patrones.stream().map(ReferenceStandardResponse::from).toList();
    }
}
